package com.raon.tikitaka.application.appversion.view;

import com.raon.tikitaka.application.appversion.UpdateType;

/**
 * 앱 버전 확인 결과. null인 필드는 응답에서 빠진다.
 *
 * @param currentVersion      요청 값 그대로
 * @param latestVersion       해당 플랫폼 최신 버전. 기능이 꺼져 있으면(설정 없음, 형식 오류) null
 * @param minSupportedVersion 해당 플랫폼 최소 지원 버전. 설정이 없거나 적용하지 않으면(형식 오류, 최신 버전보다 큼) null
 * @param storeUrl            스토어 주소. updateType이 NONE이면 null
 */
public record AppVersionCheckView(UpdateType updateType, String currentVersion, String latestVersion,
                                  String minSupportedVersion, String storeUrl) {
}
