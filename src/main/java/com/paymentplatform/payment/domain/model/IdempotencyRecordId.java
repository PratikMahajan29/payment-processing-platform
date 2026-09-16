package com.paymentplatform.payment.domain.model;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class IdempotencyRecordId implements Serializable {

    private UUID merchantId;

    private String idempotencyKey;

    public IdempotencyRecordId() {
    }

    public IdempotencyRecordId(UUID merchantId, String idempotencyKey) {
        this.merchantId = merchantId;
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(UUID merchantId) {
        this.merchantId = merchantId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof IdempotencyRecordId that)) {
            return false;
        }

        return Objects.equals(merchantId, that.merchantId)
                && Objects.equals(idempotencyKey, that.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(merchantId, idempotencyKey);
    }
}