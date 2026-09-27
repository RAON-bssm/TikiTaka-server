package com.raon.tikitaka.application.chat;

import com.raon.tikitaka.application.chat.in.CheckChatHealthUseCase;
import com.raon.tikitaka.application.chat.in.ClearChatUseCase;
import com.raon.tikitaka.application.chat.in.GetChatHistoryUseCase;
import com.raon.tikitaka.application.chat.in.GetChatbotListUseCase;
import com.raon.tikitaka.application.chat.in.SendChatUseCase;
import com.raon.tikitaka.application.chat.out.ChatClientPort;
import com.raon.tikitaka.application.chat.out.ChatMessageRepositoryPort;
import com.raon.tikitaka.application.chat.out.ChatbotRepositoryPort;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.chat.ChatMessage;
import com.raon.tikitaka.domain.chat.Chatbot;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.user.Users;
import com.raon.tikitaka.global.config.ChatbotProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 챗봇 대화 서비스. 스프링의 역할은 중계와 이력 관리다.
 * 모델 선택도 프롬프트 조립도 벡터DB 검색도 AI 서버 몫이라 여기서는 다루지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService implements SendChatUseCase, GetChatHistoryUseCase,
        GetChatbotListUseCase, ClearChatUseCase, CheckChatHealthUseCase {

    private static final int DEFAULT_PAGE_SIZE = 30;
    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepositoryPort userRepositoryPort;
    private final ChatbotRepositoryPort chatbotRepositoryPort;
    private final ChatMessageRepositoryPort chatMessageRepositoryPort;
    private final ChatClientPort chatClientPort;
    private final ChatbotProperties chatbotProperties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ChatMessageView send(UUID userId, String chatbotId, String message) {
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "메시지를 입력해주세요.");
        }

        Users user = getUser(userId);
        Chatbot chatbot = getChatbot(chatbotId);

        // 최근 N턴만 싣는다. 전부 보내면 토큰이 터지고 로컬 모델은 그만큼 느려진다
        List<ChatTurn> history = loadHistory(userId, chatbotId);

        ChatAnswer answer = chatClientPort.ask(chatbot, toUserContext(user), history, message);

        List<ChatMessage> saved = chatMessageRepositoryPort.saveAll(List.of(
                ChatMessage.ofUser(user, chatbot, message),
                ChatMessage.ofAssistant(user, chatbot, answer.reply(), writeSources(answer.sources()))
        ));
        return ChatMessageView.of(saved.get(saved.size() - 1), answer.sources());
    }

    @Override
    public ChatHistory getHistory(UUID userId, String chatbotId, LocalDateTime before, Integer size) {
        getChatbot(chatbotId);
        int pageSize = resolvePageSize(size);

        // 다음 페이지가 있는지 알려면 한 건 더 읽어보는 수밖에 없다
        List<ChatMessage> found = chatMessageRepositoryPort.findRecent(userId, chatbotId, before, pageSize + 1);
        boolean hasMore = found.size() > pageSize;
        List<ChatMessage> page = new ArrayList<>(hasMore ? found.subList(0, pageSize) : found);

        LocalDateTime nextBefore = hasMore && !page.isEmpty()
                ? page.get(page.size() - 1).getCreatedAt()
                : null;

        // 조회는 최신순이지만 화면은 오래된 것부터 쌓으므로 뒤집어서 돌려준다
        Collections.reverse(page);

        List<ChatMessageView> views = new ArrayList<>();
        for (ChatMessage message : page) {
            views.add(ChatMessageView.of(message, readSources(message.getSources())));
        }
        return new ChatHistory(views, nextBefore, hasMore);
    }

    @Override
    public List<ChatbotSummary> getChatbots(UUID userId) {
        List<ChatbotSummary> summaries = new ArrayList<>();
        for (Chatbot chatbot : chatbotRepositoryPort.findAllActive()) {
            // 챗봇 수가 적어 건별 조회로 충분하다. 늘어나면 lateral join 한 방으로 바꾼다
            Optional<ChatMessage> latest = chatMessageRepositoryPort.findLatest(userId, chatbot.getChatbotId());
            summaries.add(new ChatbotSummary(
                    chatbot,
                    latest.map(ChatMessage::getContent).orElse(null),
                    latest.map(ChatMessage::getCreatedAt).orElse(null)
            ));
        }
        return summaries;
    }

    @Override
    @Transactional
    public void clear(UUID userId, String chatbotId) {
        getChatbot(chatbotId);
        chatMessageRepositoryPort.deleteAll(userId, chatbotId);
    }

    @Override
    public ChatHealth check() {
        return chatClientPort.health();
    }

    private List<ChatTurn> loadHistory(UUID userId, String chatbotId) {
        int limit = chatbotProperties.historyTurns() * 2;   // 한 턴은 질문 + 답변 두 건이다
        List<ChatMessage> recent = new ArrayList<>(
                chatMessageRepositoryPort.findRecent(userId, chatbotId, null, limit));
        Collections.reverse(recent);

        List<ChatTurn> turns = new ArrayList<>();
        for (ChatMessage message : recent) {
            turns.add(new ChatTurn(message.getRole(), message.getContent()));
        }
        return turns;
    }

    private ChatUserContext toUserContext(Users user) {
        Location main = user.getMainLocation();
        Location current = user.getCurrentLocation();
        return new ChatUserContext(
                user.getUserId(),
                user.getUserName(),
                main != null ? main.getLocationId() : null,
                main != null ? main.getFullName() : null,
                current != null ? current.getLocationId() : null,
                current != null ? current.getFullName() : null,
                user.isAtHome()
        );
    }

    /**
     * sources는 JSON 문자열로 저장한다. 비어 있으면 컬럼을 비워 둔다.
     */
    private String writeSources(List<ChatSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        return objectMapper.writeValueAsString(sources);
    }

    /**
     * 저장된 JSON 문자열을 다시 목록으로 푼다. 형식이 깨져 있어도 대화 자체는 보여야 하므로
     * 예외를 던지지 않고 빈 목록으로 넘어간다.
     */
    private List<ChatSource> readSources(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<ChatSource>>() {
            });
        } catch (RuntimeException e) {
            log.warn("sources 파싱 실패. 빈 목록으로 대체합니다. raw={}", raw);
            return List.of();
        }
    }

    private int resolvePageSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "size는 1 이상 " + MAX_PAGE_SIZE + " 이하여야 합니다.");
        }
        return size;
    }

    private Users getUser(UUID userId) {
        return userRepositoryPort.findByIdWithLocations(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private Chatbot getChatbot(String chatbotId) {
        if (chatbotId == null || chatbotId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chatbot_id를 입력해주세요.");
        }
        return chatbotRepositoryPort.findActiveById(chatbotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 챗봇입니다."));
    }
}
