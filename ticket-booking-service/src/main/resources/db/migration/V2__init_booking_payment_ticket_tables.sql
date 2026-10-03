-- V2: Reservations, Seat Claims, Orders, Payments, Webhook Events, Idempotency, Tickets, Check-in Logs

CREATE TABLE reservations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    event_id UUID NOT NULL REFERENCES events(id),
    status VARCHAR(32) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reservation_expiry ON reservations(status, expires_at);
CREATE INDEX idx_reservation_user ON reservations(user_id, created_at);

CREATE TABLE reservation_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_id UUID NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    seat_id UUID REFERENCES seats(id),
    ticket_type_id UUID REFERENCES ticket_types(id),
    quantity INT NOT NULL DEFAULT 1,
    unit_price BIGINT NOT NULL
);
CREATE INDEX idx_reservation_items_res ON reservation_items(reservation_id);

CREATE TABLE seat_claims (
    seat_id UUID PRIMARY KEY REFERENCES seats(id) ON DELETE CASCADE,
    reservation_id UUID NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_seat_claims_reservation ON seat_claims(reservation_id);

CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    reservation_id UUID NOT NULL UNIQUE REFERENCES reservations(id),
    event_id UUID NOT NULL REFERENCES events(id),
    order_code VARCHAR(64) NOT NULL UNIQUE,
    total_amount BIGINT NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'VND',
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_orders_user ON orders(user_id, created_at);
CREATE INDEX idx_orders_event ON orders(event_id, status);

CREATE TABLE order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    seat_id UUID REFERENCES seats(id),
    ticket_type_id UUID REFERENCES ticket_types(id),
    unit_price BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    subtotal BIGINT NOT NULL
);
CREATE INDEX idx_order_items_order ON order_items(order_id);

CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id),
    provider VARCHAR(32) NOT NULL,
    provider_payment_id VARCHAR(255),
    idempotency_key VARCHAR(255) NOT NULL,
    amount BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    payment_url TEXT,
    failure_reason TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_provider_pid ON payments(provider_payment_id);
CREATE UNIQUE INDEX uq_payments_provider_pid
    ON payments(provider, provider_payment_id)
    WHERE provider_payment_id IS NOT NULL;

CREATE TABLE webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(32) NOT NULL,
    provider_event_id VARCHAR(255) NOT NULL,
    payload_hash VARCHAR(128) NOT NULL,
    raw_payload JSONB,
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    UNIQUE (provider, provider_event_id)
);

CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    endpoint VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    response_status INT NOT NULL,
    response_body JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    UNIQUE (user_id, endpoint, idempotency_key)
);

CREATE TABLE tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id),
    event_id UUID NOT NULL REFERENCES events(id),
    seat_id UUID REFERENCES seats(id),
    ticket_type_id UUID REFERENCES ticket_types(id),
    user_id UUID NOT NULL REFERENCES users(id),
    ticket_code VARCHAR(64) NOT NULL UNIQUE,
    qr_token_hash VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'VALID',
    checked_in_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_tickets_active_seat
    ON tickets(seat_id)
    WHERE seat_id IS NOT NULL AND status IN ('VALID', 'CHECKED_IN');
CREATE INDEX idx_tickets_user ON tickets(user_id, created_at);
CREATE INDEX idx_tickets_event ON tickets(event_id, status);

CREATE TABLE checkin_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES tickets(id),
    staff_id UUID NOT NULL REFERENCES users(id),
    event_id UUID NOT NULL REFERENCES events(id),
    checked_in_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    device_info TEXT
);
CREATE INDEX idx_checkin_logs_event ON checkin_logs(event_id, checked_in_at);
