#!/usr/bin/env bash
# 스위칭·키워드·게시판·내 정보 API 검증 스크립트
# 사용법: bash verify_api.sh [유저UUID]  (기본값: test03 유저)
set -u

BASE="http://localhost:8090"
USER_ID="${1:-56dd9ccc-7a65-47f6-b0b4-09a0208d78cc}"
PASS=0
FAIL=0

TOKEN=$(./gen_jwt.sh "$USER_ID" USER)
ADMIN=$(./gen_jwt.sh "$USER_ID" ADMIN)

# 실제 존재하는 동네 ID를 API에서 알아낸다 - 메인 동네와 다른 첫 번째 동네를 서브로 쓴다
MAIN_ID=$(curl -s "$BASE/api/user/me" -H "Authorization: Bearer $TOKEN" | grep -o '"location_id":[0-9]*' | head -1 | cut -d: -f2)
SUB_ID=$(curl -s "$BASE/api/location/rank" -H "Authorization: Bearer $TOKEN" | grep -o '"location_id":[0-9]*' | cut -d: -f2 | grep -v "^${MAIN_ID}$" | head -1)
if [ -z "$MAIN_ID" ] || [ -z "$SUB_ID" ]; then
    echo "동네 ID를 알아내지 못했습니다. 앱이 떠 있고 진행 중인 라운드가 있는지 확인하세요. (main=$MAIN_ID sub=$SUB_ID)"
    exit 1
fi
echo "메인 동네 ID: $MAIN_ID / 서브로 쓸 동네 ID: $SUB_ID"

check() {
    local name="$1" expected="$2" method="$3" url="$4" body="${5:-}" token="${6:-$TOKEN}"
    local args=(-s -o /tmp/verify_body -w "%{http_code}" -X "$method" "$BASE$url" -H "Authorization: Bearer $token")
    if [ -n "$body" ]; then
        args+=(-H "Content-Type: application/json" -d "$body")
    fi
    local code
    code=$(curl "${args[@]}")
    if [ "$code" = "$expected" ]; then
        echo "PASS [$code] $name"
        PASS=$((PASS+1))
    else
        echo "FAIL [$code, 기대 $expected] $name"
        echo "     응답: $(cat /tmp/verify_body)"
        FAIL=$((FAIL+1))
    fi
}

echo "=== 스위칭 API ==="
check "서브 동네 없이 예약 시도 -> 400"        400 POST   /api/user/location-swap
check "메인과 같은 동네를 서브로 설정 -> 400"  400 PUT    /api/user/sub-location "{\"location_id\": $MAIN_ID}"
check "없는 동네(99999)를 서브로 설정 -> 400"  400 PUT    /api/user/sub-location '{"location_id": 99999}'
check "다른 동네를 서브로 설정 -> 200"         200 PUT    /api/user/sub-location "{\"location_id\": $SUB_ID}"
check "스위칭 예약 -> 200"                     200 POST   /api/user/location-swap
check "예약 상태 확인(내 정보) -> 200"         200 GET    /api/user/me
if grep -q '"pending_location_swap":true' /tmp/verify_body && grep -q '"sub_location"' /tmp/verify_body; then
    echo "PASS 내 정보에 sub_location 존재 + pending_location_swap true"
    PASS=$((PASS+1))
else
    echo "FAIL 내 정보 응답에 예약 상태가 반영되지 않음"
    echo "     응답: $(cat /tmp/verify_body)"
    FAIL=$((FAIL+1))
fi
check "예약 취소 -> 200"                       200 DELETE /api/user/location-swap

echo "=== 키워드 API ==="
check "USER 토큰으로 키워드 목록 -> 403"       403 GET    /api/admin/keyword
check "ADMIN 토큰으로 키워드 목록 -> 200"      200 GET    /api/admin/keyword "" "$ADMIN"
check "키워드 추가 -> 201"                     201 POST   /api/admin/keyword '{"keyword": "포근한", "type": "형용사"}' "$ADMIN"
check "같은 키워드 중복 추가 -> 409"           409 POST   /api/admin/keyword '{"keyword": "포근한", "type": "형용사"}' "$ADMIN"
check "잘못된 타입으로 추가 -> 400"            400 POST   /api/admin/keyword '{"keyword": "테스트", "type": "동사"}' "$ADMIN"
check "키워드 삭제 -> 200"                     200 DELETE /api/admin/keyword/포근한 "" "$ADMIN"
check "없는 키워드 삭제 -> 404"                404 DELETE /api/admin/keyword/포근한 "" "$ADMIN"

echo "=== 게시판 전체 공개 ==="
check "게시판 목록 -> 200"                     200 GET    /api/board
if grep -q '"my_match":true' /tmp/verify_body; then
    echo "PASS 게시판 목록에 my_match true 항목 존재"
    PASS=$((PASS+1))
else
    echo "WARN my_match true 항목 없음 - 이 유저 동네가 이번 라운드 매치에 없으면 정상"
fi

echo
echo "결과: PASS $PASS / FAIL $FAIL"
if [ "$FAIL" -eq 0 ]; then
    echo "전부 통과 - fix 커밋 푸시하면 검증 종료"
fi
