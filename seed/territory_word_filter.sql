-- 땅따먹기 미션 금칙어·예외 단어 시드. 여러 번 실행해도 결과가 같다.
-- 금칙어는 원형 하나만 넣는다. 서버가 발음 뼈대(받침 제거, 쌍자음→평음, 비슷한 모음 통합)로 바꿔
-- 변형(씨발, 싯발, 씨바 등)까지 막는다. 한 글자 금칙어는 오탐을 막으려고 그 글자 그대로만 비교한다.
-- 정상 단어가 걸리면 allowed_word에 추가한다.
BEGIN;

CREATE TABLE IF NOT EXISTS banned_word (
    banned_word_id BIGSERIAL PRIMARY KEY,
    word           VARCHAR(30) NOT NULL UNIQUE
);
CREATE TABLE IF NOT EXISTS allowed_word (
    allowed_word_id BIGSERIAL PRIMARY KEY,
    word            VARCHAR(30) NOT NULL UNIQUE
);

INSERT INTO banned_word (word) VALUES
  ('시발'), ('병신'), ('개새끼'), ('지랄'), ('존나'), ('미친놈'), ('미친년'),
  ('엠창'), ('니미'), ('느금마'), ('김치녀'), ('틀딱'), ('좆'), ('씹')
ON CONFLICT (word) DO NOTHING;

INSERT INTO allowed_word (word) VALUES
  ('시발점'), ('시바견'), ('시바이누')
ON CONFLICT (word) DO NOTHING;

COMMIT;
