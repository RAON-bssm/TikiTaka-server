#!/bin/bash
# 로컬 검증용 JWT 생성기 — 서버(JwtProvider)와 동일한 방식(HS256)으로 액세스 토큰을 찍는다.
# 카카오 로그인 없이 기존 테스트 유저의 토큰을 만들 때 사용. 운영에서는 쓰지 말 것.
#
# 사용법:  ./gen_jwt.sh <userId(UUID)> [역할: USER|ADMIN, 기본 USER]
# 예시:    ./gen_jwt.sh 1bb5be1e-4401-410d-848b-8154f7451db3 USER
#
# 주의: 프로젝트 루트(.env가 있는 곳)에서 실행해야 한다.

set -e

USER_ID="$1"
ROLE="${2:-USER}"

if [ -z "$USER_ID" ]; then
  echo "사용법: ./gen_jwt.sh <userId(UUID)> [USER|ADMIN]" >&2
  exit 1
fi

# .env에서 서버와 같은 서명 키를 읽는다
SECRET=$(grep '^JWT_SECRET=' .env | cut -d= -f2-)
if [ -z "$SECRET" ]; then
  echo ".env에서 JWT_SECRET을 찾을 수 없습니다." >&2
  exit 1
fi

# base64url 인코딩 (JWT 표준: +/ 를 -_ 로, 패딩 = 제거)
b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

NOW=$(date +%s)
EXP=$((NOW + 3600))   # 1시간 유효

HEADER=$(printf '{"alg":"HS256"}' | b64url)
PAYLOAD=$(printf '{"sub":"%s","tokenType":"ACCESS","role":"%s","iat":%d,"exp":%d}' \
  "$USER_ID" "$ROLE" "$NOW" "$EXP" | b64url)
SIG=$(printf '%s.%s' "$HEADER" "$PAYLOAD" \
  | openssl dgst -sha256 -hmac "$SECRET" -binary | b64url)

echo "$HEADER.$PAYLOAD.$SIG"
