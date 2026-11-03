package com.paymentplatform.payment.service;

import com.paymentplatform.payment.domain.enums.PaymentAttemptStatus;
import com.paymentplatform.payment.domain.enums.PaymentEventType;
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

    @Mock
    private OutboxEventService outboxEventService;

    @InjectMocks
    private PaymentProcessingTransactionService service;

    @Test
    void shouldPrepareInitialProcessing() {

        UUID paymentId = UUID.randomUUID();

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setStatus(PaymentStatus.CREATED);

        PaymentAttempt attempt =
                pendingAttempt(
                        paymentId,
                        1
                );

        when(
                paymentRepository.findByIdForUpdate(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        when(
                paymentAttemptRepository
                        .findTopByPaymentIdOrderByAttemptNumberDesc(paymentId)
        ).thenReturn(
                Optional.of(attempt)
        );

        when(
                paymentRepository.save(payment)
        ).thenReturn(
                payment
        );

        PaymentAttempt result =
                service.prepareInitialProcessing(
                        paymentId
                );

        assertSame(
                attempt,
                result
        );

        assertEquals(
                PaymentStatus.PENDING,
                payment.getStatus()
        );

        verify(
                paymentRepository
        ).findByIdForUpdate(
                paymentId
        );

        verify(
                paymentRepository
        ).save(
                payment
        );

        verifyNoInteractions(
                paymentRetryPolicy
        );

        verifyNoInteractions(
                paymentAttemptService
        );

        verifyNoInteractions(
                outboxEventService
        );
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

        when(
                paymentRepository.findByIdForUpdate(paymentId)
        ).thenReturn(
                Optional.of(payment)
        );

        when(
                paymentAttemptRepository
                        .findTopByPaymentIdOrderByAttemptNumberDesc(paymentId)
        ).thenReturn(
                Optional.of(latestAttempt)
        );

        doNothing()
                .when(paymentRetryPolicy)
                .validateCanRetry(1);

        when(
                paymentAttemptService.createAttempt(
                        paymentId
                )
        ).thenReturn(
                retryAttempt
        );

        when(
                paymentRepository.save(payment)
        ).thenReturn(
                payment
        );

        PaymentAttempt result =
                service.prepareRetryProcessing(
                        paymentId
                );

        assertSame(
                retryAttempt,
                result
        );

        assertEquals(
                PaymentStatus.PENDING,
                payment.getStatus()
        );

        verify(
                paymentRetryPolicy
        ).validateCanRetry(
                1
        );

        verify(
                paymentAttemptService
        ).createAttempt(
                paymentId
        );

        verify(
                paymentRepository
        ).save(
                payment
        );

        verifyNoInteractions(
                outboxEventService
        );
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

        attempt.setAttemptId(
                attemptId
        );

        PaymentGatewayResult gatewayResult =
                PaymentGatewayResult.success(
                        "MOCK-TXN-1"
                );

        when(
                paymentAttemptRepository.findById(
                        attemptId
                )
        ).thenReturn(
                Optional.of(attempt)
        );

        when(
                paymentRepository.findByIdForUpdate(
                        paymentId
                )
        ).thenReturn(
                Optional.of(payment)
        );

        when(
                paymentAttemptRepository.save(
                        attempt
                )
        ).thenReturn(
                attempt
        );

        when(
                paymentRepository.save(
                        payment
                )
        ).thenReturn(
                payment
        );

        Payment result =
                service.completeGatewayAttempt(
                        attemptId,
                        gatewayResult
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

        verify(
                paymentAttemptRepository
        ).save(
                attempt
        );

        verify(
                paymentRepository
        ).save(
                payment
        );

        verify(
                outboxEventService
        ).createPaymentEvent(
                payment,
                PaymentEventType.PAYMENT_SUCCEEDED
        );
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

        attempt.setAttemptId(
                attemptId
        );

        PaymentGatewayResult gatewayResult =
                PaymentGatewayResult.failure(
                        "MOCK_FAILURE",
                        "Gateway failed"
                );

        when(
                paymentAttemptRepository.findById(
                        attemptId
                )
        ).thenReturn(
                Optional.of(attempt)
        );

        when(
                paymentRepository.findByIdForUpdate(
                        paymentId
                )
        ).thenReturn(
                Optional.of(payment)
        );

        when(
                paymentAttemptRepository.save(
                        attempt
                )
        ).thenReturn(
                attempt
        );

        when(
                paymentRepository.save(
                        payment
                )
        ).thenReturn(
                payment
        );

        Payment result =
                service.completeGatewayAttempt(
                        attemptId,
                        gatewayResult
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

        verify(
                paymentAttemptRepository
        ).save(
                attempt
        );

        verify(
                paymentRepository
        ).save(
                payment
        );

        verify(
                outboxEventService
        ).createPaymentEvent(
                payment,
                PaymentEventType.PAYMENT_FAILED
        );
    }

    @Test
    void shouldNotSavePaymentOrAttemptWhenOutboxCreationFails() {

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

        attempt.setAttemptId(
                attemptId
        );

        PaymentGatewayResult gatewayResult =
                PaymentGatewayResult.success(
                        "MOCK-TXN-OUTBOX-FAIL"
                );

        when(
                paymentAttemptRepository.findById(
                        attemptId
                )
        ).thenReturn(
                Optional.of(attempt)
        );

        when(
                paymentRepository.findByIdForUpdate(
                        paymentId
                )
        ).thenReturn(
                Optional.of(payment)
        );

        doThrow(
                new RuntimeException(
                        "Outbox persistence failed"
                )
        ).when(outboxEventService)
                .createPaymentEvent(
                        payment,
                        PaymentEventType.PAYMENT_SUCCEEDED
                );

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                service.completeGatewayAttempt(
                                        attemptId,
                                        gatewayResult
                                )
                );

        assertEquals(
                "Outbox persistence failed",
                exception.getMessage()
        );

        assertEquals(
                PaymentStatus.SUCCEEDED,
                payment.getStatus()
        );

        assertEquals(
                PaymentAttemptStatus.SUCCEEDED,
                attempt.getStatus()
        );

        verify(
                outboxEventService
        ).createPaymentEvent(
                payment,
                PaymentEventType.PAYMENT_SUCCEEDED
        );

        verify(
                paymentAttemptRepository,
                never()
        ).save(
                any(PaymentAttempt.class)
        );

        verify(
                paymentRepository,
                never()
        ).save(
                any(Payment.class)
        );
    }

    @Test
    void shouldRejectInitialProcessingWhenPaymentIsNotCreated() {

        UUID paymentId = UUID.randomUUID();

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setStatus(PaymentStatus.SUCCEEDED);

        when(
                paymentRepository.findByIdForUpdate(
                        paymentId
                )
        ).thenReturn(
                Optional.of(payment)
        );

        assertThrows(
                InvalidPaymentStateException.class,
                () ->
                        service.prepareInitialProcessing(
                                paymentId
                        )
        );

        verify(
                paymentRepository,
                never()
        ).save(
                any(Payment.class)
        );

        verifyNoInteractions(
                outboxEventService
        );
    }

    @Test
    void shouldRejectRetryWhenPaymentIsNotFailed() {

        UUID paymentId = UUID.randomUUID();

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setStatus(PaymentStatus.PENDING);

        when(
                paymentRepository.findByIdForUpdate(
                        paymentId
                )
        ).thenReturn(
                Optional.of(payment)
        );

        assertThrows(
                InvalidPaymentStateException.class,
                () ->
                        service.prepareRetryProcessing(
                                paymentId
                        )
        );

        verifyNoInteractions(
                paymentRetryPolicy
        );

        verifyNoInteractions(
                paymentAttemptService
        );

        verifyNoInteractions(
                outboxEventService
        );
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

        attempt.setAttemptId(
                attemptId
        );

        when(
                paymentAttemptRepository.findById(
                        attemptId
                )
        ).thenReturn(
                Optional.of(attempt)
        );

        when(
                paymentRepository.findByIdForUpdate(
                        paymentId
                )
        ).thenReturn(
                Optional.of(payment)
        );

        assertThrows(
                InvalidPaymentStateException.class,
                () ->
                        service.completeGatewayAttempt(
                                attemptId,
                                PaymentGatewayResult.success(
                                        "MOCK-TXN-1"
                                )
                        )
        );

        verify(
                paymentAttemptRepository,
                never()
        ).save(
                any(PaymentAttempt.class)
        );

        verify(
                paymentRepository,
                never()
        ).save(
                any(Payment.class)
        );

        verifyNoInteractions(
                outboxEventService
        );
    }

    @Test
    void shouldPreparePendingRecovery() {

        UUID paymentId = UUID.randomUUID();

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setStatus(PaymentStatus.PENDING);

        PaymentAttempt attempt =
                pendingAttempt(
                        paymentId,
                        1
                );

        when(
                paymentRepository.findByIdForUpdate(
                        paymentId
                )
        ).thenReturn(
                Optional.of(payment)
        );

        when(
                paymentAttemptRepository
                        .findTopByPaymentIdOrderByAttemptNumberDesc(
                                paymentId
                        )
        ).thenReturn(
                Optional.of(attempt)
        );

        PaymentAttempt result =
                service.preparePendingRecovery(
                        paymentId
                );

        assertSame(
                attempt,
                result
        );

        assertEquals(
                PaymentAttemptStatus.PENDING,
                result.getStatus()
        );

        verify(
                paymentRepository
        ).findByIdForUpdate(
                paymentId
        );

        verify(
                paymentAttemptRepository
        ).findTopByPaymentIdOrderByAttemptNumberDesc(
                paymentId
        );

        verifyNoInteractions(
                paymentRetryPolicy
        );

        verifyNoInteractions(
                paymentAttemptService
        );

        verifyNoInteractions(
                outboxEventService
        );
    }

    @Test
    void shouldRejectRecoveryWhenPaymentIsNotPending() {

        UUID paymentId = UUID.randomUUID();

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setStatus(PaymentStatus.FAILED);

        when(
                paymentRepository.findByIdForUpdate(
                        paymentId
                )
        ).thenReturn(
                Optional.of(payment)
        );

        assertThrows(
                InvalidPaymentStateException.class,
                () ->
                        service.preparePendingRecovery(
                                paymentId
                        )
        );

        verifyNoInteractions(
                paymentAttemptRepository
        );

        verifyNoInteractions(
                outboxEventService
        );
    }

    @Test
    void shouldRejectRecoveryWhenLatestAttemptIsNotPending() {

        UUID paymentId = UUID.randomUUID();

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setStatus(PaymentStatus.PENDING);

        PaymentAttempt attempt =
                pendingAttempt(
                        paymentId,
                        1
                );

        attempt.setStatus(
                PaymentAttemptStatus.FAILED
        );

        when(
                paymentRepository.findByIdForUpdate(
                        paymentId
                )
        ).thenReturn(
                Optional.of(payment)
        );

        when(
                paymentAttemptRepository
                        .findTopByPaymentIdOrderByAttemptNumberDesc(
                                paymentId
                        )
        ).thenReturn(
                Optional.of(attempt)
        );

        assertThrows(
                IllegalStateException.class,
                () ->
                        service.preparePendingRecovery(
                                paymentId
                        )
        );

        verifyNoInteractions(
                outboxEventService
        );
    }

    @Test
    void shouldReturnPaymentWhenGatewayCompletionIsReplayed() {

        UUID paymentId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setStatus(PaymentStatus.SUCCEEDED);

        PaymentAttempt attempt =
                pendingAttempt(
                        paymentId,
                        1
                );

        attempt.setAttemptId(
                attemptId
        );

        attempt.setStatus(
                PaymentAttemptStatus.SUCCEEDED
        );

        attempt.setGateway(
                "MOCK"
        );

        attempt.setGatewayTransactionId(
                "MOCK-TXN-1"
        );

        when(
                paymentAttemptRepository.findById(
                        attemptId
                )
        ).thenReturn(
                Optional.of(attempt)
        );

        when(
                paymentRepository.findByIdForUpdate(
                        paymentId
                )
        ).thenReturn(
                Optional.of(payment)
        );

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

        verify(
                paymentAttemptRepository,
                never()
        ).save(
                any(PaymentAttempt.class)
        );

        verify(
                paymentRepository,
                never()
        ).save(
                any(Payment.class)
        );

        verifyNoInteractions(
                outboxEventService
        );
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