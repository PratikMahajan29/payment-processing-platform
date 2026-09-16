ALTER TABLE payments
    ADD COLUMN merchant_id UUID;

-- Backfill payments that already have an idempotency record.
UPDATE payments p
SET merchant_id = ir.merchant_id
FROM idempotency_records ir
WHERE ir.payment_id = p.payment_id
  AND p.merchant_id IS NULL;

-- Existing payments without a matching idempotency record
-- are assigned to the legacy/default merchant used during migration.
UPDATE payments
SET merchant_id = '00000000-0000-0000-0000-000000000001'
WHERE merchant_id IS NULL;

ALTER TABLE payments
    ALTER COLUMN merchant_id SET NOT NULL;

CREATE INDEX idx_payments_merchant_id
    ON payments (merchant_id);