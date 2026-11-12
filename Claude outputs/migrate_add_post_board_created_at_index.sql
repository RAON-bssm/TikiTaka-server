-- ============================================================================
--  post 에 (board_id, created_at, post_id) 인덱스 추가
--  tikitaka / PostgreSQL 16
--
--  게시판 글 목록(GET /api/post/{boardId})이 board_id 로 거르고 created_at desc, post_id desc 로 읽는다.
--  커서 페이징(size, cursor)은 (created_at, post_id) 기준 keyset 이라 같은 순서를 쓴다. 그 조회용 인덱스다.
--  B-tree 는 뒤에서부터 읽을 수 있어 오름차순 인덱스 하나로 내림차순 정렬을 처리한다(역방향 인덱스 스캔).
--  이름과 컬럼은 Post 엔티티의 @Index(idx_post_board_created_at) 와 같다. ddl-auto=update 도 서버 기동 때
--  같은 인덱스를 만들지만, 기동 중에 큰 테이블에 인덱스를 거는 일을 피하려고 새 서버를 띄우기 전에 미리 실행한다.
--  이 SQL 로 먼저 만들어 두면 서버 기동 때는 같은 이름이 있어 다시 만들지 않는다.
--  사용자가 적은 시간에 실행한다. 여러 번 실행해도 결과가 같다.
--
--  부분 인덱스(where is_active = true and status = 'DONE')를 쓰지 않은 이유
--    목록 조건과 맞아 인덱스가 조금 작아지지만(합성 데이터 20만 행 기준 약 19%) 실행 시간 차이는 거의 없었다.
--    Hibernate @Index 로는 where 절을 선언할 수 없어 엔티티와 이 SQL 의 정의가 달라지므로 일반 인덱스로 둔다.
--
--  CONCURRENTLY 를 쓰지 않은 이유
--    일반 CREATE INDEX 는 만드는 동안 post 쓰기(insert/update)를 잠깐 기다리게 한다. 읽기는 막지 않는다.
--    post 는 사용자가 직접 올린 글만 쌓이는 테이블이라 행 수가 많지 않고(업로드 한도로 사용자당
--    10분에 2건), 합성 데이터 20만 행에서도 인덱스 생성이 1초 안에 끝났다.
--    CONCURRENTLY 는 트랜잭션 안에서 실행할 수 없고, 도중에 실패하면 INVALID 인덱스가 같은 이름으로
--    남아 IF NOT EXISTS 가 다시 만들지 않는다(멱등이 깨진다). 그래서 기본은 일반 생성으로 둔다.
--    먼저 SELECT count(*) FROM post; 로 행 수를 보고, 수십만 행 이상이면 STEP 1 대신 아래를
--    트랜잭션 밖에서 한 줄씩 실행한다.
--      SELECT indisvalid FROM pg_index WHERE indexrelid = 'idx_post_board_created_at'::regclass;
--        -- false 로 나오면 실패한 흔적이므로 먼저 지운다(결과가 없으면 아직 없는 것이다)
--      DROP INDEX CONCURRENTLY IF EXISTS idx_post_board_created_at;
--      CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_post_board_created_at ON post (board_id, created_at, post_id);
--
--  되돌리기
--    DROP INDEX IF EXISTS idx_post_board_created_at;
--    인덱스만 지우므로 데이터는 바뀌지 않는다. 엔티티에 @Index 가 남아 있으면 다음 서버 기동 때 다시 만들어진다.
-- ============================================================================

BEGIN;

-- STEP 1. 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_post_board_created_at ON post (board_id, created_at, post_id);

COMMIT;


-- STEP 2. 확인. 정의와 유효 여부(indisvalid = true)를 본다
SELECT i.indexrelid::regclass AS index_name, i.indisvalid, pg_get_indexdef(i.indexrelid) AS definition
FROM pg_index i
WHERE i.indrelid = 'post'::regclass
  AND i.indexrelid = 'idx_post_board_created_at'::regclass;
