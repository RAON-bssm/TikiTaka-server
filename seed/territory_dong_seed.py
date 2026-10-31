#!/usr/bin/env python3
"""
busan_dong_list.csv -> seed/territory_dong.sql

사용법: python3 seed/territory_dong_seed.py <csv 경로> [source_version]
  - csv 필수 컬럼: adm_cd2(10자리 행정동 코드), gu(구 이름 = location.location_name), dong(동 이름)
  - 생성된 SQL은 psql로 실행한다. location과 매칭되지 않는 구가 있으면 아무것도 넣지 않고 중단한다.
  - dong_code 기준 upsert라서 여러 번 실행해도 결과가 같다.
"""
import csv
import sys
from pathlib import Path

CITY = "부산광역시"
EXPECTED = 206


def q(s: str) -> str:
    return "'" + s.replace("'", "''") + "'"


def main():
    if len(sys.argv) < 2:
        sys.exit("usage: territory_dong_seed.py <csv> [source_version]")
    src = Path(sys.argv[1])
    version = sys.argv[2] if len(sys.argv) > 2 else "ver20261001"

    with src.open(encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        missing = {"adm_cd2", "gu", "dong"} - set(reader.fieldnames or [])
        if missing:
            sys.exit(f"csv에 컬럼이 없습니다: {sorted(missing)} (있는 컬럼: {reader.fieldnames})")
        rows = [(r["adm_cd2"].strip(), r["gu"].strip(), r["dong"].strip()) for r in reader]

    bad = [r for r in rows if not (r[0].isdigit() and len(r[0]) == 10)]
    if bad:
        sys.exit(f"10자리 숫자가 아닌 adm_cd2: {bad[:5]}")
    codes = [r[0] for r in rows]
    if len(codes) != len(set(codes)):
        sys.exit("adm_cd2가 중복됩니다")
    if len(rows) != EXPECTED:
        print(f"경고: 동 수가 {len(rows)}개입니다 (예상 {EXPECTED})", file=sys.stderr)

    values = ",\n".join(f"  ({c}, {q(g)}, {q(d)})" for c, g, d in sorted(rows))
    sql = f"""-- 자동 생성: seed/territory_dong_seed.py ({src.name}, {version}). 직접 고치지 말 것
BEGIN;

CREATE TABLE IF NOT EXISTS dong (
    dong_id        BIGSERIAL PRIMARY KEY,
    dong_code      BIGINT       NOT NULL UNIQUE,
    location_id    BIGINT       NOT NULL REFERENCES location (location_id),
    dong_name      VARCHAR(255) NOT NULL,
    source_version VARCHAR(255) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_dong_location ON dong (location_id);

CREATE TEMP TABLE tmp_dong (dong_code BIGINT, gu VARCHAR, dong_name VARCHAR) ON COMMIT DROP;
INSERT INTO tmp_dong VALUES
{values};

-- location과 매칭되지 않는 구가 있으면 트랜잭션 전체를 중단한다
DO $$
DECLARE missing TEXT;
BEGIN
    SELECT string_agg(DISTINCT t.gu, ', ') INTO missing
    FROM tmp_dong t
    LEFT JOIN location l ON l.city_name = {q(CITY)} AND l.location_name = t.gu
    WHERE l.location_id IS NULL;
    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'location 매칭 실패: %', missing;
    END IF;
END $$;

INSERT INTO dong (dong_code, location_id, dong_name, source_version)
SELECT t.dong_code, l.location_id, t.dong_name, {q(version)}
FROM tmp_dong t
JOIN location l ON l.city_name = {q(CITY)} AND l.location_name = t.gu
ON CONFLICT (dong_code) DO UPDATE
    SET location_id = EXCLUDED.location_id,
        dong_name = EXCLUDED.dong_name,
        source_version = EXCLUDED.source_version;

SELECT count(*) AS dong_count FROM dong;
COMMIT;
"""
    out = Path(__file__).with_name("territory_dong.sql")
    out.write_text(sql, encoding="utf-8")
    print(f"{out} 생성: {len(rows)}개 동")


if __name__ == "__main__":
    main()
