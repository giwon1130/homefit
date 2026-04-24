-- 사용자 프로필 코어
CREATE TABLE user_profiles (
    user_id                        BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    birth_date                     DATE,
    marriage_date                  DATE,
    is_householder                 BOOLEAN,
    is_first_time_buyer            BOOLEAN,           -- 생애최초 여부
    no_home_since                  DATE,              -- 무주택 기간 기산일
    subscription_account_opened_at DATE,              -- 청약통장 가입일
    subscription_deposit_months    INT,               -- 납입횟수
    subscription_deposit_total     BIGINT,            -- 납입총액 (원)
    updated_at                     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 가족 구성 (세대주 외 구성원)
CREATE TABLE household_members (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    relation   TEXT NOT NULL,        -- 'SPOUSE' | 'CHILD' | 'PARENT' | 'GRANDPARENT' | 'OTHER'
    birth_date DATE
);
CREATE INDEX idx_household_members_user ON household_members(user_id);

-- 연 단위 소득 (민감, AES-GCM 암호화된 bytea)
CREATE TABLE incomes (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    year              INT NOT NULL,
    self_amount_enc   BYTEA,
    spouse_amount_enc BYTEA,
    UNIQUE (user_id, year)
);

-- 자산 (민감)
CREATE TABLE assets (
    user_id         BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    net_worth_enc   BYTEA,
    real_estate_enc BYTEA,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 거주지
CREATE TABLE residences (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    address     TEXT NOT NULL,
    sido        TEXT,
    sigungu     TEXT,
    dong_code   TEXT,
    latitude    NUMERIC(9,6),
    longitude   NUMERIC(9,6),
    lived_since DATE,
    is_current  BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_residences_user ON residences(user_id);

-- 직장 (본인 + 배우자, 최대 2개)
CREATE TABLE workplaces (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    owner       TEXT NOT NULL,        -- 'SELF' | 'SPOUSE'
    label       TEXT,
    address     TEXT NOT NULL,
    latitude    NUMERIC(9,6),
    longitude   NUMERIC(9,6),
    arrival_time TIME DEFAULT '09:00'
);
CREATE INDEX idx_workplaces_user ON workplaces(user_id);

-- 선호 (매칭 스코어 입력값)
CREATE TABLE preferences (
    user_id              BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    max_purchase_price   BIGINT,
    max_jeonse_price     BIGINT,
    max_monthly_rent     INT,
    max_deposit_for_rent BIGINT,
    min_size_m2          NUMERIC(5,2),
    max_size_m2          NUMERIC(5,2),
    min_rooms            INT,
    max_commute_minutes  INT,
    preferred_sidos      TEXT[],
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 주택 소유 이력
CREATE TABLE housing_history (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    owned_from DATE NOT NULL,
    owned_to   DATE,
    note       TEXT
);
CREATE INDEX idx_housing_history_user ON housing_history(user_id);
