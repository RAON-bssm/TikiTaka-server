-- ============================================================================
--  LoginProvider 에 APPLE 추가
--  tikitaka / PostgreSQL 16
--
--  Hibernate 는 @Enumerated(EnumType.STRING) 컬럼을 만들 때
--    provider varchar(255) check (provider in ('KAKAO','GOOGLE'))
--  처럼 CHECK 제약을 같이 건다(이름은 PostgreSQL 이 users_provider_check 처럼 자동으로 붙인다).
--  ddl-auto=update 는 이미 있는 제약을 고치지 않으므로, 그대로 두면 APPLE 유저 가입(users)과
--  refresh token 저장(token) 이 제약 위반으로 실패한다. 배포 전에 한 번 실행한다.
--
--  제약 이름을 못 박지 않고 pg_constraint 에서 provider 컬럼의 CHECK 제약을 찾아 드롭한 뒤,
--  APPLE 을 포함한 제약을 다시 건다. 여러 번 실행해도 결과가 같다.
-- ============================================================================

BEGIN;

-- STEP 0. 현재 제약 확인
SELECT cl.relname AS table_name, con.conname, pg_get_constraintdef(con.oid) AS definition
FROM pg_constraint con
         JOIN pg_class cl ON cl.oid = con.conrelid
WHERE con.contype = 'c'
  AND cl.relname IN ('users', 'token')
  AND pg_get_constraintdef(con.oid) LIKE '%provider%';


-- STEP 1. provider 컬럼의 기존 CHECK 제약 드롭
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT cl.relname AS table_name, con.conname AS constraint_name
        FROM pg_constraint con
                 JOIN pg_class cl ON cl.oid = con.conrelid
                 JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = ANY (con.conkey)
        WHERE con.contype = 'c'
          AND cl.relname IN ('users', 'token')
          AND att.attname = 'provider'
        LOOP
            EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', r.table_name, r.constraint_name);
            RAISE NOTICE 'dropped CHECK %  (on table %)', r.constraint_name, r.table_name;
        END LOOP;
END $$;


-- STEP 2. APPLE 을 포함해 다시 생성 (Hibernate 가 새로 만들 때와 같은 이름)
ALTER TABLE users ADD CONSTRAINT users_provider_check
    CHECK (provider IN ('KAKAO', 'GOOGLE', 'APPLE'));

ALTER TABLE token ADD CONSTRAINT token_provider_check
    CHECK (provider IN ('KAKAO', 'GOOGLE', 'APPLE'));


-- STEP 3. 검증 — 두 테이블 모두 APPLE 이 들어간 제약이 보여야 한다
SELECT cl.relname AS table_name, con.conname, pg_get_constraintdef(con.oid) AS definition
FROM pg_constraint con
         JOIN pg_class cl ON cl.oid = con.conrelid
WHERE con.contype = 'c'
  AND cl.relname IN ('users', 'token')
  AND pg_get_constraintdef(con.oid) LIKE '%provider%';

COMMIT;
