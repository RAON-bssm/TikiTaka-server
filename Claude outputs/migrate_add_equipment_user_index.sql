-- ============================================================================
--  equipment 에 (user_id) 인덱스 추가
--  tikitaka / PostgreSQL 16
--
--  착용 정보 조회가 모두 equipment.user_id 로 거른다.
--    GET /api/equipment/{userId}          유저 한 명(구버전 앱은 글 카드, 랭킹 행마다 호출)
--    GET /api/post/{boardId}, /api/users/rank   작성자, 랭킹 유저 착용 정보 일괄 조회(user_id in (...))
--    착용 교체                              user_id 로 기존 착용을 지운다
--  user_id 는 FK 지만 PostgreSQL 은 FK 에 인덱스를 자동으로 만들지 않아, 지금은 조회마다 테이블 전체를 읽는다.
--  이름과 컬럼은 Equipment 엔티티의 @Index(idx_equipment_user) 와 같다. ddl-auto=update 도 서버 기동 때
--  같은 인덱스를 만들지만, 기동 중에 인덱스를 거는 일을 피하려고 새 서버를 띄우기 전에 미리 실행한다.
--  이 SQL 로 먼저 만들어 두면 서버 기동 때는 같은 이름이 있어 다시 만들지 않는다.
--  사용자가 적은 시간에 실행한다. 여러 번 실행해도 결과가 같다.
--
--  합성 데이터(유저 1만 명, 착용 4만 행) 기준
--    단건 조회: 순차 스캔 4만 행(버퍼 315, 1.14ms) -> 인덱스 스캔 4행(버퍼 7, 0.11ms)
--    101명 일괄 조회: 순차 스캔(2.2ms) -> 인덱스 스캔(0.63ms)
--    인덱스 크기 약 584kB. 생성 약 35ms
--    합성 데이터라 운영과 행 수, 분포가 다르다. 순차 스캔 비용은 착용 행 수(유저 수)에 비례해 커진다.
--
--  CONCURRENTLY 를 쓰지 않은 이유
--    일반 CREATE INDEX 는 만드는 동안 equipment 쓰기(착용 변경)를 잠깐 기다리게 한다. 읽기는 막지 않는다.
--    equipment 는 유저당 최대 상품 타입 수만큼만 행이 있어(한 사람 10행 미만) 작고, 4만 행에서 35ms 걸렸다.
--    CONCURRENTLY 는 트랜잭션 안에서 실행할 수 없고, 도중에 실패하면 INVALID 인덱스가 같은 이름으로
--    남아 IF NOT EXISTS 가 다시 만들지 않는다(멱등이 깨진다). 그래서 기본은 일반 생성으로 둔다.
--    먼저 SELECT count(*) FROM equipment; 로 행 수를 보고, 수백만 행 이상이면 STEP 1 대신 아래를
--    트랜잭션 밖에서 한 줄씩 실행한다.
--      SELECT indisvalid FROM pg_index WHERE indexrelid = 'idx_equipment_user'::regclass;
--        -- false 로 나오면 실패한 흔적이므로 먼저 지운다(결과가 없으면 아직 없는 것이다)
--      DROP INDEX CONCURRENTLY IF EXISTS idx_equipment_user;
--      CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_equipment_user ON equipment (user_id);
--
--  되돌리기
--    DROP INDEX IF EXISTS idx_equipment_user;
--    인덱스만 지우므로 데이터는 바뀌지 않는다. 엔티티에 @Index 가 남아 있으면 다음 서버 기동 때 다시 만들어진다.
-- ============================================================================

BEGIN;

-- STEP 1. 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_equipment_user ON equipment (user_id);

COMMIT;


-- STEP 2. 확인. 정의와 유효 여부(indisvalid = true)를 본다
SELECT i.indexrelid::regclass AS index_name, i.indisvalid, pg_get_indexdef(i.indexrelid) AS definition
FROM pg_index i
WHERE i.indrelid = 'equipment'::regclass
  AND i.indexrelid = 'idx_equipment_user'::regclass;
