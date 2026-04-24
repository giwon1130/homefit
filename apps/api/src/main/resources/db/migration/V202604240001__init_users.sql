CREATE TABLE users (
    id                BIGSERIAL PRIMARY KEY,
    email             TEXT UNIQUE NOT NULL,
    oauth_provider    TEXT NOT NULL,
    oauth_subject     TEXT NOT NULL,
    display_name      TEXT,
    profile_image_url TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login_at     TIMESTAMPTZ,
    UNIQUE (oauth_provider, oauth_subject)
);

CREATE TABLE refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
