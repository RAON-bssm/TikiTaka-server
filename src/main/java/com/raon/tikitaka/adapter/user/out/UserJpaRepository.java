package com.raon.tikitaka.adapter.user.out;

import com.raon.tikitaka.domain.enums.LoginProvider;
import com.raon.tikitaka.domain.user.Users;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<Users, UUID> {

    Optional<Users> findByProviderAndProviderId(LoginProvider provider, String providerId);

    boolean existsByUserName(String userName);
}
