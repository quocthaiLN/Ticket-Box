ALTER TABLE payments DROP CONSTRAINT chk_payments_status;
ALTER TABLE payments ADD CONSTRAINT chk_payments_status
    CHECK (status IN ('CREATING', 'PENDING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED', 'REFUNDED'));

ALTER TABLE payments ADD COLUMN next_reconcile_at TIMESTAMPTZ;
ALTER TABLE payments ADD COLUMN refund_required BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_payments_next_reconcile_at ON payments(next_reconcile_at)
    WHERE next_reconcile_at IS NOT NULL;
CREATE INDEX idx_payments_refund_required ON payments(refund_required)
    WHERE refund_required = TRUE;
