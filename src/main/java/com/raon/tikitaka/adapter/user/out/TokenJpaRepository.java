package com.raon.tikitaka.adapter.user.out;

import com.raon.tikitaka.domain.token.Token;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TokenJpaRepository extends JpaRepository<Token, UUID> {
}
