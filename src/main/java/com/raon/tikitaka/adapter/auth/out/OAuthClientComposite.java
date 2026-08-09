package com.raon.tikitaka.adapter.auth.out;

import com.raon.tikitaka.application.auth.SocialUserInfo;
import com.raon.tikitaka.application.auth.out.OAuthClientPort;
import com.raon.tikitaka.domain.enums.LoginProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OAuthClientComposite implements OAuthClientPort {

    private final List<ProviderOAuthClient> providerOAuthClients;

    @Override
    public SocialUserInfo fetchUserInfo(LoginProvider provider, String providerAccessToken) {
        return providerOAuthClients.stream()
                .filter(client -> client.getProvider() == provider)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 로그인 provider입니다: " + provider))
                .fetchUserInfo(providerAccessToken);
    }
}
