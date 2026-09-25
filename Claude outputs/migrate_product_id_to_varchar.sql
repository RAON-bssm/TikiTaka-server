-- ============================================================================
--  product.product_id : bigint(auto increment) -> varchar(100)
--  tikitaka / PostgreSQL 16
--
--  실행 전 필수
--    1) 애플리케이션을 반드시 중지한다 (ddl-auto=update 가 중간에 끼어들면 꼬인다)
--    2) 백업:  pg_dump -U <user> -d <db> -F c -f backup_before_product_id.dump
--    3) STEP 4 의 매핑 테이블을 실제 상품에 맞게 채운 뒤 실행한다
--
--  전체가 하나의 트랜잭션이다. 중간에 실패하면 ROLLBACK 되고 원상복구된다.
-- ============================================================================

BEGIN;


-- ----------------------------------------------------------------------------
-- STEP 0. 현재 상태 확인 (실행 전 눈으로 확인용)
-- ----------------------------------------------------------------------------
SELECT table_name, column_name, data_type, character_maximum_length, is_nullable
FROM information_schema.columns
WHERE column_name = 'product_id'
  AND table_name IN ('product', 'inventory', 'equipment')
ORDER BY table_name;

SELECT count(*) AS product_count FROM product;
SELECT count(*) AS inventory_count FROM inventory;
SELECT count(*) AS equipment_count FROM equipment;


-- ----------------------------------------------------------------------------
-- STEP 1. product 를 참조하는 FK 전부 드롭
--   Hibernate 가 fk3s7v... 같은 랜덤 이름으로 만들어서 이름을 못 박는다.
--   pg_constraint 를 뒤져서 동적으로 찾아 드롭한다.
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT con.conname AS constraint_name,
               cl.relname  AS table_name
        FROM pg_constraint con
                 JOIN pg_class cl  ON cl.oid  = con.conrelid
                 JOIN pg_class ref ON ref.oid = con.confrelid
        WHERE con.contype = 'f'
          AND ref.relname = 'product'
        LOOP
            EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', r.table_name, r.constraint_name);
            RAISE NOTICE 'dropped FK %  (on table %)', r.constraint_name, r.table_name;
        END LOOP;
END $$;


-- ----------------------------------------------------------------------------
-- STEP 2. 자동 증가 속성 제거
--   Hibernate 버전에 따라 identity 컬럼이거나 serial(+시퀀스)이다. 둘 다 처리한다.
-- ----------------------------------------------------------------------------
ALTER TABLE product ALTER COLUMN product_id DROP IDENTITY IF EXISTS;
ALTER TABLE product ALTER COLUMN product_id DROP DEFAULT;
DROP SEQUENCE IF EXISTS product_product_id_seq;


-- ----------------------------------------------------------------------------
-- STEP 3. 컬럼 타입 변경 (bigint -> varchar(100))
--   기존 숫자는 문자열로 그대로 옮겨진다: 1 -> '1'
--   PK/유니크 인덱스(uk_inventory_user_product)는 PostgreSQL 이 자동으로 재생성한다.
-- ----------------------------------------------------------------------------
ALTER TABLE product   ALTER COLUMN product_id TYPE varchar(100) USING product_id::varchar;
ALTER TABLE inventory ALTER COLUMN product_id TYPE varchar(100) USING product_id::varchar;
ALTER TABLE equipment ALTER COLUMN product_id TYPE varchar(100) USING product_id::varchar;


-- ----------------------------------------------------------------------------
-- STEP 4. 숫자 ID -> 실제 상품 코드 치환
--
--   ★ 여기를 실제 상품에 맞게 채워야 한다 ★
--   아래 쿼리로 현재 상품 목록을 먼저 뽑아보고 매핑을 작성할 것:
--
--       SELECT product_id, product_name, product_type FROM product ORDER BY product_id::int;
--
--   상품 데이터가 아예 없다면 STEP 4 전체를 건너뛰어도 된다.
-- ----------------------------------------------------------------------------
CREATE TEMP TABLE product_id_map (
    old_id varchar(100) PRIMARY KEY,
    new_id varchar(100) NOT NULL UNIQUE
) ON COMMIT DROP;

