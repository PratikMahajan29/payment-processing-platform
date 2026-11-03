ALTER TABLE payment_attempts
    ADD COLUMN gateway_idempotency_key VARCHAR(100);

UPDATE payment_attempts
SET gateway_idempotency_key =
        'PAYMENT-'
        || payment_id::text
        || '-ATTEMPT-'
        || attempt_number::text
WHERE gateway_idempotency_key IS NULL;

ALTER TABLE payment_attempts
    ALTER COLUMN gateway_idempotency_key SET NOT NULL;

ALTER TABLE payment_attempts
    ADD CONSTRAINT uq_payment_attempt_gateway_idempotency_key
        UNIQUE (gateway_idempotency_key);