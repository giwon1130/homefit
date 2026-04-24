CREATE TABLE listings (
    id                       BIGSERIAL PRIMARY KEY,
    source                   TEXT NOT NULL,           -- 'PUBLIC_DATA_APT' | 'LH' | 'SH' | ...
    source_ref               TEXT NOT NULL,           -- 공고번호/주택관리번호 등 원본 ID
    listing_type             TEXT NOT NULL,           -- 'PRIVATE_SALE' | 'PUBLIC_SALE' | 'HAPPY_HOUSE' | ...
    name                     TEXT NOT NULL,
    developer                TEXT,
    sido                     TEXT,
    sigungu                  TEXT,
    address                  TEXT,
    -- geom GEOMETRY(POINT, 4326) -- Phase 2에서 PostGIS 활성화 후 ALTER TABLE로 추가
    application_start        TIMESTAMPTZ,
    application_end          TIMESTAMPTZ,
    announcement_date        DATE,
    winner_announcement_date DATE,
    contract_start_date      DATE,
    contract_end_date        DATE,
    move_in_date             DATE,
    total_supply             INT,
    raw_document_url         TEXT,
    raw_json                 JSONB,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (source, source_ref)
);

CREATE INDEX idx_listings_app_period ON listings(application_start, application_end);
CREATE INDEX idx_listings_announcement ON listings(announcement_date DESC);
CREATE INDEX idx_listings_sido_sigungu ON listings(sido, sigungu);

CREATE TABLE listing_units (
    id            BIGSERIAL PRIMARY KEY,
    listing_id    BIGINT NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    model_no      TEXT,
    unit_type     TEXT,            -- '84A', '59B' 등
    size_m2       NUMERIC(7,2),
    supply_count  INT,
    price_max_krw BIGINT,          -- 분양최고금액 (원 단위로 정규화)
    raw_json      JSONB
);

CREATE INDEX idx_listing_units_listing ON listing_units(listing_id);

CREATE TABLE ingestion_runs (
    id           BIGSERIAL PRIMARY KEY,
    source       TEXT NOT NULL,
    started_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at  TIMESTAMPTZ,
    status       TEXT NOT NULL,    -- 'RUNNING' | 'SUCCESS' | 'FAILED'
    pages_read   INT DEFAULT 0,
    rows_upserted INT DEFAULT 0,
    error        TEXT
);

CREATE INDEX idx_ingestion_runs_source_started ON ingestion_runs(source, started_at DESC);
