-- DSR 계산용: 기존 채무의 월 상환액 (AES 암호화).
ALTER TABLE assets
    ADD COLUMN monthly_debt_amount_enc BYTEA;
