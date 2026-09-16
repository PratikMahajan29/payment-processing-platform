CREATE TABLE idempotency_records (
    idempotency_key VARCHAR(100) PRIMARY KEY,

    request_hash VARCHAR(64) NOT NULL,

    payment_id UUID NOT NULL,

    status VARCHAR(20) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_idempotency_payment
        FOREIGN KEY (payment_id)
        REFERENCES payments(payment_id)
);