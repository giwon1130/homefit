-- 모바일 푸시 알림. Expo Push Service 사용 (APNs/FCM 추상화).
-- 토큰은 디바이스 단위 — 한 유저가 여러 디바이스 보유 가능, 한 토큰은 한 유저에게만 매핑.
-- 같은 토큰을 다른 유저가 등록하면 (디바이스 양도/공유) 마지막 등록자에게 귀속.

CREATE TABLE push_tokens (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform     TEXT NOT NULL,                    -- 'IOS' | 'ANDROID' | 'EXPO' (단일화 시 'EXPO')
    token        TEXT NOT NULL UNIQUE,             -- ExponentPushToken[...] 그대로 저장
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_push_tokens_user ON push_tokens(user_id);

-- 사용자별 푸시 알림 수신 토글 (이메일과 별도 — 둘 다 받거나 하나만 받거나).
ALTER TABLE users
    ADD COLUMN notification_push_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- 기존 unique 인덱스는 (user, listing, kind) 였음. 이메일+푸시 둘 다 발송 가능하도록 channel 추가.
DROP INDEX IF EXISTS uq_notifications_user_listing_kind;
CREATE UNIQUE INDEX uq_notifications_user_listing_kind_channel
    ON notifications(user_id, listing_id, kind, channel);
