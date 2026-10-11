-- TicketBox Database Migration V1: Initial Schema
-- PostgreSQL DDL Script matching domain entities and database-design.md

-- Enable UUID extension if not present
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =============================================================================
-- 1. USERS & ACCESS CONTROL
-- =============================================================================

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    role VARCHAR(20) NOT NULL DEFAULT 'AUDIENCE',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_phone UNIQUE (phone),
    CONSTRAINT chk_users_email CHECK (email LIKE '%@%'),
    CONSTRAINT chk_users_role CHECK (role IN ('AUDIENCE', 'ORGANIZER', 'CHECKER', 'ADMIN')),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'PENDING', 'SUSPENDED', 'DELETED'))
);

CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_status ON users(status);

CREATE TABLE user_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    password_hash TEXT,
    provider TEXT NOT NULL,
    provider_user_id TEXT NOT NULL,
    user_account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_user_accounts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_user_accounts_provider_provider_user_id UNIQUE (provider, provider_user_id),
    CONSTRAINT chk_user_accounts_local_password_hash
        CHECK (provider <> 'LOCAL' OR (password_hash IS NOT NULL AND BTRIM(password_hash) <> '')),
    CONSTRAINT chk_user_accounts_status CHECK (user_account_status IN ('ACTIVE', 'SUSPENDED', 'DELETED'))
);

CREATE INDEX idx_user_accounts_user_id ON user_accounts(user_id);

-- =============================================================================
-- 2. CATALOG (CONCERTS & SEAT ZONES)
-- =============================================================================

CREATE TABLE concerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organizer_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL,
    venue VARCHAR(255) NOT NULL,
    description TEXT,
    artist_name VARCHAR(255) NOT NULL,
    artist_bio TEXT,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    cover_image_url TEXT,
    seat_map_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_concerts_slug UNIQUE (slug),
    CONSTRAINT fk_concerts_organizer FOREIGN KEY (organizer_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_concerts_dates CHECK (ends_at > starts_at),
    CONSTRAINT chk_concerts_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED', 'COMPLETED'))
);

CREATE INDEX idx_concerts_title ON concerts(title);
CREATE INDEX idx_concerts_slug ON concerts(slug);
CREATE INDEX idx_concerts_status_starts ON concerts(status, starts_at);
CREATE INDEX idx_concerts_organizer_id ON concerts(organizer_id);

CREATE TABLE seat_zones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    concert_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    capacity INT NOT NULL,
    svg_path TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_seat_zones_concert FOREIGN KEY (concert_id) REFERENCES concerts(id) ON DELETE CASCADE,
    CONSTRAINT uk_seat_zones_concert_code UNIQUE (concert_id, code),
    CONSTRAINT uk_seat_zones_id_concert UNIQUE (id, concert_id),
    CONSTRAINT chk_seat_zones_capacity CHECK (capacity > 0)
);

CREATE INDEX idx_seat_zones_concert_id ON seat_zones(concert_id);
CREATE INDEX idx_seat_zones_code ON seat_zones(code);
CREATE INDEX idx_seat_zones_name ON seat_zones(name);
CREATE INDEX idx_seat_zones_concert_sort ON seat_zones(concert_id, sort_order);

-- =============================================================================
-- 3. INVENTORY & TICKET TYPES
-- =============================================================================