INSERT INTO product_id_map (old_id, new_id) VALUES
    ('1',  'basic-body-01'),
    ('2',  'red-glasses-01'),
    ('3',  'blue-hoodie-01')
    -- ... 실제 상품 수만큼 이어서 작성
;

-- 4-1. 매핑 누락 검사 — 하나라도 빠지면 여기서 멈춘다
DO $$
DECLARE
    missing text;
BEGIN
    SELECT string_agg(p.product_id || '(' || coalesce(p.product_name, '?') || ')', ', ')
    INTO missing
    FROM product p
             LEFT JOIN product_id_map m ON m.old_id = p.product_id
    WHERE m.old_id IS NULL;

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION '매핑이 빠진 상품이 있습니다: %', missing;
    END IF;
END $$;

-- 4-2. 새 코드가 형식에 맞는지 검사 (CHECK 제약을 붙이기 전에 미리 걸러낸다)
DO $$
DECLARE
    invalid text;
BEGIN
    SELECT string_agg(new_id, ', ') INTO invalid
    FROM product_id_map
    WHERE new_id !~ '^[a-z0-9]+(-[a-z0-9]+)*$';

    IF invalid IS NOT NULL THEN
        RAISE EXCEPTION '케밥케이스 형식에 맞지 않는 코드가 있습니다: %', invalid;
    END IF;
END $$;

-- 4-3. 치환 (FK 가 드롭된 상태라 순서는 상관없다)
UPDATE inventory i SET product_id = m.new_id
FROM product_id_map m WHERE i.product_id = m.old_id;

UPDATE equipment e SET product_id = m.new_id
FROM product_id_map m WHERE e.product_id = m.old_id;

UPDATE product p SET product_id = m.new_id
FROM product_id_map m WHERE p.product_id = m.old_id;


-- ----------------------------------------------------------------------------
-- STEP 5. 제약 재생성
-- ----------------------------------------------------------------------------
ALTER TABLE product   ALTER COLUMN product_id SET NOT NULL;
ALTER TABLE inventory ALTER COLUMN product_id SET NOT NULL;
ALTER TABLE equipment ALTER COLUMN product_id SET NOT NULL;

-- 엔티티의 @Check(name = "ck_product_id_format", ...) 와 동일한 제약
ALTER TABLE product ADD CONSTRAINT ck_product_id_format
    CHECK (product_id ~ '^[a-z0-9]+(-[a-z0-9]+)*$');

ALTER TABLE inventory ADD CONSTRAINT fk_inventory_product
    FOREIGN KEY (product_id) REFERENCES product (product_id);

ALTER TABLE equipment ADD CONSTRAINT fk_equipment_product
    FOREIGN KEY (product_id) REFERENCES product (product_id);


-- ----------------------------------------------------------------------------
-- STEP 6. 검증 — 결과를 눈으로 확인하고 COMMIT 한다
-- ----------------------------------------------------------------------------
SELECT table_name, column_name, data_type, character_maximum_length, is_nullable
FROM information_schema.columns
WHERE column_name = 'product_id'
  AND table_name IN ('product', 'inventory', 'equipment')
ORDER BY table_name;

-- 고아 레코드가 없어야 한다 (0건이어야 정상)
SELECT 'inventory' AS tbl, i.product_id
FROM inventory i LEFT JOIN product p ON p.product_id = i.product_id
WHERE p.product_id IS NULL
UNION ALL
SELECT 'equipment', e.product_id
FROM equipment e LEFT JOIN product p ON p.product_id = e.product_id
WHERE p.product_id IS NULL;

SELECT product_id, product_name, product_type, price, is_active
FROM product ORDER BY product_id;


COMMIT;
-- 문제가 있으면 COMMIT 대신 ROLLBACK;
