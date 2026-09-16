package com.paymentplatform.payment.repository;

import com.paymentplatform.payment.domain.model.IdempotencyRecord;
import com.paymentplatform.payment.domain.model.IdempotencyRecordId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface IdempotencyRecordRepository
        extends JpaRepository<IdempotencyRecord, IdempotencyRecordId> {

    @Modifying
    @Query(value = """
            INSERT INTO idempotency_records (
                merchant_id,
                idempotency_key,
                request_hash,
                payment_id,
                status,
                created_at,
                updated_at
            )
            VALUES (
                :merchantId,
                :idempotencyKey,
                :requestHash,
                NULL,
                'PROCESSING',
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            ON CONFLICT (merchant_id, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int tryClaim(
            @Param("merchantId") UUID merchantId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash
    );
}