package com.raon.tikitaka.adapter.territory;

import com.raon.tikitaka.adapter.territory.dto.DefensePhotoRequest;
import com.raon.tikitaka.adapter.territory.dto.MissionChangeRequest;
import com.raon.tikitaka.application.territory.in.ChangeMissionUseCase;
import com.raon.tikitaka.application.territory.in.QueryTerritoryActivityUseCase;
import com.raon.tikitaka.application.territory.in.RetakeDefensePhotoUseCase;
import com.raon.tikitaka.application.territory.view.DevelopmentHistoryView;
import com.raon.tikitaka.application.territory.view.MissionChangeView;
import com.raon.tikitaka.application.territory.view.MyTerritoriesView;
import com.raon.tikitaka.application.territory.view.SetupResultView;
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

/** 땅 주인 활동(미션 변경·대표 사진 재촬영)과 내 땅·개발 이력 조회 */
@RestController
@RequestMapping("/api/territory")
@RequiredArgsConstructor
public class TerritoryOwnerController {

    private final ChangeMissionUseCase changeMissionUseCase;
    private final RetakeDefensePhotoUseCase retakeDefensePhotoUseCase;
    private final QueryTerritoryActivityUseCase queryTerritoryActivityUseCase;
    private final RequestGuard requestGuard;

    /** 미션 변경(땅따먹기 라운드). 대기 미션으로 저장되고 대표 사진을 다시 찍을 때 적용된다 */
    @PatchMapping("/dongs/{dongCode}/mission")
    public ApiResponse<MissionChangeView> changeMission(@AuthenticationPrincipal UUID userId, @PathVariable Long dongCode,
                                                        @RequestBody MissionChangeRequest request) {
        MissionChangeView result = changeMissionUseCase.requestChange(userId, dongCode,
                request.adjective(), request.noun(), request.scoringCriteria());
        return ApiResponse.of(200, "미션 변경 요청 성공", result);
    }

    /** 대표 사진 재촬영(땅따먹기 라운드). 더블탭으로 AI·S3가 두 번 돌지 않게 감싼다 */
    @PostMapping(value = "/dongs/{dongCode}/defense-photo", consumes = "multipart/form-data")
    public ApiResponse<SetupResultView> retake(@AuthenticationPrincipal UUID userId, @PathVariable Long dongCode,
                                               @ModelAttribute DefensePhotoRequest request) {
        MultipartFile image = request.image();
        if (image == null || image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지는 필수입니다.");
        }
        SetupResultView result = requestGuard.execute("territory-retake:" + userId, "대표 사진을 처리하는 중이에요.",
                () -> retakeDefensePhotoUseCase.retake(userId, dongCode, new RetakeDefensePhotoUseCase.Command(
                        readBytes(image), image.getOriginalFilename(), image.getContentType())));
        return ApiResponse.of(200, "대표 사진 재촬영 성공", result);
    }

    @GetMapping("/me")
    public ApiResponse<MyTerritoriesView> getMyTerritories(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.of(200, "내 땅 조회 성공", queryTerritoryActivityUseCase.getMyTerritories(userId));
    }

    @GetMapping("/dongs/{dongCode}/developments")
    public ApiResponse<DevelopmentHistoryView> getDevelopments(@AuthenticationPrincipal UUID userId,
                                                               @PathVariable Long dongCode) {
        return ApiResponse.of(200, "땅 개발 이력 조회 성공", queryTerritoryActivityUseCase.getDevelopments(userId, dongCode));
    }

    private static byte[] readBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지를 읽을 수 없습니다.");
        }
    }
}
