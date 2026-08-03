ALTER TABLE appointments
    ADD COLUMN video_conference BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN meeting_url VARCHAR(500);

CREATE INDEX idx_appointments_video ON appointments(tenant_id, video_conference, starts_at DESC)
    WHERE video_conference = TRUE AND status = 'SCHEDULED';
