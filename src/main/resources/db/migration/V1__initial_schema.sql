CREATE TABLE payments (
    payment_id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    customer_id UUID NOT NULL,

    amount BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,

    status VARCHAR(20) NOT NULL,

    idempotency_key VARCHAR(100) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT uq_payments_idempotency_key
        UNIQUE (idempotency_key),

    CONSTRAINT chk_payments_amount_positive
        CHECK (amount > 0),

    CONSTRAINT chk_payments_currency_format
        CHECK (char_length(currency) = 3)
);

CREATE TABLE payment_attempts (
    attempt_id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,

    attempt_number INTEGER NOT NULL,

    status VARCHAR(20) NOT NULL,

    gateway VARCHAR(50),
    gateway_transaction_id VARCHAR(100),

    failure_code VARCHAR(50),
    failure_message TEXT,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_payment_attempts_payment
        FOREIGN KEY (payment_id)
        REFERENCES payments(payment_id),

    CONSTRAINT uq_payment_attempt_number
        UNIQUE (payment_id, attempt_number),

    CONSTRAINT chk_payment_attempt_number_positive
        CHECK (attempt_number > 0)
);

CREATE TABLE refunds (
    refund_id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,

    amount BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,

    status VARCHAR(20) NOT NULL,

    gateway_refund_id VARCHAR(100),
    reason VARCHAR(255),

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_refunds_payment
        FOREIGN KEY (payment_id)
        REFERENCES payments(payment_id),

    CONSTRAINT chk_refund_amount_positive
        CHECK (amount > 0),

    CONSTRAINT chk_refund_currency_format
        CHECK (char_length(currency) = 3)
);