CREATE TABLE ticket_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    concert_id UUID NOT NULL,
    seat_zone_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    price NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    total_quantity INT NOT NULL,
    held_quantity INT NOT NULL DEFAULT 0,
    sold_quantity INT NOT NULL DEFAULT 0,
    max_per_user INT NOT NULL,
    sale_start_at TIMESTAMPTZ NOT NULL,
    sale_end_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ticket_types_concert FOREIGN KEY (concert_id) REFERENCES concerts(id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_types_seat_zone_composite FOREIGN KEY (seat_zone_id, concert_id) REFERENCES seat_zones(id, concert_id) ON DELETE CASCADE,
    CONSTRAINT uk_ticket_types_concert_name UNIQUE (concert_id, name),
    CONSTRAINT uk_ticket_types_id_concert UNIQUE (id, concert_id),
    CONSTRAINT chk_ticket_types_price CHECK (price >= 0),
    CONSTRAINT chk_ticket_types_total_quantity CHECK (total_quantity >= 0),
    CONSTRAINT chk_ticket_types_held_quantity CHECK (held_quantity >= 0),
    CONSTRAINT chk_ticket_types_sold_quantity CHECK (sold_quantity >= 0),
    CONSTRAINT chk_ticket_types_max_per_user CHECK (max_per_user > 0),
    CONSTRAINT chk_ticket_types_inventory CHECK (total_quantity >= held_quantity + sold_quantity),
    CONSTRAINT chk_ticket_types_sale_window CHECK (sale_end_at > sale_start_at),
    CONSTRAINT chk_ticket_types_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ON_SALE', 'SUSPENDED', 'CLOSED', 'SOLD_OUT'))
);

CREATE INDEX idx_ticket_types_concert_status ON ticket_types(concert_id, status);
CREATE INDEX idx_ticket_types_sale_window ON ticket_types(sale_start_at, sale_end_at);
CREATE INDEX idx_ticket_types_seat_zone ON ticket_types(seat_zone_id);

CREATE TABLE user_ticket_type_counters (
    user_id UUID NOT NULL,
    ticket_type_id UUID NOT NULL,
    held_quantity INT NOT NULL DEFAULT 0,
    paid_quantity INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, ticket_type_id),
    CONSTRAINT fk_uttc_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_uttc_ticket_type FOREIGN KEY (ticket_type_id) REFERENCES ticket_types(id) ON DELETE CASCADE,
    CONSTRAINT chk_uttc_held_quantity CHECK (held_quantity >= 0),
    CONSTRAINT chk_uttc_paid_quantity CHECK (paid_quantity >= 0)
);

CREATE INDEX idx_uttc_ticket_type_id ON user_ticket_type_counters(ticket_type_id);

CREATE OR REPLACE FUNCTION fn_check_user_ticket_limit()
RETURNS TRIGGER AS $$
DECLARE
    v_max_per_user INT;
BEGIN
    SELECT max_per_user INTO v_max_per_user
    FROM ticket_types
    WHERE id = NEW.ticket_type_id;

    IF (NEW.held_quantity + NEW.paid_quantity) > v_max_per_user THEN
        RAISE EXCEPTION 'Constraint violation: User % exceeds max_per_user (%) for ticket_type % (current: %)',
            NEW.user_id, v_max_per_user, NEW.ticket_type_id, (NEW.held_quantity + NEW.paid_quantity);
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_check_uttc_limit
AFTER INSERT OR UPDATE OF held_quantity, paid_quantity ON user_ticket_type_counters
DEFERRABLE INITIALLY IMMEDIATE
FOR EACH ROW
EXECUTE FUNCTION fn_check_user_ticket_limit();

CREATE OR REPLACE FUNCTION fn_check_ticket_type_max_reduction()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.max_per_user < OLD.max_per_user THEN
        IF EXISTS (
            SELECT 1 
            FROM user_ticket_type_counters
            WHERE ticket_type_id = NEW.id 
              AND (held_quantity + paid_quantity) > NEW.max_per_user
        ) THEN
            RAISE EXCEPTION 'Cannot reduce max_per_user to %: existing users exceed this limit', NEW.max_per_user;
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_check_ticket_type_max_reduction
BEFORE UPDATE OF max_per_user ON ticket_types
FOR EACH ROW
EXECUTE FUNCTION fn_check_ticket_type_max_reduction();

-- =============================================================================
-- 4. ORDERS & ORDER ITEMS
-- =============================================================================

CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    concert_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'HELD',
    total_amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    hold_expires_at TIMESTAMPTZ,
    confirmed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    expired_at TIMESTAMPTZ,
    cancelled_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_orders_concert FOREIGN KEY (concert_id) REFERENCES concerts(id),
    CONSTRAINT uk_orders_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT chk_orders_total_amount CHECK (total_amount >= 0),
    CONSTRAINT chk_orders_status CHECK (status IN ('HELD', 'CONFIRMED', 'CANCELLED', 'EXPIRED'))
);

CREATE INDEX idx_orders_user_status_created ON orders(user_id, status, created_at);
CREATE INDEX idx_orders_concert_status ON orders(concert_id, status);
CREATE INDEX idx_orders_hold_expires_at ON orders(hold_expires_at);

CREATE TABLE order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL,
    ticket_type_id UUID NOT NULL,
    quantity INT NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    line_total NUMERIC(12, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_ticket_type FOREIGN KEY (ticket_type_id) REFERENCES ticket_types(id),
    CONSTRAINT uk_order_items_order_ticket_type UNIQUE (order_id, ticket_type_id),
    CONSTRAINT chk_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_order_items_unit_price CHECK (unit_price >= 0),
    CONSTRAINT chk_order_items_line_total CHECK (line_total = quantity * unit_price)
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_ticket_type_id ON order_items(ticket_type_id);

CREATE OR REPLACE FUNCTION fn_validate_order_total_amount()
RETURNS TRIGGER AS $$
DECLARE
    v_order_id UUID;
    v_order_total NUMERIC(12, 2);
    v_items_sum NUMERIC(12, 2);
BEGIN
    IF TG_TABLE_NAME = 'orders' THEN
        v_order_id := NEW.id;
        v_order_total := NEW.total_amount;
    ELSE
        v_order_id := COALESCE(NEW.order_id, OLD.order_id);
        SELECT total_amount INTO v_order_total 
        FROM orders 
        WHERE id = v_order_id;
    END IF;
    IF v_order_total IS NULL THEN
        RETURN NULL;
    END IF;
    SELECT COALESCE(SUM(line_total), 0) INTO v_items_sum
    FROM order_items
    WHERE order_id = v_order_id;
    IF v_order_total <> v_items_sum THEN
        RAISE EXCEPTION 'Constraint violation: orders.total_amount (%) must equal sum of order_items.line_total (%) for order %',
            v_order_total, v_items_sum, v_order_id;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_check_orders_total
AFTER INSERT OR UPDATE OF total_amount ON orders
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION fn_validate_order_total_amount();

CREATE CONSTRAINT TRIGGER trg_check_order_items_total
AFTER INSERT OR UPDATE OF line_total, order_id OR DELETE ON order_items
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION fn_validate_order_total_amount();

-- =============================================================================
-- 5. PAYMENTS
-- =============================================================================

CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL,
    provider VARCHAR(50) NOT NULL,
    provider_transaction_id VARCHAR(255),
    idempotency_key VARCHAR(128) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    checkout_url TEXT,
    provider_payload JSONB,
    webhook_payload JSONB,
    webhook_received_at TIMESTAMPTZ,
    webhook_signature_valid BOOLEAN,
    paid_at TIMESTAMPTZ,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT uk_payments_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uk_payments_provider_tx UNIQUE (provider, provider_transaction_id),
    CONSTRAINT chk_payments_amount CHECK (amount > 0),
    CONSTRAINT chk_payments_provider CHECK (provider IN ('VNPAY', 'MOMO', 'STRIPE', 'ALIPAY')),
    CONSTRAINT chk_payments_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED', 'REFUNDED'))
);

CREATE INDEX idx_payments_order_status ON payments(order_id, status);
CREATE INDEX idx_payments_status_created ON payments(status, created_at);

-- =============================================================================
-- 6. TICKETS (E-TICKETS & QR)
-- =============================================================================

