-- ============================================================================
--  post 에 post_thumbnail 컬럼 추가
--  tikitaka / PostgreSQL 16
--
--  글 목록용 썸네일의 S3 key(thumbnails/UUID.jpg)를 담는다. 업로드 때 서버가 원본과 함께 만든다.
--  이름과 타입은 Post 엔티티의 @Column(name = "post_thumbnail") 와 같다(Hibernate 기본 varchar(255)).
--  ddl-auto=update 도 서버 기동 때 같은 컬럼을 만들지만, 운영 DB 변경을 기동에 맡기지 않으려고
--  새 서버를 띄우기 전에 미리 실행한다. 이미 있으면 다시 만들지 않으므로 여러 번 실행해도 결과가 같다.
--
--  기존 글은 NULL 로 남고, 목록은 썸네일이 없으면 원본(post_image)으로 대신한다. 따로 채우지 않는다.
--  NULL 허용, 기본값 없는 컬럼 추가는 테이블을 다시 쓰지 않아 행 수와 상관없이 바로 끝난다.
--  잠깐 걸리는 잠금(ACCESS EXCLUSIVE)도 곧바로 풀리지만, 오래 걸리는 쿼리가 post 를 잡고 있으면 그 뒤에서 기다리므로
--  사용자가 적은 시간에 실행한다.
--
--  되돌리기
--    ALTER TABLE post DROP COLUMN IF EXISTS post_thumbnail;
--    S3 의 thumbnails/ 파일은 지워지지 않는다. 엔티티에 필드가 남아 있으면 다음 서버 기동 때 다시 만들어진다.
-- ============================================================================

BEGIN;

-- STEP 1. 컬럼 추가
ALTER TABLE post ADD COLUMN IF NOT EXISTS post_thumbnail varchar(255);

COMMIT;


-- STEP 2. 확인. data_type = character varying, character_maximum_length = 255, is_nullable = YES 여야 한다
SELECT column_name, data_type, character_maximum_length, is_nullable
FROM information_schema.columns
WHERE table_name = 'post'
  AND column_name = 'post_thumbnail';
