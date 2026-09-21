package com.paymentplatform.payment.service;

import com.paymentplatform.payment.domain.enums.PaymentAttemptStatus;
import com.paymentplatform.payment.domain.enums.PaymentStatus;
import com.paymentplatform.payment.domain.model.Payment;
import com.paymentplatform.payment.domain.model.PaymentAttempt;
import com.paymentplatform.payment.exception.InvalidPaymentStateException;
import com.paymentplatform.payment.gateway.PaymentGatewayResult;
import com.paymentplatform.payment.repository.PaymentAttemptRepository;
import com.paymentplatform.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentProcessingTransactionServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    @Mock
    private PaymentAttemptService paymentAttemptService;

    @Spy
    private PaymentStateMachine paymentStateMachine =
            new PaymentStateMachine();

    @Mock
    private PaymentRetryPolicy paymentRetryPolicy;

    @InjectMocks
    private PaymentProcessingTransactionService service;

    @Test
    void shouldPrepareInitialProcessing() {

        UUID paymentId = UUID.randomUUID();

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setStatus(PaymentStatus.CREATED);

        PaymentAttempt attempt = pendingAttempt(
                paymentId,
                1
        );

        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.of(payment));

        when(paymentAttemptRepository
                .findTopByPaymentIdOrderByAttemptNumberDesc(paymentId))
                .thenReturn(Optional.of(attempt));

        when(paymentRepository.save(payment))
                .thenReturn(payment);

        PaymentAttempt result =
                service.prepareInitialProcessing(paymentId);

        assertSame(attempt, result);

        assertEquals(
                PaymentStatus.PENDING,
                payment.getStatus()
        );

        verify(paymentRepository)
                .findByIdForUpdate(paymentId);

        verify(paymentRepository)
                .save(payment);

        verifyNoInteractions(paymentRetryPolicy);
        verifyNoInteractions(paymentAttemptService);
    }

    @Test
    void shouldPrepareRetryProcessing() {

        UUID paymentId = UUID.randomUUID();

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setStatus(PaymentStatus.FAILED);

        PaymentAttempt latestAttempt =
                pendingAttempt(
                        paymentId,
                        1
                );

        latestAttempt.setStatus(
                PaymentAttemptStatus.FAILED
        );

        PaymentAttempt retryAttempt =
                pendingAttempt(
                        paymentId,
                        2
                );

        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.of(payment));

        when(paymentAttemptRepository
                .findTopByPaymentIdOrderByAttemptNumberDesc(paymentId))
                .thenReturn(Optional.of(latestAttempt));

        doNothing()
                .when(paymentRetryPolicy)
                .validateCanRetry(1);

        when(paymentAttemptService.createAttempt(paymentId))
                .thenReturn(retryAttempt);

        when(paymentRepository.save(payment))
                .thenReturn(payment);

        PaymentAttempt result =
                service.prepareRetryProcessing(paymentId);

        assertSame(
                retryAttempt,
                result
        );

        assertEquals(
                PaymentStatus.PENDING,
                payment.getStatus()
        );

        verify(paymentRetryPolicy)
                .validateCanRetry(1);

        verify(paymentAttemptService)
                .createAttempt(paymentId);

        verify(paymentRepository)
                .save(payment);
    }

    @Test
    void shouldCompleteSuccessfulGatewayAttempt() {

        UUID paymentId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setStatus(PaymentStatus.PENDING);

        PaymentAttempt attempt =
                pendingAttempt(
                        paymentId,
                        1
                );

        attempt.setAttemptId(attemptId);

        when(paymentAttemptRepository.findById(attemptId))
                .thenReturn(Optional.of(attempt));

        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.of(payment));

        when(paymentAttemptRepository.save(attempt))
                .thenReturn(attempt);

        when(paymentRepository.save(payment))
                .thenReturn(payment);

        Payment result =
                service.completeGatewayAttempt(
                        attemptId,
                        PaymentGatewayResult.success(
                                "MOCK-TXN-1"
                        )
                );

        assertSame(
                payment,
                result
        );

        assertEquals(
                PaymentAttemptStatus.SUCCEEDED,
                attempt.getStatus()
        );

        assertEquals(
                "MOCK",
                attempt.getGateway()
        );

        assertEquals(
                "MOCK-TXN-1",
                attempt.getGatewayTransactionId()
        );

        assertNull(
                attempt.getFailureCode()
        );

        assertNull(
                attempt.getFailureMessage()
        );

        assertNotNull(
                attempt.getCompletedAt()
        );

        assertEquals(
                PaymentStatus.SUCCEEDED,
                payment.getStatus()
        );

        verify(paymentAttemptRepository)
                .save(attempt);

        verify(paymentRepository)
                .save(payment);
    }

    @Test
    void shouldCompleteFailedGatewayAttempt() {

        UUID paymentId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setStatus(PaymentStatus.PENDING);

        PaymentAttempt attempt =
                pendingAttempt(
                        paymentId,
                        1
                );

        attempt.setAttemptId(attemptId);

        when(paymentAttemptRepository.findById(attemptId))
                .thenReturn(Optional.of(attempt));

        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.of(payment));

        when(paymentAttemptRepository.save(attempt))
                .thenReturn(attempt);

        when(paymentRepository.save(payment))
                .thenReturn(payment);

        Payment result =
                service.completeGatewayAttempt(
                        attemptId,
                        PaymentGatewayResult.failure(
                                "MOCK_FAILURE",
                                "Gateway failed"
                        )
                );

        assertSame(
                payment,
                result
        );

        assertEquals(
                PaymentAttemptStatus.FAILED,
                attempt.getStatus()
        );

        assertEquals(
                "MOCK",
                attempt.getGateway()
        );

        assertNull(
                attempt.getGatewayTransactionId()
        );

        assertEquals(
                "MOCK_FAILURE",
                attempt.getFailureCode()
        );

        assertEquals(
                "Gateway failed",
                attempt.getFailureMessage()
        );

        assertNotNull(
                attempt.getCompletedAt()
        );

        assertEquals(
                PaymentStatus.FAILED,
                payment.getStatus()
        );
    }

    @Test
    void shouldRejectInitialProcessingWhenPaymentIsNotCreated() {

        UUID paymentId = UUID.randomUUID();

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setStatus(PaymentStatus.SUCCEEDED);

        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.of(payment));

        assertThrows(
                InvalidPaymentStateException.class,
                () -> service.prepareInitialProcessing(paymentId)
        );

        verify(paymentRepository, never())
                .save(any(Payment.class));
    }

    @Test
    void shouldRejectRetryWhenPaymentIsNotFailed() {

        UUID paymentId = UUID.randomUUID();

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setStatus(PaymentStatus.PENDING);

        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.of(payment));

        assertThrows(
                InvalidPaymentStateException.class,
                () -> service.prepareRetryProcessing(paymentId)
        );

        verifyNoInteractions(paymentRetryPolicy);
        verifyNoInteractions(paymentAttemptService);
    }

    @Test
    void shouldRejectGatewayCompletionWhenPaymentIsNotPending() {

        UUID paymentId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setStatus(PaymentStatus.FAILED);

        PaymentAttempt attempt =
                pendingAttempt(
                        paymentId,
                        1
                );

        attempt.setAttemptId(attemptId);

        when(paymentAttemptRepository.findById(attemptId))
                .thenReturn(Optional.of(attempt));

        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.of(payment));

        assertThrows(
                InvalidPaymentStateException.class,
                () -> service.completeGatewayAttempt(
                        attemptId,
                        PaymentGatewayResult.success(
                                "MOCK-TXN-1"
                        )
                )
        );

        verify(paymentAttemptRepository, never())
                .save(any(PaymentAttempt.class));

        verify(paymentRepository, never())
                .save(any(Payment.class));
    }

    private PaymentAttempt pendingAttempt(
            UUID paymentId,
            int attemptNumber
    ) {

        PaymentAttempt attempt =
                new PaymentAttempt();

        attempt.setAttemptId(
                UUID.randomUUID()
        );

        attempt.setPaymentId(
                paymentId
        );

        attempt.setAttemptNumber(
                attemptNumber
        );

        attempt.setStatus(
                PaymentAttemptStatus.PENDING
        );

        return attempt;
    }
}