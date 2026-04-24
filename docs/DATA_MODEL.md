# Data Model — homefit

PostgreSQL 16 + PostGIS. Flyway 마이그레이션으로 관리 (`apps/api/src/main/resources/db/migration/`).

## 1. 도메인별 테이블

### 1.1 계정 / 인증

```sql
users (
  id                BIGSERIAL PRIMARY KEY,
  email             TEXT UNIQUE NOT NULL,
  oauth_provider    TEXT NOT NULL,          -- 'google' | 'kakao'
  oauth_subject     TEXT NOT NULL,
  display_name      TEXT,
  profile_image_url TEXT,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_login_at     TIMESTAMPTZ,
  UNIQUE (oauth_provider, oauth_subject)
);

refresh_tokens (
  id          BIGSERIAL PRIMARY KEY,
  user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash  TEXT NOT NULL,
  expires_at  TIMESTAMPTZ NOT NULL,
  revoked_at  TIMESTAMPTZ
);
```

### 1.2 프로필 (사용자 입력 정보)

```sql
user_profiles (
  user_id              BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  birth_date           DATE,
  marriage_date        DATE,
  is_householder       BOOLEAN,
  is_first_time_buyer  BOOLEAN,              -- 생애최초 여부
  no_home_since        DATE,                 -- 무주택 기간 기산일
  subscription_account_opened_at DATE,       -- 청약통장 가입일
  subscription_deposit_months    INT,        -- 납입횟수
  subscription_deposit_total     BIGINT,     -- 납입총액 (원)
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 가족 구성
household_members (
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  relation   TEXT NOT NULL,         -- 'spouse' | 'child' | 'parent'
  birth_date DATE
);

-- 소득 (연 단위, 민감정보 암호화)
incomes (
  id              BIGSERIAL PRIMARY KEY,
  user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  year            INT NOT NULL,
  self_amount_enc BYTEA,            -- AES-256
  spouse_amount_enc BYTEA,
  UNIQUE (user_id, year)
);

-- 자산
assets (
  user_id       BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  net_worth_enc BYTEA,              -- 순자산
  real_estate_enc BYTEA,            -- 부동산 평가액
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 거주지
residences (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  address       TEXT NOT NULL,
  sido          TEXT NOT NULL,
  sigungu       TEXT NOT NULL,
  dong_code     TEXT,               -- 법정동 코드
  geom          GEOMETRY(POINT, 4326),
  lived_since   DATE,
  is_current    BOOLEAN NOT NULL DEFAULT true
);

-- 직장 (본인 + 배우자)
workplaces (
  id          BIGSERIAL PRIMARY KEY,
  user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  owner       TEXT NOT NULL,        -- 'self' | 'spouse'
  label       TEXT,                 -- '회사', '본사' 등
  address     TEXT NOT NULL,
  geom        GEOMETRY(POINT, 4326),
  arrival_time TIME DEFAULT '09:00' -- 출근 도착 기준
);

-- 선호 (예산/면적)
preferences (
  user_id               BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  max_purchase_price    BIGINT,     -- 원
  max_jeonse_price      BIGINT,
  max_monthly_rent      INT,
  max_deposit_for_rent  BIGINT,
  min_size_m2           NUMERIC(5,2),
  max_size_m2           NUMERIC(5,2),
  min_rooms             INT,
  max_commute_minutes   INT,
  preferred_sidos       TEXT[]      -- ['서울특별시', '경기도']
);

-- 주택 소유 이력
housing_history (
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  owned_from DATE NOT NULL,
  owned_to   DATE,
  note       TEXT
);
```

### 1.3 청약 / 매물

```sql
listings (
  id                  BIGSERIAL PRIMARY KEY,
  source              TEXT NOT NULL,        -- 'PUBLIC_DATA' | 'LH' | 'SH' | 'MANUAL'
  source_ref          TEXT NOT NULL,        -- 원본 공고 ID
  listing_type        TEXT NOT NULL,        -- 'PRIVATE_SALE' | 'PUBLIC_SALE' | 'HAPPY_HOUSE' | 'NEWLYWED_HOPE' | 'PURCHASE_RENTAL' | 'JEONSE_RENTAL' | 'NATIONAL_RENTAL'
  name                TEXT NOT NULL,
  developer           TEXT,
  sido                TEXT NOT NULL,
  sigungu             TEXT NOT NULL,
  dong                TEXT,
  address             TEXT,
  geom                GEOMETRY(POINT, 4326),
  application_start   TIMESTAMPTZ,
  application_end     TIMESTAMPTZ,
  announcement_date   DATE,
  move_in_date        DATE,
  total_supply        INT,
  raw_document_url    TEXT,                 -- 공고 원문 PDF
  raw_json            JSONB,                -- 원본 API 응답
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (source, source_ref)
);

CREATE INDEX idx_listings_app_period ON listings(application_start, application_end);
CREATE INDEX idx_listings_geom ON listings USING GIST(geom);

-- 평형/공급 세부
listing_units (
  id             BIGSERIAL PRIMARY KEY,
  listing_id     BIGINT NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
  unit_type      TEXT,                 -- '84A', '59B' 등
  size_m2        NUMERIC(6,2),
  rooms          INT,
  supply_count   INT,
  price_min      BIGINT,               -- 원
  price_max      BIGINT,
  deposit_amount BIGINT,               -- 임대용
  monthly_rent   INT                    -- 임대용
);

-- 공급 유형별 규칙 (생애최초/신혼부부/다자녀/일반 등)
listing_eligibility_rules (
  id           BIGSERIAL PRIMARY KEY,
  listing_id   BIGINT NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
  supply_type  TEXT NOT NULL,          -- 'FIRST_TIME' | 'NEWLYWED' | 'MULTI_CHILD' | 'GENERAL'
  supply_count INT,
  income_limit_pct INT,                -- 도시근로자 월평균소득 대비 %
  asset_limit  BIGINT,
  require_householder BOOLEAN,
  require_no_home     BOOLEAN,
  region_residence_months INT,         -- 해당지역 의무거주 개월
  rule_json    JSONB NOT NULL          -- 기타 복잡 규칙
);

listing_media (
  id         BIGSERIAL PRIMARY KEY,
  listing_id BIGINT NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
  kind       TEXT NOT NULL,            -- 'FLOORPLAN' | 'RENDERING' | 'LOCATION_PHOTO' | 'DOC'
  url        TEXT NOT NULL,
  caption    TEXT,
  sort_order INT DEFAULT 0
);
```

