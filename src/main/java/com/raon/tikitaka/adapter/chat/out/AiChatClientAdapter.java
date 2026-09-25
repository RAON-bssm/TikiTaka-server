package com.raon.tikitaka.adapter.chat.out;

import com.raon.tikitaka.application.chat.ChatAnswer;
import com.raon.tikitaka.application.chat.ChatHealth;
import com.raon.tikitaka.application.chat.ChatSource;
import com.raon.tikitaka.application.chat.ChatTurn;
import com.raon.tikitaka.application.chat.ChatUserContext;
import com.raon.tikitaka.application.chat.out.ChatClientPort;
import com.raon.tikitaka.domain.chat.Chatbot;
import com.raon.tikitaka.global.config.ChatbotProperties;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.netty.http.client.HttpClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * FastAPI AI 서버 호출 어댑터.
 *
 * 유저 정보는 헤더로, 페르소나·이력·질문은 body로 보낸다. 헤더 값에 한글이 들어갈 수 있어
 * 전부 URL 인코딩한다 - HTTP 헤더 기본 인코딩이 ISO-8859-1이라 그냥 실으면 깨진다.
 */
@Slf4j
@Component
public class AiChatClientAdapter implements ChatClientPort {

    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final Duration readTimeout;
    private final Duration healthTimeout;

    public AiChatClientAdapter(ChatbotProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) properties.connectTimeout().toMillis());
        this.webClient = WebClient.builder()
                .baseUrl(properties.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        this.readTimeout = properties.readTimeout();
        // 헬스체크까지 120초를 기다리면 "꺼졌다"를 알려주는 의미가 없다
        this.healthTimeout = properties.connectTimeout().plusSeconds(2);
    }

    @Override
    public ChatAnswer ask(Chatbot chatbot, ChatUserContext user, List<ChatTurn> history, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("persona_prompt", chatbot.getPersonaPrompt());
        body.put("model", chatbot.getModel());
        body.put("history", toHistoryPayload(history));
        body.put("message", message);

        long startedAt = System.currentTimeMillis();
        String raw;
        try {
            raw = webClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/chat")
                            .queryParam("chatbot_id", chatbot.getChatbotId())
                            .build())
                    .headers(headers -> {
                        headers.set("X-User-Id", user.userId().toString());
                        setEncoded(headers::set, "X-User-Name", user.userName());
                        setEncoded(headers::set, "X-Main-Location", user.mainLocation());
                        setEncoded(headers::set, "X-Current-Location", user.currentLocation());
                        if (user.mainLocationId() != null) {
                            headers.set("X-Main-Location-Id", String.valueOf(user.mainLocationId()));
                        }
                        if (user.currentLocationId() != null) {
                            headers.set("X-Current-Location-Id", String.valueOf(user.currentLocationId()));
                        }
                        headers.set("X-At-Home", String.valueOf(user.atHome()));
                    })
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(readTimeout)
                    .block();
        } catch (RuntimeException e) {
            throw translate(e, chatbot.getChatbotId(), System.currentTimeMillis() - startedAt);
        }

        JsonNode response = parse(raw, chatbot.getChatbotId());
        if (!response.hasNonNull("reply")) {
            log.error("AI 서버 응답에 reply가 없습니다. chatbotId={}, body={}", chatbot.getChatbotId(), raw);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "챗봇 응답을 처리하지 못했습니다.");
        }

        log.info("AI 서버 호출 성공: chatbotId={}, elapsedMs={}",
                chatbot.getChatbotId(), System.currentTimeMillis() - startedAt);
        return new ChatAnswer(response.path("reply").asString(), toSources(response.path("sources")));
    }

    @Override
    public ChatHealth health() {
        try {
            webClient.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(healthTimeout)
                    .block();
            return new ChatHealth(true, null);
        } catch (RuntimeException e) {
            log.warn("챗봇 헬스체크 실패: {}", e.toString());
            return new ChatHealth(false, "챗봇 서버에 연결할 수 없습니다.");
        }
    }

    /**
     * 응답이 JSON이 아니면 500으로 새지 않게 502로 끊는다.
     * 게시물 생성의 AI 심사 실패와 같은 취급이다.
     */
    private JsonNode parse(String raw, String chatbotId) {
        if (raw == null || raw.isBlank()) {
            log.error("AI 서버 응답이 비어 있습니다. chatbotId={}", chatbotId);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "챗봇 응답을 처리하지 못했습니다.");
        }
        try {
            return objectMapper.readTree(raw);
        } catch (RuntimeException e) {
            log.error("AI 서버 응답이 JSON 형식이 아닙니다. chatbotId={}, body={}", chatbotId, raw);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "챗봇 응답을 처리하지 못했습니다.");
        }
    }

    private List<Map<String, String>> toHistoryPayload(List<ChatTurn> history) {
        List<Map<String, String>> payload = new ArrayList<>();
        for (ChatTurn turn : history) {
            payload.add(Map.of(
                    "role", turn.role().name().toLowerCase(),
                    "content", turn.content()
            ));
        }
        return payload;
    }

    private List<ChatSource> toSources(JsonNode node) {
        List<ChatSource> sources = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return sources;
        }
        for (JsonNode item : node) {
            sources.add(new ChatSource(text(item, "type"), text(item, "id"), text(item, "title")));
        }
        return sources;
    }

    private String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.path(field).asString() : null;
    }

    private void setEncoded(HeaderSetter setter, String name, String value) {
        if (value == null) {
            return;
        }
        setter.set(name, URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    /**
     * 실패 원인을 클라이언트가 구분할 수 있는 상태로 바꾼다.
     * 연결 실패(노트북이 꺼짐)와 응답 지연(모델이 느림)은 앱에서 다르게 안내해야 한다.
     */
    private ResponseStatusException translate(RuntimeException e, String chatbotId, long elapsedMs) {
        if (hasCause(e, TimeoutException.class)) {
            log.error("AI 서버 응답 시간 초과: chatbotId={}, elapsedMs={}", chatbotId, elapsedMs);
            return new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "챗봇이 답하는 데 너무 오래 걸립니다. 잠시 후 다시 시도해주세요.");
        }
        if (e instanceof WebClientResponseException responseException) {
            log.error("AI 서버 오류 응답: chatbotId={}, status={}, body={}",
                    chatbotId, responseException.getStatusCode(), responseException.getResponseBodyAsString());
            return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "챗봇 응답을 처리하지 못했습니다.");
        }
        log.error("AI 서버 연결 실패: chatbotId={}, elapsedMs={}, cause={}", chatbotId, elapsedMs, e.toString());
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "챗봇이 지금 쉬고 있어요. 잠시 후 다시 시도해주세요.");
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @FunctionalInterface
    private interface HeaderSetter {
        void set(String name, String value);
    }
}
