package com.raon.tikitaka.adapter.storage;

import com.raon.tikitaka.application.storage.StorageService;
import com.raon.tikitaka.application.storage.out.StoragePort;
import com.raon.tikitaka.global.security.JwtAccessDeniedHandler;
import com.raon.tikitaka.global.security.JwtAuthenticationEntryPoint;
import com.raon.tikitaka.global.security.SecurityConfig;
import com.raon.tikitaka.global.security.SecurityErrorWriter;
import com.raon.tikitaka.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.URI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 조회 URL 단건·일괄 API를 실제 보안 설정과 함께 HTTP로 호출해 본다. 검증은 실제 StorageService가 하고
 * 서명(StoragePort)만 가짜다. 토큰 없이 호출하므로 비로그인 접근 정책도 같이 확인된다.
 */
@WebMvcTest(StorageController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class,
        SecurityErrorWriter.class, StorageService.class})
class StorageControllerViewUrlsTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean StoragePort storagePort;
    @MockitoBean JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        when(storagePort.issueViewUrl(anyString(), any())).thenAnswer(inv ->
                URI.create("https://bucket.s3.ap-northeast-2.amazonaws.com/" + inv.getArgument(0) + "?X-Amz-Signature=abc")
                        .toURL());
    }

    // ===== 일괄 =====

    @Test
    void 비로그인으로_여러_key를_보내면_요청_순서대로_key와_URL을_짝지어_준다() throws Exception {
        mockMvc.perform(get("/api/storage/view-urls?key=b.jpg&key=dir/a.jpg&key=b.jpg&key=c.jpg"))
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
        mockMvc.perform(get("/api/storage/view-urls").param("key", "1b2c-a,b.jpg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.urls.length()").value(1))
                .andExpect(jsonPath("$.data.urls[0].key").value("1b2c-a,b.jpg"));
    }

    @Test
    void key_50개는_통과한다() throws Exception {
        mockMvc.perform(withKeys(50))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.urls.length()").value(50));
    }

    @Test
    void key가_없거나_51개면_400이고_기존_응답_형식이다() throws Exception {
        String body = "{\"status\":400,\"message\":\"key는 1개 이상 50개 이하여야 합니다.\"}";
        mockMvc.perform(get("/api/storage/view-urls"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(body, true));
        mockMvc.perform(withKeys(51))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(body, true));
    }

    @Test
    void 빈_key나_공백뿐인_key가_섞이면_400() throws Exception {
        String body = "{\"status\":400,\"message\":\"빈 key는 보낼 수 없습니다.\"}";
        mockMvc.perform(get("/api/storage/view-urls?key=a.jpg&key="))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(body, true));
        mockMvc.perform(get("/api/storage/view-urls").param("key", "a.jpg", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(body, true));
    }

    // ===== 단건과 다른 경로의 정책 =====

    @Test
    void 단건_view_url_응답은_그대로다() throws Exception {
        mockMvc.perform(get("/api/storage/view-url").param("key", "1b2c-photo.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"status\":200,\"message\":\"조회 URL 발급 성공\",\"data\":{"
                        + "\"url\":\"https://bucket.s3.ap-northeast-2.amazonaws.com/1b2c-photo.jpg?X-Amz-Signature=abc\","
                        + "\"key\":\"1b2c-photo.jpg\"}}"));
    }

    @Test
    void upload_url은_계속_인증이_필요하다() throws Exception {
        mockMvc.perform(get("/api/storage/upload-url").param("fileName", "a.jpg"))
                .andExpect(status().isUnauthorized());
    }

    // ===== helpers =====

    private static MockHttpServletRequestBuilder withKeys(int count) {
        String[] keys = new String[count];
        for (int i = 0; i < count; i++) {
            keys[i] = "k" + i + ".jpg";
        }
        return get("/api/storage/view-urls").param("key", keys);
    }
}
