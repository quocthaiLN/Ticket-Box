ALTER TABLE artist_bio_jobs ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE artist_bio_jobs ADD COLUMN next_attempt_at TIMESTAMPTZ;
ALTER TABLE artist_bio_jobs ADD COLUMN processing_token UUID;
ALTER TABLE artist_bio_jobs ADD COLUMN lease_until TIMESTAMPTZ;
ALTER TABLE artist_bio_jobs ADD CONSTRAINT chk_abj_attempts CHECK (attempts >= 0);
CREATE INDEX idx_abj_pending_due ON artist_bio_jobs(next_attempt_at, updated_at)
    WHERE status = 'PENDING';
CREATE INDEX idx_abj_processing_lease ON artist_bio_jobs(lease_until)
    WHERE status = 'PROCESSING';
