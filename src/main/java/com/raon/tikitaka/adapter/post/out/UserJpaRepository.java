package com.raon.tikitaka.adapter.post.out;

import com.raon.tikitaka.domain.user.Users;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<Users, UUID> {
}
