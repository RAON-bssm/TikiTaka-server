-- ============================================================================
--  post 에 AI 심사 상태와 대기열 컬럼 추가
--  tikitaka / PostgreSQL 16
--
--  게시물 AI 심사를 비동기로 돌리기 위해 post 행 자체를 대기열로 쓴다.
--    status            : PENDING / DONE / REJECTED / FAILED / EXPIRED. 기존 글은 모두 DONE
--    review_attempts   : 실제 AI 호출이 실패한 횟수
--    review_next_at    : 재시도 백오프. 이 시각 전에는 다시 선점하지 않는다
--    review_started_at : 워커가 선점한 시각. 오래되면 다시 선점한다
--
--  ddl-auto=update 는 NOT NULL 컬럼을 기본값 없이 추가하려다 기존 행 때문에 실패하므로,
--  새 서버를 띄우기 전에 한 번 실행한다. 컬럼이 이미 있으면 update 가 건드리지 않는다.
--  Hibernate 가 @Enumerated(EnumType.STRING) 컬럼에 거는 CHECK 제약도 여기서 같이 건다
--  (migrate_add_apple_provider.sql 참고). 여러 번 실행해도 결과가 같다.
-- ============================================================================

BEGIN;

-- STEP 1. 컬럼 추가. 기본값 덕분에 기존 행은 DONE, 0 으로 채워진다
ALTER TABLE post ADD COLUMN IF NOT EXISTS status varchar(20) NOT NULL DEFAULT 'DONE';
ALTER TABLE post ADD COLUMN IF NOT EXISTS review_attempts integer NOT NULL DEFAULT 0;
ALTER TABLE post ADD COLUMN IF NOT EXISTS review_next_at timestamp(6);
ALTER TABLE post ADD COLUMN IF NOT EXISTS review_started_at timestamp(6);


-- STEP 2. status CHECK 제약. 이름을 고정해 다시 실행해도 한 개만 남게 한다
ALTER TABLE post DROP CONSTRAINT IF EXISTS post_status_check;
ALTER TABLE post ADD CONSTRAINT post_status_check
    CHECK (status IN ('PENDING', 'DONE', 'REJECTED', 'FAILED', 'EXPIRED'));


-- STEP 3. 대기열 선점용 인덱스. 이름은 Post 엔티티의 @Index 와 같다
CREATE INDEX IF NOT EXISTS idx_post_status_review_next_at ON post (status, review_next_at);

COMMIT;


-- STEP 4. 확인. 기존 글이 모두 DONE 인지 본다
SELECT status, count(*) FROM post GROUP BY status;

SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_name = 'post'
  AND column_name IN ('status', 'review_attempts', 'review_next_at', 'review_started_at')
ORDER BY column_name;