### 1.4 매칭 (precomputed)

```sql
matches (
  user_id                BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  listing_id             BIGINT NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
  eligible_supply_types  TEXT[] NOT NULL,
  best_supply_type       TEXT,
  expected_competition   TEXT,        -- 'LOW' | 'MID' | 'HIGH'
  commute_self_min       INT,
  commute_spouse_min     INT,
  budget_fit_score       INT,         -- 0~100
  total_score            INT,         -- 0~100
  reasoning              TEXT,
  computed_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, listing_id)
);

CREATE INDEX idx_matches_user_score ON matches(user_id, total_score DESC);
```

### 1.5 실거래가 (대안 추천용)

```sql
real_transactions (
  id              BIGSERIAL PRIMARY KEY,
  deal_type       TEXT NOT NULL,      -- 'SALE' | 'JEONSE' | 'MONTHLY'
  sigungu_code    TEXT NOT NULL,      -- 5자리
  dong_code       TEXT,
  apartment_name  TEXT,
  geom            GEOMETRY(POINT, 4326),
  size_m2         NUMERIC(6,2),
  floor           INT,
  built_year      INT,
  deal_date       DATE NOT NULL,
  price           BIGINT NOT NULL,    -- 매매가 or 보증금
  monthly_rent    INT,
  source          TEXT NOT NULL DEFAULT 'MOLIT'
);

CREATE INDEX idx_rt_sigungu_date ON real_transactions(sigungu_code, deal_date DESC);
CREATE INDEX idx_rt_geom ON real_transactions USING GIST(geom);
```

### 1.6 통근 경로 캐시

```sql
commute_cache (
  origin_geohash  TEXT NOT NULL,
  dest_geohash    TEXT NOT NULL,
  mode            TEXT NOT NULL,      -- 'TRANSIT' | 'CAR'
  arrive_by       TIME,
  total_minutes   INT NOT NULL,
  walk_minutes    INT,
  transfers       INT,
  route_summary   JSONB,
  cached_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (origin_geohash, dest_geohash, mode, arrive_by)
);
```

### 1.7 알림 / 즐겨찾기

```sql
favorites (
  user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  listing_id BIGINT NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, listing_id)
);

notifications (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  listing_id    BIGINT REFERENCES listings(id) ON DELETE CASCADE,
  trigger       TEXT NOT NULL,        -- 'D-1' | 'OPEN_DAY' | 'CLOSE_SOON' | 'RESULT'
  scheduled_at  TIMESTAMPTZ NOT NULL,
  sent_at       TIMESTAMPTZ,
  error         TEXT
);

push_tokens (
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  platform   TEXT NOT NULL,            -- 'IOS' | 'ANDROID' | 'WEB'
  token      TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (platform, token)
);
```

### 1.8 감사 / 법규 버전

```sql
eligibility_rule_versions (
  id            BIGSERIAL PRIMARY KEY,
  effective_from DATE NOT NULL,
  document_url  TEXT,
  summary       TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- 자격 판정 엔진은 항상 해당 공고 announcement_date 기준으로 룰 버전 선택
```

## 2. 규모 가정

- `listings`: 월 유입 100~300건, 1년 ~3,600건 → 수년간 1만 건 이하. 인덱스만 잘 잡으면 문제 없음.
- `real_transactions`: 월 40만 건 → 연 500만 건. 파티셔닝(월 단위) 고려 필요.
- `matches`: 유저 × 활성 청약. 활성 청약 200건 × 유저 10만 = 2천만 → Phase 3 시점에 파티셔닝/TTL 정리.
- `commute_cache`: 유저당 수십 건, 문제 없음.

## 3. 마이그레이션 컨벤션

- 위치: `apps/api/src/main/resources/db/migration/` (classpath로 로딩)
- 파일명: `V{yyyyMMddHHmm}__{snake_case_description}.sql`
- 초기 버전은 도메인별 분할: `V202604240001__init_users.sql`, `V202604240002__init_profiles.sql`, ...
- PostGIS 확장: `V202604240000__enable_extensions.sql` (`CREATE EXTENSION postgis; CREATE EXTENSION pgcrypto;`)
- 프로덕션에서 되돌릴 때는 새 마이그레이션 추가, 기존 파일 수정 금지.
