-- 임대 단지의 보증금/월임대료. 분양과 임대를 같은 테이블에 두되 nullable.
ALTER TABLE listing_units
    ADD COLUMN deposit_amount BIGINT,
    ADD COLUMN monthly_rent INT;
