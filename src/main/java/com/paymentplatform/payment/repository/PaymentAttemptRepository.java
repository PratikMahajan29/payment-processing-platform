package com.paymentplatform.payment.repository;

import com.paymentplatform.payment.domain.model.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    List<PaymentAttempt> findByPaymentIdOrderByAttemptNumberAsc(UUID paymentId);

    Optional<PaymentAttempt> findByPaymentIdAndAttemptNumber(
            UUID paymentId,
            Integer attemptNumber
    );

    Optional<PaymentAttempt> findTopByPaymentIdOrderByAttemptNumberDesc(
            UUID paymentId
    );
}