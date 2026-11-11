package com.raon.tikitaka.adapter.storage;

import com.raon.tikitaka.application.storage.StorageService;
import com.raon.tikitaka.application.storage.out.StoragePort;
import com.raon.tikitaka.global.exception.InvalidTokenException;
import com.raon.tikitaka.global.security.JwtAccessDeniedHandler;
import com.raon.tikitaka.global.security.JwtAuthenticationEntryPoint;
import com.raon.tikitaka.global.security.SecurityConfig;
import com.raon.tikitaka.global.security.SecurityErrorWriter;
import com.raon.tikitaka.global.security.jwt.JwtProvider;
import com.raon.tikitaka.global.security.jwt.TokenErrorCode;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.URI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 조회 URL 단건·일괄 API를 실제 보안 설정과 함께 HTTP로 호출해 본다. 검증은 실제 StorageService가 하고
 * 서명(StoragePort)과 토큰 해석(JwtProvider)만 가짜다.
 * 일괄(view-urls)은 로그인이 필요하고, 단건(view-url)은 지금처럼 비로그인도 호출할 수 있다.
 */
@WebMvcTest(StorageController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class,
        SecurityErrorWriter.class, StorageService.class})
class StorageControllerViewUrlsTest {

    private static final String VALID_TOKEN = "Bearer valid-access-token";
    private static final String EXPIRED_TOKEN = "Bearer expired-access-token";

    @Autowired MockMvc mockMvc;

    @MockitoBean StoragePort storagePort;
    @MockitoBean JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        when(storagePort.issueViewUrl(anyString(), any())).thenAnswer(inv ->
                URI.create("https://bucket.s3.ap-northeast-2.amazonaws.com/" + inv.getArgument(0) + "?X-Amz-Signature=abc")
                        .toURL());

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("11111111-1111-1111-1111-000000000001");
        when(claims.get("role", String.class)).thenReturn("USER");
        when(jwtProvider.parseAccessToken("valid-access-token")).thenReturn(claims);
        when(jwtProvider.parseAccessToken("expired-access-token"))
                .thenThrow(new InvalidTokenException(TokenErrorCode.TOKEN_EXPIRED, "expired"));
    }

    // ===== 일괄: 인증 =====

    @Test
    void 일괄은_토큰이_없으면_기존_인증_실패_형식의_401이고_서명하지_않는다() throws Exception {
        mockMvc.perform(get("/api/storage/view-urls?key=a.jpg&key=b.jpg"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(
                        "{\"status\":401,\"message\":\"인증이 필요합니다.\",\"data\":{\"code\":\"TOKEN_MISSING\"}}", true));
        verify(storagePort, never()).issueViewUrl(anyString(), any());
    }

    @Test
    void 일괄은_만료된_토큰이면_401_TOKEN_EXPIRED다() throws Exception {
        mockMvc.perform(get("/api/storage/view-urls?key=a.jpg").header(HttpHeaders.AUTHORIZATION, EXPIRED_TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(
                        "{\"status\":401,\"message\":\"토큰이 만료되었습니다.\",\"data\":{\"code\":\"TOKEN_EXPIRED\"}}", true));
        verify(storagePort, never()).issueViewUrl(anyString(), any());
    }

    // ===== 일괄: 로그인 상태의 동작 =====

    @Test
    void 로그인하고_여러_key를_보내면_요청_순서대로_key와_URL을_짝지어_준다() throws Exception {
        mockMvc.perform(authed(get("/api/storage/view-urls?key=b.jpg&key=dir/a.jpg&key=b.jpg&key=c.jpg")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("조회 URL 일괄 발급 성공"))
                .andExpect(jsonPath("$.data.urls.length()").value(3))
                .andExpect(jsonPath("$.data.urls[0].key").value("b.jpg"))
                .andExpect(jsonPath("$.data.urls[0].url")
                        .value("https://bucket.s3.ap-northeast-2.amazonaws.com/b.jpg?X-Amz-Signature=abc"))
                // key에 '/'가 있어도 URL에서 잘라내지 않고 요청받은 key 그대로다
                .andExpect(jsonPath("$.data.urls[1].key").value("dir/a.jpg"))
                .andExpect(jsonPath("$.data.urls[1].url")
                        .value("https://bucket.s3.ap-northeast-2.amazonaws.com/dir/a.jpg?X-Amz-Signature=abc"))
                .andExpect(jsonPath("$.data.urls[2].key").value("c.jpg"));
    }

    @Test
    void 쉼표가_들어간_key도_나뉘지_않는다() throws Exception {
        mockMvc.perform(authed(get("/api/storage/view-urls").param("key", "1b2c-a,b.jpg")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.urls.length()").value(1))
                .andExpect(jsonPath("$.data.urls[0].key").value("1b2c-a,b.jpg"));
    }

    @Test
    void key_50개는_통과한다() throws Exception {
        mockMvc.perform(authed(withKeys(50)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.urls.length()").value(50));
    }

    @Test
    void key가_없거나_51개면_400이고_기존_응답_형식이다() throws Exception {
        String body = "{\"status\":400,\"message\":\"key는 1개 이상 50개 이하여야 합니다.\"}";
        mockMvc.perform(authed(get("/api/storage/view-urls")))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(body, true));
        mockMvc.perform(authed(withKeys(51)))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(body, true));
    }

    @Test
    void 빈_key나_공백뿐인_key가_섞이면_400() throws Exception {
        String body = "{\"status\":400,\"message\":\"빈 key는 보낼 수 없습니다.\"}";
        mockMvc.perform(authed(get("/api/storage/view-urls?key=a.jpg&key=")))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(body, true));
        mockMvc.perform(authed(get("/api/storage/view-urls").param("key", "a.jpg", "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(body, true));
    }

    // ===== 단건과 다른 경로의 정책 =====

    @Test
    void 단건_view_url은_지금처럼_비로그인으로_호출되고_응답도_그대로다() throws Exception {
        String body = "{\"status\":200,\"message\":\"조회 URL 발급 성공\",\"data\":{"
                + "\"url\":\"https://bucket.s3.ap-northeast-2.amazonaws.com/1b2c-photo.jpg?X-Amz-Signature=abc\","
                + "\"key\":\"1b2c-photo.jpg\"}}";
        mockMvc.perform(get("/api/storage/view-url").param("key", "1b2c-photo.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().string(body));
        // 만료된 토큰을 달고 와도 permitAll 경로라 비로그인처럼 통과한다(기존 동작)
        mockMvc.perform(get("/api/storage/view-url").param("key", "1b2c-photo.jpg")
                        .header(HttpHeaders.AUTHORIZATION, EXPIRED_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string(body));
    }

    @Test
    void upload_url은_계속_인증이_필요하다() throws Exception {
        mockMvc.perform(get("/api/storage/upload-url").param("fileName", "a.jpg"))
                .andExpect(status().isUnauthorized());
    }

    // ===== helpers =====

    private static MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, VALID_TOKEN);
    }

    private static MockHttpServletRequestBuilder withKeys(int count) {
        String[] keys = new String[count];
        for (int i = 0; i < count; i++) {
            keys[i] = "k" + i + ".jpg";
        }
        return get("/api/storage/view-urls").param("key", keys);
    }
}
