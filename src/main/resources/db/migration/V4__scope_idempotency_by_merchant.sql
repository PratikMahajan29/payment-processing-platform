ALTER TABLE idempotency_records
    ADD COLUMN merchant_id UUID;

UPDATE idempotency_records
SET merchant_id = '00000000-0000-0000-0000-000000000001'
WHERE merchant_id IS NULL;

ALTER TABLE idempotency_records
    ALTER COLUMN merchant_id SET NOT NULL;

ALTER TABLE idempotency_records
    DROP CONSTRAINT idempotency_records_pkey;

ALTER TABLE idempotency_records
    ADD CONSTRAINT pk_idempotency_records
        PRIMARY KEY (merchant_id, idempotency_key);