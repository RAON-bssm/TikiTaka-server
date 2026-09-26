package com.raon.tikitaka.adapter.chat;

import com.raon.tikitaka.adapter.chat.dto.ChatAnswerResponse;
import com.raon.tikitaka.adapter.chat.dto.ChatHealthResponse;
import com.raon.tikitaka.adapter.chat.dto.ChatHistoryResponse;
import com.raon.tikitaka.adapter.chat.dto.ChatRequest;
import com.raon.tikitaka.application.chat.ChatHistory;
import com.raon.tikitaka.application.chat.ChatMessageView;
import com.raon.tikitaka.application.chat.in.CheckChatHealthUseCase;
import com.raon.tikitaka.application.chat.in.ClearChatUseCase;
import com.raon.tikitaka.application.chat.in.GetChatHistoryUseCase;
import com.raon.tikitaka.application.chat.in.SendChatUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 동네 정보 공유 챗봇 API.
 *
 * 스프링은 중계와 이력 관리만 한다. 모델 선택, 프롬프트 조립, 벡터DB 검색은 전부 AI 서버 몫이다.
 * chatbot_id는 @RequestParam 이름을 명시해야 한다. Jackson의 SNAKE_CASE 설정은
 * 요청 파라미터 이름에 적용되지 않아서 빼면 chatbotId가 된다.
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final SendChatUseCase sendChatUseCase;
    private final GetChatHistoryUseCase getChatHistoryUseCase;
    private final ClearChatUseCase clearChatUseCase;
    private final CheckChatHealthUseCase checkChatHealthUseCase;

    @PostMapping
    public ApiResponse<ChatAnswerResponse> send(
            @AuthenticationPrincipal UUID userId,
            @RequestParam("chatbot_id") String chatbotId,
            @RequestBody ChatRequest request
    ) {
        ChatMessageView answer = sendChatUseCase.send(userId, chatbotId, request.message());
        return ApiResponse.of(200, "질문 전송 성공", ChatAnswerResponse.from(answer));
    }

    /**
     * before를 주면 그 시각 이전 메시지를 가져온다. 메시지는 계속 늘어나므로
     * 페이지 번호 대신 커서를 쓴다. 번호 방식은 새 메시지가 들어오면 경계가 밀려
     * 같은 글이 두 번 보이거나 건너뛰게 된다.
     */
    @GetMapping
    public ApiResponse<ChatHistoryResponse> getHistory(
            @AuthenticationPrincipal UUID userId,
            @RequestParam("chatbot_id") String chatbotId,
            @RequestParam(value = "before", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        ChatHistory history = getChatHistoryUseCase.getHistory(userId, chatbotId, before, size);
        return ApiResponse.of(200, "대화 목록 조회 성공", ChatHistoryResponse.from(chatbotId, history));
    }

    @DeleteMapping
    public ApiResponse<Void> clear(
            @AuthenticationPrincipal UUID userId,
            @RequestParam("chatbot_id") String chatbotId
    ) {
        clearChatUseCase.clear(userId, chatbotId);
        return ApiResponse.of(204, "대화 초기화 성공", null);
    }

    /**
     * 챗봇이 지금 답할 수 있는지. 로컬 LLM이 노트북에서 돌아 꺼져 있는 경우가 흔해서,
     * 앱이 질문을 보내기 전에 미리 안내할 수 있도록 둔다.
     */
    @GetMapping("/health")
    public ApiResponse<ChatHealthResponse> health() {
        return ApiResponse.of(200, "챗봇 상태 조회 성공",
                ChatHealthResponse.from(checkChatHealthUseCase.check()));
    }
}
