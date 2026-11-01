-- ============================================================================
--  post 에 (user_id, created_at) 인덱스 추가
--  tikitaka / PostgreSQL 16
--
--  사용자별 업로드 한도가 글을 올릴 때마다 "이 사용자가 최근 window 안에 만든 글"을
--  user_id 로 거르고 created_at 최근 순으로 읽는다. 그 조회용 인덱스다.
--  이름은 Post 엔티티의 @Index(idx_post_user_created_at) 와 같다. ddl-auto=update 도 서버 기동 때
--  같은 인덱스를 만들지만, 기동 중에 큰 테이블에 인덱스를 거는 일을 피하려고 배포 전에 미리 실행한다.
--  여러 번 실행해도 결과가 같다.
--
--  CONCURRENTLY 를 쓰지 않은 이유
--    일반 CREATE INDEX 는 만드는 동안 post 쓰기(insert/update)를 막는다. 읽기는 막지 않는다.
--    post 는 사용자가 직접 올린 글만 쌓이는 테이블이라 행 수가 많지 않고(업로드 한도로 사용자당
--    10분에 2건), 이 규모에서는 인덱스 생성이 1초 안에 끝나 쓰기가 잠깐 기다리는 정도다.
--    CONCURRENTLY 는 트랜잭션 안에서 실행할 수 없고, 도중에 실패하면 INVALID 인덱스가 같은 이름으로
--    남아 IF NOT EXISTS 가 다시 만들지 않는다(멱등이 깨진다). 그래서 기본은 일반 생성으로 둔다.
--    운영 데이터가 수십만 행 이상으로 커졌다면 STEP 1 대신 아래를 트랜잭션 밖에서 한 줄씩 실행한다.
--      SELECT indisvalid FROM pg_index WHERE indexrelid = 'idx_post_user_created_at'::regclass;
--        -- false 로 나오면 실패한 흔적이므로 먼저 지운다
--      DROP INDEX CONCURRENTLY IF EXISTS idx_post_user_created_at;
--      CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_post_user_created_at ON post (user_id, created_at);
-- ============================================================================

BEGIN;

-- STEP 1. 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_post_user_created_at ON post (user_id, created_at);

COMMIT;


-- STEP 2. 확인. 정의와 유효 여부(indisvalid = true)를 본다
SELECT i.indexrelid::regclass AS index_name, i.indisvalid, pg_get_indexdef(i.indexrelid) AS definition
FROM pg_index i
WHERE i.indrelid = 'post'::regclass
  AND i.indexrelid = 'idx_post_user_created_at'::regclass;
