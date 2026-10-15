-- =============================================================================
-- Migration V4: Payment Resilience, Payment Events Audit Trail & Refunds
-- =============================================================================

-- 1. Cập nhật bảng payments
ALTER TABLE payments ADD COLUMN IF NOT EXISTS merchant_txn_ref VARCHAR(100);

-- Backfill dữ liệu cho các dòng payment cũ (nếu có)
UPDATE payments
SET merchant_txn_ref = replace(cast(id as text), '-', '')
WHERE merchant_txn_ref IS NULL;

ALTER TABLE payments ALTER COLUMN merchant_txn_ref SET NOT NULL;
ALTER TABLE payments ADD CONSTRAINT uk_payments_merchant_txn_ref UNIQUE (merchant_txn_ref);

ALTER TABLE payments ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS failure_code VARCHAR(100);

ALTER TABLE payments DROP CONSTRAINT IF EXISTS chk_payments_status;
ALTER TABLE payments ADD CONSTRAINT chk_payments_status
    CHECK (status IN ('INITIATED', 'CREATING', 'PENDING', 'SUCCEEDED', 'SUCCESS', 'FAILED', 'CANCELLED', 'EXPIRED', 'REFUNDED'));

-- 2. Tạo bảng payment_events (Append-only Audit Log & Deduplication)
CREATE TABLE payment_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID,
    provider VARCHAR(50) NOT NULL,
    event_source VARCHAR(50) NOT NULL,
    dedupe_key VARCHAR(255) NOT NULL,
    event_type VARCHAR(100),
    raw_payload JSONB NOT NULL,
    signature_valid BOOLEAN NOT NULL,
    processing_status VARCHAR(50) NOT NULL DEFAULT 'RECEIVED',
    error_message TEXT,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ,
    CONSTRAINT fk_payment_events_payment FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE SET NULL,
    CONSTRAINT chk_payment_events_provider CHECK (provider IN ('VNPAY', 'MOMO', 'STRIPE', 'ALIPAY')),
    CONSTRAINT chk_payment_events_source CHECK (event_source IN ('IPN', 'RETURN_URL', 'QUERY_API', 'INTERNAL')),
    CONSTRAINT chk_payment_events_status CHECK (processing_status IN ('RECEIVED', 'PROCESSED', 'FAILED', 'IGNORED')),
    CONSTRAINT uk_payment_events_provider_dedupe UNIQUE (provider, dedupe_key)
);

CREATE INDEX idx_payment_events_payment_id ON payment_events(payment_id);
CREATE INDEX idx_payment_events_status ON payment_events(processing_status, received_at);

-- 3. Tạo bảng refunds (Quản lý lệnh hoàn tiền độc lập & idempotent)
CREATE TABLE refunds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'REQUESTED',
    provider_refund_id VARCHAR(255),
    reason TEXT,
    requested_by UUID NOT NULL,
    failure_code VARCHAR(100),
    failure_message TEXT,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE RESTRICT,
    CONSTRAINT uk_refunds_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT chk_refunds_amount CHECK (amount > 0),
    CONSTRAINT chk_refunds_status CHECK (status IN ('REQUESTED', 'PROCESSING', 'SUCCESS', 'FAILED'))
);

CREATE INDEX idx_refunds_payment_id ON refunds(payment_id);
CREATE INDEX idx_refunds_status ON refunds(status, requested_at);
