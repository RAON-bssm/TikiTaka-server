package com.raon.tikitaka.adapter.territory;

import com.raon.tikitaka.adapter.territory.dto.ChallengeRequest;
import com.raon.tikitaka.application.territory.in.ChallengeUseCase;
import com.raon.tikitaka.application.territory.view.ChallengeResultView;
import com.raon.tikitaka.global.guard.RequestGuard;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/territory")
@RequiredArgsConstructor
public class ChallengeController {

    private final ChallengeUseCase challengeUseCase;
    private final RequestGuard requestGuard;

    /** 도전. 더블탭으로 AI 채점·S3 업로드·통행료가 두 번 일어나지 않게 전체를 감싼다 */
    @PostMapping(value = "/dongs/{dongCode}/challenge", consumes = "multipart/form-data")
    public ApiResponse<ChallengeResultView> challenge(@AuthenticationPrincipal UUID userId, @PathVariable Long dongCode,
                                                      @ModelAttribute ChallengeRequest request) {
        MultipartFile image = request.image();
        if (image == null || image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지는 필수입니다.");
        }
        ChallengeResultView result = requestGuard.execute("territory-challenge:" + userId, "도전을 처리하는 중이에요.",
                () -> challengeUseCase.challenge(userId, dongCode, new ChallengeUseCase.Command(
                        readBytes(image), image.getOriginalFilename(), image.getContentType(),
                        request.lat(), request.lng())));
        return ApiResponse.of(200, "도전 완료", result);
    }

    @GetMapping("/challenges/{challengeId}")
    public ApiResponse<ChallengeResultView> getResult(@AuthenticationPrincipal UUID userId,
                                                      @PathVariable UUID challengeId) {
        return ApiResponse.of(200, "도전 결과 조회 성공", challengeUseCase.getResult(userId, challengeId));
    }

    private static byte[] readBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지를 읽을 수 없습니다.");
        }
    }
}
