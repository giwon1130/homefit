-- listings 좌표 (Phase 2 통근 계산 + 지도 표시용)
-- PostGIS 도입 전까지는 단순 NUMERIC. 도입 후 ALTER TABLE ... ADD COLUMN geom 으로 보강.
ALTER TABLE listings
    ADD COLUMN latitude  NUMERIC(9,6),
    ADD COLUMN longitude NUMERIC(9,6),
    ADD COLUMN geocoded_at TIMESTAMPTZ;

CREATE INDEX idx_listings_geocoded ON listings(geocoded_at) WHERE geocoded_at IS NULL;