CREATE TABLE tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL,
    order_item_id UUID NOT NULL,
    user_id UUID NOT NULL,
    concert_id UUID NOT NULL,
    ticket_type_id UUID NOT NULL,
    seat_zone_id UUID NOT NULL,
    qr_token_hash VARCHAR(255) NOT NULL,
    qr_payload JSONB,
    qr_signature TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
    issued_at TIMESTAMPTZ NOT NULL,
    checked_in_at TIMESTAMPTZ,
    checked_in_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tickets_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_tickets_order_item FOREIGN KEY (order_item_id) REFERENCES order_items(id),
    CONSTRAINT fk_tickets_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_tickets_concert FOREIGN KEY (concert_id) REFERENCES concerts(id),
    CONSTRAINT fk_tickets_checker FOREIGN KEY (checked_in_by) REFERENCES users(id),
    CONSTRAINT fk_tickets_ticket_type_composite FOREIGN KEY (ticket_type_id, concert_id) REFERENCES ticket_types(id, concert_id),
    CONSTRAINT fk_tickets_seat_zone_composite FOREIGN KEY (seat_zone_id, concert_id) REFERENCES seat_zones(id, concert_id),
    CONSTRAINT uk_tickets_qr_token_hash UNIQUE (qr_token_hash),
    CONSTRAINT chk_tickets_status CHECK (status IN ('ISSUED', 'CHECKED_IN', 'REFUNDED', 'INVALIDATED', 'CANCELLED'))
);

CREATE INDEX idx_tickets_user_concert_status ON tickets(user_id, concert_id, status);
CREATE INDEX idx_tickets_user_ticket_type_status ON tickets(user_id, ticket_type_id, status);
CREATE INDEX idx_tickets_ticket_type_status ON tickets(ticket_type_id, status);
CREATE INDEX idx_tickets_order_id ON tickets(order_id);
CREATE INDEX idx_tickets_order_item_id ON tickets(order_item_id);

-- =============================================================================
-- 7. GUEST CSV IMPORT
-- =============================================================================

CREATE TABLE guest_import_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    concert_id UUID NOT NULL,
    uploaded_by UUID NOT NULL,
    file_url TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_rows INT NOT NULL DEFAULT 0,
    success_rows INT NOT NULL DEFAULT 0,
    error_rows INT NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_guest_import_jobs_concert FOREIGN KEY (concert_id) REFERENCES concerts(id) ON DELETE CASCADE,
    CONSTRAINT fk_guest_import_jobs_user FOREIGN KEY (uploaded_by) REFERENCES users(id),
    CONSTRAINT chk_gij_total_rows CHECK (total_rows >= 0),
    CONSTRAINT chk_gij_success_rows CHECK (success_rows >= 0),
    CONSTRAINT chk_gij_error_rows CHECK (error_rows >= 0),
    CONSTRAINT chk_gij_status CHECK (status IN ('PENDING', 'PROCESSING', 'DONE', 'PARTIAL', 'FAILED'))
);

CREATE INDEX idx_guest_import_jobs_concert_status ON guest_import_jobs(concert_id, status);

CREATE TABLE guest_list (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    concert_id UUID NOT NULL,
    seat_zone_id UUID,
    import_job_id UUID,
    full_name VARCHAR(255) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(255),
    code VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'INVITED',
    checked_in_at TIMESTAMPTZ,
    checked_in_by UUID,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_guest_list_concert FOREIGN KEY (concert_id) REFERENCES concerts(id) ON DELETE CASCADE,
    CONSTRAINT fk_guest_list_seat_zone_composite FOREIGN KEY (seat_zone_id, concert_id) REFERENCES seat_zones(id, concert_id),
    CONSTRAINT fk_guest_list_import_job FOREIGN KEY (import_job_id) REFERENCES guest_import_jobs(id) ON DELETE SET NULL,
    CONSTRAINT fk_guest_list_checker FOREIGN KEY (checked_in_by) REFERENCES users(id),
    CONSTRAINT uk_guest_list_concert_phone UNIQUE (concert_id, phone),
    CONSTRAINT uk_guest_list_concert_code UNIQUE (concert_id, code),
    CONSTRAINT chk_guest_list_status CHECK (status IN ('INVITED', 'CHECKED_IN', 'CANCELLED'))
);

