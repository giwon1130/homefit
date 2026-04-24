CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- PostGIS는 Phase 2(통근/지역 기능)에서 별도 마이그레이션으로 추가.
-- Railway 기본 Postgres 플러그인은 PostGIS 미포함이라, 그 시점에 Postgres 서비스를
-- postgis/postgis 이미지 기반 커스텀 서비스로 교체하고 별도 migration에서 extension 활성화.
