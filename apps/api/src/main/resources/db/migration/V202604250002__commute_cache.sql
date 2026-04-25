-- 출발지/도착지 좌표 → 대중교통 통근시간 캐시
-- 좌표는 4자리(약 10m)로 양자화해 캐시 hit률 ↑.
CREATE TABLE commute_cache (
    origin_lat     NUMERIC(8,4) NOT NULL,
    origin_lng     NUMERIC(9,4) NOT NULL,
    dest_lat       NUMERIC(8,4) NOT NULL,
    dest_lng       NUMERIC(9,4) NOT NULL,
    total_minutes  INT NOT NULL,
    walk_minutes   INT,
    transfers      INT,
    payment_krw    INT,
    cached_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (origin_lat, origin_lng, dest_lat, dest_lng)
);

CREATE INDEX idx_commute_cache_cached_at ON commute_cache(cached_at);
