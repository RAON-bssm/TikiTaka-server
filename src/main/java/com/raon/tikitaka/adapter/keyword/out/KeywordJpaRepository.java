package com.raon.tikitaka.adapter.keyword.out;

import com.raon.tikitaka.domain.keyword.Keyword;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KeywordJpaRepository extends JpaRepository<Keyword, String> {

    List<Keyword> findAllByType(String type);
}
