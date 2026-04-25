-- VWorld 연속지적도 폴리곤 (FeatureCollection GeoJSON)
ALTER TABLE listings
    ADD COLUMN polygon_geojson JSONB,
    ADD COLUMN polygon_fetched_at TIMESTAMPTZ;

CREATE INDEX idx_listings_polygon_pending ON listings(polygon_fetched_at)
    WHERE polygon_fetched_at IS NULL AND latitude IS NOT NULL;
