package com.paymentplatform.payment.service;

import com.paymentplatform.payment.domain.enums.PaymentAttemptStatus;
import com.paymentplatform.payment.domain.model.Payment;
import com.paymentplatform.payment.domain.model.PaymentAttempt;
import com.paymentplatform.payment.repository.PaymentAttemptRepository;
import com.paymentplatform.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentAttemptServiceTest {

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    @InjectMocks
    private PaymentAttemptService paymentAttemptService;

    @Mock
    private PaymentRepository paymentRepository;

    @Test
    void shouldCreateFirstAttempt() {
        UUID paymentId = UUID.randomUUID();

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);

        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.of(payment));

        when(paymentAttemptRepository
                .findTopByPaymentIdOrderByAttemptNumberDesc(paymentId))
                .thenReturn(Optional.empty());

        when(paymentAttemptRepository.save(any(PaymentAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentAttempt result = paymentAttemptService.createAttempt(paymentId);

        assertEquals(paymentId, result.getPaymentId());
        assertEquals(1, result.getAttemptNumber());
        assertEquals(PaymentAttemptStatus.PENDING, result.getStatus());

        verify(paymentAttemptRepository)
                .findTopByPaymentIdOrderByAttemptNumberDesc(paymentId);

        verify(paymentAttemptRepository)
                .save(any(PaymentAttempt.class));
    }

    @Test
    void shouldCreateNextAttemptNumber() {
        UUID paymentId = UUID.randomUUID();

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);

        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.of(payment));

        PaymentAttempt previousAttempt = new PaymentAttempt();
        previousAttempt.setAttemptNumber(2);

        when(paymentAttemptRepository
                .findTopByPaymentIdOrderByAttemptNumberDesc(paymentId))
                .thenReturn(Optional.of(previousAttempt));

        when(paymentAttemptRepository.save(any(PaymentAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentAttempt result = paymentAttemptService.createAttempt(paymentId);

        assertEquals(3, result.getAttemptNumber());
        assertEquals(PaymentAttemptStatus.PENDING, result.getStatus());
    }
}