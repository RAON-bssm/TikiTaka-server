package com.raon.tikitaka.adapter.appversion;

import com.raon.tikitaka.application.appversion.AppVersionService;
import com.raon.tikitaka.global.config.AppVersionProperties;
import com.raon.tikitaka.global.security.JwtAccessDeniedHandler;
import com.raon.tikitaka.global.security.JwtAuthenticationEntryPoint;
import com.raon.tikitaka.global.security.SecurityConfig;
import com.raon.tikitaka.global.security.SecurityErrorWriter;
import com.raon.tikitaka.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 앱 버전 확인 API를 실제 보안 설정, 실제 서비스, 실제 설정 바인딩과 함께 HTTP로 호출해 본다.
 * 안드로이드만 설정하고 iOS는 비워 둔다(iOS 스토어 주소는 배포 담당이 넣는다).
 */
@WebMvcTest(AppVersionController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class,
        SecurityErrorWriter.class, AppVersionService.class})
@EnableConfigurationProperties(AppVersionProperties.class)
@TestPropertySource(properties = {
        "app-version.android.latest=1.4.0",
        "app-version.android.min-supported=1.2.0",
        "app-version.android.store-url=https://play.google.com/store/apps/details?id=com.tikitaka.raon",
        "app-version.ios.latest=",
        "app-version.ios.min-supported=",
        "app-version.ios.store-url="
})
class AppVersionControllerTest {

    private static final String PLAY = "https://play.google.com/store/apps/details?id=com.tikitaka.raon";

    @Autowired MockMvc mockMvc;

    // 보안 필터의 의존성. 이 API는 토큰을 보지 않는다
    @MockitoBean JwtProvider jwtProvider;

    // ===== 인증 없이 200 =====

    @Test
    void 토큰_없이_200이고_FORCE면_모든_키가_snake_case로_나간다() throws Exception {
        mockMvc.perform(get("/api/app/version?platform=android&current_version=1.1.0"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"status":200,"message":"앱 버전 확인 성공","data":{
                          "update_type":"FORCE","current_version":"1.1.0","latest_version":"1.4.0",
                          "min_supported_version":"1.2.0","store_url":"%s"}}
                        """.formatted(PLAY), true));
    }

    @Test
    void OPTIONAL() throws Exception {
        mockMvc.perform(get("/api/app/version?platform=android&current_version=1.3.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.update_type").value("OPTIONAL"))
                .andExpect(jsonPath("$.data.store_url").value(PLAY));
    }

    @Test
    void NONE이면_store_url이_응답에서_빠진다() throws Exception {
        mockMvc.perform(get("/api/app/version?platform=android&current_version=1.5.0"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"status":200,"message":"앱 버전 확인 성공","data":{
                          "update_type":"NONE","current_version":"1.5.0","latest_version":"1.4.0",
                          "min_supported_version":"1.2.0"}}
                        """, true))
                .andExpect(jsonPath("$.data.store_url").doesNotExist())
                .andExpect(jsonPath("$.data.updateType").doesNotExist());
    }

    @Test
    void ios_설정이_비어_있으면_NONE이고_android_값이_섞이지_않는다() throws Exception {
        mockMvc.perform(get("/api/app/version?platform=IOS&current_version=0.0.1"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"status":200,"message":"앱 버전 확인 성공","data":{
                          "update_type":"NONE","current_version":"0.0.1"}}
                        """, true));
    }

    // ===== 입력 검증 400 =====

    @Test
    void 지원하지_않는_platform이면_400() throws Exception {
        mockMvc.perform(get("/api/app/version?platform=web&current_version=1.0.0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(
                        "{\"status\":400,\"message\":\"지원하지 않는 플랫폼입니다.\"}", true));
    }

    @Test
    void platform이_없으면_400() throws Exception {
        mockMvc.perform(get("/api/app/version?current_version=1.0.0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(
                        "{\"status\":400,\"message\":\"지원하지 않는 플랫폼입니다.\"}", true));
    }

    @Test
    void current_version_형식이_틀리면_400() throws Exception {
        mockMvc.perform(get("/api/app/version?platform=android&current_version=abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(
                        "{\"status\":400,\"message\":\"앱 버전 형식이 올바르지 않습니다.\"}", true));
    }

    @Test
    void current_version이_없으면_400() throws Exception {
        mockMvc.perform(get("/api/app/version?platform=android"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(
                        "{\"status\":400,\"message\":\"앱 버전 형식이 올바르지 않습니다.\"}", true));
    }

    @Test
    void camelCase_파라미터_currentVersion은_받지_않는다() throws Exception {
        mockMvc.perform(get("/api/app/version?platform=android&currentVersion=1.0.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("앱 버전 형식이 올바르지 않습니다."));
    }

    // ===== 보안: GET /api/app/version만 열린다 =====

    @Test
    void 같은_경로라도_GET이_아니면_인증이_필요하다() throws Exception {
        mockMvc.perform(post("/api/app/version?platform=android&current_version=1.0.0"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data.code").value("TOKEN_MISSING"));
    }

    @Test
    void 다른_api_app_경로는_여전히_인증이_필요하다() throws Exception {
        mockMvc.perform(get("/api/app/other"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data.code").value("TOKEN_MISSING"));
        mockMvc.perform(get("/api/app/version/extra"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/app"))
                .andExpect(status().isUnauthorized());
    }
}
