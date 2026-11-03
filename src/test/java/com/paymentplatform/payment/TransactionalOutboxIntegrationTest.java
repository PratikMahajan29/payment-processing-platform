package com.paymentplatform.payment;

import com.paymentplatform.payment.domain.enums.PaymentAttemptStatus;
import com.paymentplatform.payment.domain.enums.PaymentStatus;
import com.paymentplatform.payment.domain.model.Payment;
import com.paymentplatform.payment.domain.model.PaymentAttempt;
import com.paymentplatform.payment.repository.OutboxEventRepository;
import com.paymentplatform.payment.repository.PaymentAttemptRepository;
import com.paymentplatform.payment.repository.PaymentRepository;
import com.paymentplatform.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@TestPropertySource(
        properties = "payment.gateway.mock.fail-attempts=0"
)
class TransactionalOutboxIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private OutboxEventRepository outboxEventRepository;

    @Test
    void shouldRollbackPaymentAttemptAndOutboxWhenOutboxPersistenceFails() {

        UUID merchantId =
                UUID.randomUUID();

        UUID orderId =
                UUID.randomUUID();

        UUID customerId =
                UUID.randomUUID();

        String idempotencyKey =
                "outbox-rollback-" + UUID.randomUUID();

        Payment createdPayment =
                paymentService.createPayment(
                        merchantId,
                        orderId,
                        customerId,
                        new BigDecimal("499.50"),
                        "INR",
                        idempotencyKey
                );

        UUID paymentId =
                createdPayment.getPaymentId();

        Payment initialPayment =
                paymentRepository.findById(paymentId)
                        .orElseThrow();

        assertEquals(
                PaymentStatus.CREATED,
                initialPayment.getStatus()
        );

        PaymentAttempt initialAttempt =
                paymentAttemptRepository
                        .findTopByPaymentIdOrderByAttemptNumberDesc(
                                paymentId
                        )
                        .orElseThrow();

        assertEquals(
                PaymentAttemptStatus.PENDING,
                initialAttempt.getStatus()
        );

        /*
         * The gateway succeeds, but writing the outbox event fails.
         */
        doThrow(
                new RuntimeException(
                        "Simulated outbox persistence failure"
                )
        )
                .when(outboxEventRepository)
                .save(
                        org.mockito.ArgumentMatchers.any()
                );

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                paymentService.processPayment(
                                        paymentId
                                )
                );

        assertEquals(
                "Simulated outbox persistence failure",
                exception.getMessage()
        );

        /*
         * The preparation transaction has already committed,
         * therefore the payment remains PENDING after the
         * gateway response transaction rolls back.
         */
        Payment persistedPayment =
                paymentRepository.findById(paymentId)
                        .orElseThrow();

        assertEquals(
                PaymentStatus.PENDING,
                persistedPayment.getStatus()
        );

        /*
         * The attempt update must also have rolled back.
         */
        PaymentAttempt persistedAttempt =
                paymentAttemptRepository
                        .findTopByPaymentIdOrderByAttemptNumberDesc(
                                paymentId
                        )
                        .orElseThrow();

        assertEquals(
                PaymentAttemptStatus.PENDING,
                persistedAttempt.getStatus()
        );

        assertNull(
                persistedAttempt.getGatewayTransactionId()
        );

        assertNull(
                persistedAttempt.getCompletedAt()
        );

        /*
         * No outbox event should exist for this payment.
         */
        Integer outboxCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM outbox_events
                        WHERE aggregate_type = 'PAYMENT'
                          AND aggregate_id = ?
                        """,
                        Integer.class,
                        paymentId
                );

        assertEquals(
                0,
                outboxCount
        );
    }
}