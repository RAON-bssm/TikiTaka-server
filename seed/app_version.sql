-- 앱 버전 확인(GET /api/app/version) 설정 테이블과 기본 행. 직접 쓴 파일이다(자동 생성 아님).
--
-- 실행: psql -f seed/app_version.sql
-- - 한 트랜잭션이고 여러 번 실행해도 결과가 같다(테이블과 행이 이미 있으면 건드리지 않는다).
-- - JPA_DDL_AUTO가 update면 서버가 기동 때 테이블을 먼저 만들 수 있다. 그래도 이 파일은 그대로 동작하고 행만 넣는다.
-- - 운영 DB에서 JPA_DDL_AUTO가 update가 아니면 테이블이 자동으로 생기지 않으므로 이 파일로 만든다.
-- - 시드는 필수가 아니다. 행이 없는 플랫폼은 꺼진 상태(항상 NONE)이고, 운영자가 직접 INSERT해도 된다.
-- - 두 행 모두 값이 비어 있다(기능 꺼짐). 스토어 주소는 배포 채널과 앱 ID가 정해지면 운영자가 넣는다.
--
-- 테이블과 체크 제약은 엔티티(domain/appversion/AppVersionSetting)와 같다.
-- 버전은 숫자(.숫자){0,2}(각 자리 최대 6자리)만, 스토어 주소는 http:// 또는 https://로 시작하는 값만 들어간다.
-- 'v1.4.0', '1.2.3.4', '1.4.0-beta', 빈 문자열, 'ftp://...'는 DB가 거부한다.
--
-- 운영 SQL 예시
-- 서버는 값을 메모리에 들고 있다가 60초마다 다시 읽는다. 바꾼 값은 서버 재기동 없이 최대 60초 안에 반영된다.
-- 서버가 여러 대면 각자 따로 읽으므로 서버마다 반영 시점이 최대 60초 어긋날 수 있다.
-- 스토어 주소(store_url)가 없으면 최신 버전을 넣어도 업데이트 안내가 나가지 않는다(항상 NONE).
--
--   현재 값 확인
--     SELECT * FROM app_version;
--
--   안드로이드 최신 버전과 스토어 주소 설정
--     UPDATE app_version SET latest_version = '1.4.0', store_url = '<Play 스토어 주소>', updated_at = now() WHERE platform = 'ANDROID';
--
--   iOS 최신 버전과 스토어 주소 설정
--     UPDATE app_version SET latest_version = '1.4.0', store_url = 'https://apps.apple.com/app/id<앱 ID>', updated_at = now() WHERE platform = 'IOS';
--
--   이후 릴리즈에서 최신 버전만 올리기(새 버전을 스토어에서 받을 수 있게 된 뒤에)
--     UPDATE app_version SET latest_version = '1.5.0', updated_at = now() WHERE platform = 'ANDROID';
--
--   최소 지원 버전 올리기(이보다 낮은 앱은 강제 업데이트. 구버전에 실제 문제가 있을 때만)
--     UPDATE app_version SET min_supported_version = '1.4.0', updated_at = now() WHERE platform = 'ANDROID';
--
--   최소 지원 버전 해제(강제 없이 권장만)
--     UPDATE app_version SET min_supported_version = NULL, updated_at = now() WHERE platform = 'ANDROID';
--
--   플랫폼 기능 끄기(항상 NONE)
--     UPDATE app_version SET latest_version = NULL, updated_at = now() WHERE platform = 'IOS';
--
--   참고: min_supported_version이 latest_version보다 크면 DB는 받지만 서버가 강제를 무시하고 권장만 판정하며 경고 로그를 남긴다.

BEGIN;

CREATE TABLE IF NOT EXISTS app_version (
    platform              VARCHAR(20)  NOT NULL,
    latest_version        VARCHAR(20),
    min_supported_version VARCHAR(20),
    store_url             VARCHAR(500),
    updated_at            TIMESTAMP(6) NOT NULL,
    CONSTRAINT app_version_pkey PRIMARY KEY (platform),
    CONSTRAINT app_version_platform_check CHECK (platform IN ('IOS', 'ANDROID')),
    CONSTRAINT ck_app_version_latest_format
        CHECK (latest_version IS NULL OR latest_version ~ '^[0-9]{1,6}(\.[0-9]{1,6}){0,2}$'),
    CONSTRAINT ck_app_version_min_format
        CHECK (min_supported_version IS NULL OR min_supported_version ~ '^[0-9]{1,6}(\.[0-9]{1,6}){0,2}$'),
    CONSTRAINT ck_app_version_store_url
        CHECK (store_url IS NULL OR store_url ~ '^https?://')
);

INSERT INTO app_version (platform, latest_version, min_supported_version, store_url, updated_at)
VALUES ('IOS', NULL, NULL, NULL, now()),
       ('ANDROID', NULL, NULL, NULL, now())
ON CONFLICT (platform) DO NOTHING;

COMMIT;
