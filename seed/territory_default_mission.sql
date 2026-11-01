-- 땅따먹기 기본 미션. 땅따먹기 주간 시작 때 미션이 없는 땅(주인 없는 땅, 세팅 안 한 땅)에 랜덤으로 들어간다.
-- 어느 동네에서나 찍을 수 있는 주제만 넣는다. 여러 번 실행해도 결과가 같다.
BEGIN;

CREATE TABLE IF NOT EXISTS default_mission (
    default_mission_id BIGSERIAL PRIMARY KEY,
    adjective          VARCHAR(10) NOT NULL,
    noun               VARCHAR(10) NOT NULL,
    scoring_criteria   VARCHAR(50),
    CONSTRAINT uk_default_mission UNIQUE (adjective, noun)
);

INSERT INTO default_mission (adjective, noun, scoring_criteria) VALUES
  ('파란', '하늘', '하늘이 화면을 넓게 채울수록 고득점'),
  ('빨간', '간판', '빨간색이 선명할수록 고득점'),
  ('귀여운', '고양이', NULL),
  ('푸른', '나무', '잎이 풍성하게 보일수록 고득점'),
  ('노란', '꽃', NULL),
  ('맛있는', '음식', '음식이 먹음직스럽게 보일수록 고득점'),
  ('오래된', '건물', NULL),
  ('반짝이는', '불빛', '야경이나 조명이 잘 보일수록 고득점'),
  ('동그란', '물건', NULL),
  ('하얀', '구름', NULL),
  ('작은', '화분', NULL),
  ('알록달록한', '벽화', '색이 다양할수록 고득점'),
  ('조용한', '골목', NULL),
  ('따뜻한', '커피', NULL),
  ('신나는', '놀이터', NULL),
  ('긴', '그림자', '그림자가 길고 뚜렷할수록 고득점'),
  ('붉은', '노을', NULL),
  ('네모난', '창문', NULL),
  ('씩씩한', '강아지', NULL),
  ('시원한', '물', NULL)
ON CONFLICT (adjective, noun) DO NOTHING;

COMMIT;
