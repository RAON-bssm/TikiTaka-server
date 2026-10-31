package com.raon.tikitaka.adapter.territory;

import com.raon.tikitaka.adapter.territory.dto.SetupTerritoryRequest;
import com.raon.tikitaka.application.territory.in.QueryTerritoryUseCase;
import com.raon.tikitaka.application.territory.in.SetupTerritoryUseCase;
import com.raon.tikitaka.application.territory.view.SetupResultView;
import com.raon.tikitaka.application.territory.view.CurrentCycleView;
import com.raon.tikitaka.application.territory.view.TerritoryDetailView;
import com.raon.tikitaka.application.territory.view.TerritoryDongsView;
import com.raon.tikitaka.global.guard.RequestGuard;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

/** 조회 응답은 application의 view 레코드를 그대로 쓴다(필드 가공이 없어 DTO를 따로 두지 않는다) */
@RestController
@RequestMapping("/api/territory")
@RequiredArgsConstructor
public class TerritoryController {

    private final QueryTerritoryUseCase queryTerritoryUseCase;
    private final SetupTerritoryUseCase setupTerritoryUseCase;
    private final RequestGuard requestGuard;

    @GetMapping("/cycle/current")
    public ApiResponse<CurrentCycleView> getCurrentCycle(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.of(200, "현재 사이클 조회 성공", queryTerritoryUseCase.getCurrentCycle(userId));
    }

    /** 동 정보 목록. locationId가 없으면 보드 전체 */
    @GetMapping("/dongs")
    public ApiResponse<TerritoryDongsView> getDongs(@AuthenticationPrincipal UUID userId,
                                                    @RequestParam(required = false) Long locationId) {
        return ApiResponse.of(200, "동 정보 조회 성공", queryTerritoryUseCase.getDongs(userId, locationId));
    }

    /** 동 상세. 앱이 아는 dong_code(열거형 값)로 조회한다 */
    @GetMapping("/dongs/{dongCode}")
    public ApiResponse<TerritoryDetailView> getDongDetail(@AuthenticationPrincipal UUID userId,
                                                          @PathVariable Long dongCode) {
        return ApiResponse.of(200, "동 상세 조회 성공", queryTerritoryUseCase.getDongDetail(userId, dongCode));
    }

    /** 땅 세팅(5~7일차): 미션 키워드 + 채점 기준 + 대표 사진. 더블탭으로 AI·S3가 두 번 돌지 않게 감싼다 */
    @PostMapping(value = "/dongs/{dongCode}/setup", consumes = "multipart/form-data")
    public ApiResponse<SetupResultView> setup(@AuthenticationPrincipal UUID userId, @PathVariable Long dongCode,
                                              @ModelAttribute SetupTerritoryRequest request) {
        MultipartFile image = request.image();
        if (image == null || image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지는 필수입니다.");
        }
        SetupResultView result = requestGuard.execute("territory-setup:" + userId, "땅을 세팅하는 중이에요.",
                () -> setupTerritoryUseCase.setup(userId, dongCode, new SetupTerritoryUseCase.Command(
                        request.adjective(), request.noun(), request.scoringCriteria(),
                        readBytes(image), image.getOriginalFilename(), image.getContentType())));
        return ApiResponse.of(200, "땅 세팅 성공", result);
    }

    private static byte[] readBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지를 읽을 수 없습니다.");
        }
    }
}
