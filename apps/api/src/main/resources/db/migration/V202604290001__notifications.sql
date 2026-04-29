-- D-1 청약 마감 알림 발송 로그 + 사용자 토글
-- 즐겨찾기한 청약의 applicationEnd 임박 시 이메일 발송, 중복 방지를 위한 unique idx.

ALTER TABLE users
    ADD COLUMN notification_email_enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE notifications (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    listing_id  BIGINT NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    kind        TEXT   NOT NULL,             -- 'D_MINUS_1' (당분간 1종)
    channel     TEXT   NOT NULL,             -- 'EMAIL'
    sent_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    status      TEXT   NOT NULL,             -- 'SENT' | 'FAILED'
    error_text  TEXT
);

-- 같은 (user, listing, kind) 조합은 한 번만. 재시도/중복 발송 차단.
CREATE UNIQUE INDEX uq_notifications_user_listing_kind
    ON notifications(user_id, listing_id, kind);

CREATE INDEX idx_notifications_user_sent_at
    ON notifications(user_id, sent_at DESC);