CREATE INDEX idx_guest_list_concert_status ON guest_list(concert_id, status);
CREATE INDEX idx_guest_list_phone ON guest_list(phone);

CREATE TABLE guest_import_errors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL,
    row_number INT NOT NULL,
    raw_data JSONB,
    error_code VARCHAR(100) NOT NULL,
    error_message TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_guest_import_errors_job FOREIGN KEY (job_id) REFERENCES guest_import_jobs(id) ON DELETE CASCADE,
    CONSTRAINT uk_guest_import_errors_job_row_code UNIQUE (job_id, row_number, error_code),
    CONSTRAINT chk_gie_row_number CHECK (row_number > 0)
);

CREATE INDEX idx_guest_import_errors_job_id ON guest_import_errors(job_id);

-- =============================================================================
-- 8. AI ARTIST BIO
-- =============================================================================

CREATE TABLE artist_bio_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    concert_id UUID NOT NULL,
    requested_by UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    source_file_url TEXT NOT NULL,
    extracted_text TEXT,
    generated_bio TEXT,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    processing_token UUID,
    lease_until TIMESTAMPTZ,
    CONSTRAINT fk_artist_bio_jobs_concert FOREIGN KEY (concert_id) REFERENCES concerts(id) ON DELETE CASCADE,
    CONSTRAINT fk_artist_bio_jobs_user FOREIGN KEY (requested_by) REFERENCES users(id),
    CONSTRAINT chk_abj_attempts CHECK (attempts >= 0),
    CONSTRAINT chk_abj_status CHECK (status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED'))
);

CREATE INDEX idx_abj_concert_status ON artist_bio_jobs(concert_id, status);
CREATE INDEX idx_abj_status_created ON artist_bio_jobs(status, created_at);
CREATE INDEX idx_abj_pending_due ON artist_bio_jobs(next_attempt_at, updated_at)
    WHERE status = 'PENDING';
CREATE INDEX idx_abj_processing_lease ON artist_bio_jobs(lease_until)
    WHERE status = 'PROCESSING';

-- =============================================================================
-- 9. NOTIFICATION LOG / QUEUE
-- =============================================================================

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,
    concert_id UUID,
    ticket_id UUID,
    channel VARCHAR(20) NOT NULL,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payload JSONB NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    error_message TEXT,
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_notifications_concert FOREIGN KEY (concert_id) REFERENCES concerts(id),
    CONSTRAINT fk_notifications_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id),
    CONSTRAINT chk_notifications_attempts CHECK (attempts >= 0),
    CONSTRAINT chk_notifications_channel CHECK (channel IN ('APP', 'EMAIL', 'SMS', 'ZALO')),
    CONSTRAINT chk_notifications_type CHECK (type IN ('ORDER_HELD', 'ORDER_CONFIRMED', 'ORDER_CANCELLED', 'TICKET_ISSUED', 'CONCERT_REMINDER', 'SYSTEM_ALERT', 'EVENT_REMINDER')),
    CONSTRAINT chk_notifications_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX idx_notifications_status_created ON notifications(status, created_at);
CREATE INDEX idx_notifications_user_created ON notifications(user_id, created_at);
CREATE INDEX idx_notifications_concert_status ON notifications(concert_id, status);
CREATE INDEX idx_notifications_ticket_id ON notifications(ticket_id);

-- =============================================================================
-- 10. AUDIT LOGS
-- =============================================================================

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100),
    metadata JSONB,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_user_id) REFERENCES users(id)
);

CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_actor_created ON audit_logs(actor_user_id, created_at);
