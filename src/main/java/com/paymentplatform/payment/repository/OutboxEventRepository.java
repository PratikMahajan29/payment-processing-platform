package com.paymentplatform.payment.repository;

import com.paymentplatform.payment.domain.enums.OutboxEventStatus;
import com.paymentplatform.payment.domain.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository
        extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(
            OutboxEventStatus status
    );

    @Query(
            value = """
                    SELECT *
                    FROM outbox_events
                    WHERE status = 'NEW'
                    ORDER BY created_at ASC
                    LIMIT :batchSize
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true
    )
    List<OutboxEvent> findNewForUpdateSkipLocked(
            @Param("batchSize") int batchSize
    );
}