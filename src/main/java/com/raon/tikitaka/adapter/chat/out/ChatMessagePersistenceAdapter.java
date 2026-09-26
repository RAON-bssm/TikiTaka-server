package com.raon.tikitaka.adapter.chat.out;

import com.raon.tikitaka.application.chat.out.ChatMessageRepositoryPort;
import com.raon.tikitaka.domain.chat.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ChatMessagePersistenceAdapter implements ChatMessageRepositoryPort {

    private final ChatMessageJpaRepository chatMessageJpaRepository;

    @Override
    public List<ChatMessage> findRecent(UUID userId, String chatbotId, LocalDateTime before, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        return before == null
                ? chatMessageJpaRepository.findRecent(userId, chatbotId, page)
                : chatMessageJpaRepository.findRecentBefore(userId, chatbotId, before, page);
    }

    @Override
    public Optional<ChatMessage> findLatest(UUID userId, String chatbotId) {
        List<ChatMessage> found = chatMessageJpaRepository.findRecent(userId, chatbotId, PageRequest.of(0, 1));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    @Override
    public List<ChatMessage> saveAll(List<ChatMessage> messages) {
        return chatMessageJpaRepository.saveAll(messages);
    }

    @Override
    public void deleteAll(UUID userId, String chatbotId) {
        chatMessageJpaRepository.deleteAllByUserAndChatbot(userId, chatbotId);
    }
}
