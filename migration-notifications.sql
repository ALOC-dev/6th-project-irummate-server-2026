BEGIN;

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recipient_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category VARCHAR(20) NOT NULL,
    type VARCHAR(50) NOT NULL,
    event_key VARCHAR(200) NOT NULL,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMPTZ,
    UNIQUE (recipient_id, event_key),
    UNIQUE (id, recipient_id, category)
);

ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS notifications_category_check,
    DROP CONSTRAINT IF EXISTS notifications_category_check1,
    ADD CONSTRAINT notifications_category_check CHECK (
        category IN ('MATCHING', 'MATE', 'DORM_NOTICE', 'CERTIFICATION', 'RECRUITMENT', 'ADMIN_NOTICE')
    );

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_latest
    ON notifications (recipient_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_recipient_category_latest
    ON notifications (recipient_id, category, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_recipient_unread
    ON notifications (recipient_id) WHERE read_at IS NULL;

CREATE TABLE IF NOT EXISTS notification_preferences (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    paused BOOLEAN NOT NULL DEFAULT FALSE,
    chat BOOLEAN NOT NULL DEFAULT TRUE,
    matching BOOLEAN NOT NULL DEFAULT TRUE,
    mate BOOLEAN NOT NULL DEFAULT TRUE,
    dorm_notice BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE notification_preferences
    ADD COLUMN IF NOT EXISTS certification BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS recruitment BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS admin_notice BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS push_devices (
    installation_id UUID PRIMARY KEY,
    login_session_id UUID NOT NULL REFERENCES login_sessions(id) ON DELETE CASCADE,
    platform VARCHAR(10) NOT NULL CHECK (platform IN ('ANDROID', 'IOS')),
    fcm_token TEXT UNIQUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    disabled_at TIMESTAMPTZ,
    CONSTRAINT chk_push_device_active_token CHECK ((disabled_at IS NULL) = (fcm_token IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS idx_push_devices_session ON push_devices (login_session_id);

CREATE TABLE IF NOT EXISTS admin_notification_dispatches (
    event_key VARCHAR(200) PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS push_topic_subscriptions (
    fcm_token TEXT PRIMARY KEY,
    subscribed BOOLEAN,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_token UUID,
    lease_expires_at TIMESTAMPTZ
);
INSERT INTO push_topic_subscriptions (fcm_token)
SELECT fcm_token FROM push_devices WHERE fcm_token IS NOT NULL
ON CONFLICT (fcm_token) DO NOTHING;

CREATE TABLE IF NOT EXISTS push_jobs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    installation_id UUID REFERENCES push_devices(installation_id) ON DELETE CASCADE,
    recipient_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    login_session_id UUID,
    notification_id BIGINT,
    category VARCHAR(20) NOT NULL,
    event_key VARCHAR(200) NOT NULL,
    payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED', 'SKIPPED')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    lease_token UUID,
    lease_expires_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    last_error_code VARCHAR(80),
    UNIQUE (installation_id, recipient_id, event_key),
    CONSTRAINT chk_push_job_expiry CHECK (expires_at > created_at),
    CONSTRAINT chk_push_job_lease CHECK (
        (status = 'PROCESSING' AND lease_token IS NOT NULL AND lease_expires_at IS NOT NULL)
        OR (status <> 'PROCESSING' AND lease_token IS NULL AND lease_expires_at IS NULL)
    ),
    CONSTRAINT chk_push_job_finished CHECK ((status IN ('SENT', 'FAILED', 'SKIPPED')) = (finished_at IS NOT NULL))
);

ALTER TABLE push_jobs
    ADD COLUMN IF NOT EXISTS topic VARCHAR(128),
    ALTER COLUMN installation_id DROP NOT NULL,
    ALTER COLUMN recipient_id DROP NOT NULL,
    ALTER COLUMN login_session_id DROP NOT NULL,
    DROP CONSTRAINT IF EXISTS push_jobs_category_check,
    DROP CONSTRAINT IF EXISTS chk_push_job_target,
    DROP CONSTRAINT IF EXISTS chk_push_job_inbox_link,
    DROP CONSTRAINT IF EXISTS fk_push_job_notification,
    DROP CONSTRAINT IF EXISTS uk_push_jobs_topic_event,
    ADD CONSTRAINT push_jobs_category_check CHECK (
        category IN ('MATCHING', 'MATE', 'DORM_NOTICE', 'CERTIFICATION', 'RECRUITMENT', 'ADMIN_NOTICE', 'CHAT')
    ),
    ADD CONSTRAINT chk_push_job_target CHECK (
        (topic IS NULL AND installation_id IS NOT NULL AND recipient_id IS NOT NULL AND login_session_id IS NOT NULL)
        OR (topic IS NOT NULL AND installation_id IS NULL AND recipient_id IS NULL
            AND login_session_id IS NULL AND notification_id IS NULL AND category = 'ADMIN_NOTICE')
    ),
    ADD CONSTRAINT chk_push_job_inbox_link CHECK (topic IS NOT NULL OR (category = 'CHAT') = (notification_id IS NULL)),
    ADD CONSTRAINT fk_push_job_notification FOREIGN KEY (notification_id, recipient_id, category)
        REFERENCES notifications (id, recipient_id, category) ON DELETE CASCADE,
    ADD CONSTRAINT uk_push_jobs_topic_event UNIQUE (topic, event_key);

CREATE INDEX IF NOT EXISTS idx_push_jobs_pending ON push_jobs (next_attempt_at, id) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_push_jobs_expired_leases
    ON push_jobs (lease_expires_at, id) WHERE status = 'PROCESSING';
CREATE INDEX IF NOT EXISTS idx_push_jobs_notification
    ON push_jobs (notification_id) WHERE notification_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_push_jobs_recipient ON push_jobs (recipient_id);

COMMIT;
