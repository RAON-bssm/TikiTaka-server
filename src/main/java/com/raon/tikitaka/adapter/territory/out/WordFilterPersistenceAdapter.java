package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.application.territory.out.WordFilterPort;
import com.raon.tikitaka.domain.territory.AllowedWord;
import com.raon.tikitaka.domain.territory.BannedWord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class WordFilterPersistenceAdapter implements WordFilterPort {

    private final BannedWordJpaRepository bannedWordJpaRepository;
    private final AllowedWordJpaRepository allowedWordJpaRepository;

    @Override
    public List<String> findAllBannedWords() {
        return bannedWordJpaRepository.findAll().stream().map(BannedWord::getWord).toList();
    }

    @Override
    public List<String> findAllAllowedWords() {
        return allowedWordJpaRepository.findAll().stream().map(AllowedWord::getWord).toList();
    }
}
