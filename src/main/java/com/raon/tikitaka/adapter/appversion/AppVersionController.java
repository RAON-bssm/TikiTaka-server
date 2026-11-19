package com.raon.tikitaka.adapter.appversion;

import com.raon.tikitaka.adapter.appversion.dto.AppVersionResponse;
import com.raon.tikitaka.application.appversion.in.CheckAppVersionUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 앱 버전 확인 API. 앱이 로그인 전에도 부르므로 SecurityConfig에서 GET /api/app/version만 열어 둔다.
 *
 * 파라미터는 required=false로 받는다. 누락을 스프링 기본 예외(MissingServletRequestParameterException)로 두면
 * 공통 응답 형식이 아닌 스프링 기본 에러 바디가 나가므로, 서비스가 직접 검증해 한글 메시지의 400을 낸다.
 */
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class AppVersionController {

    private final CheckAppVersionUseCase checkAppVersionUseCase;

    @GetMapping("/version")
    public ApiResponse<AppVersionResponse> checkVersion(
            @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "current_version", required = false) String currentVersion) {
        AppVersionResponse response = AppVersionResponse.from(checkAppVersionUseCase.check(platform, currentVersion));
        return ApiResponse.of(200, "앱 버전 확인 성공", response);
    }
}
