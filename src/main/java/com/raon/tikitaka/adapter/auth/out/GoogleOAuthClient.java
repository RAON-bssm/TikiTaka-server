package com.raon.tikitaka.adapter.auth.out;

import com.raon.tikitaka.application.auth.SocialUserInfo;
import com.raon.tikitaka.domain.enums.LoginProvider;
import com.raon.tikitaka.global.exception.SocialLoginFailedException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.JsonNode;

@Component
public class GoogleOAuthClient implements ProviderOAuthClient {

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://www.googleapis.com")
            .build();

    @Override
    public LoginProvider getProvider() {
        return LoginProvider.GOOGLE;
    }

    @Override
    public SocialUserInfo fetchUserInfo(String providerAccessToken) {
        JsonNode response;
        try {
            response = webClient.get()
                    .uri("/oauth2/v3/userinfo")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerAccessToken)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (WebClientResponseException e) {
            throw new SocialLoginFailedException(LoginProvider.GOOGLE, e);
        }

        String googleId = response.path("sub").asString();
        return new SocialUserInfo(LoginProvider.GOOGLE, googleId);
    }
}
