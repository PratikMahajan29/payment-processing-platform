package com.paymentplatform.payment.service;

import com.paymentplatform.payment.domain.enums.PaymentStatus;
import com.paymentplatform.payment.domain.model.Payment;
import com.paymentplatform.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void shouldCreatePayment() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        when(paymentRepository.findByIdempotencyKey("key-123"))
                .thenReturn(Optional.empty());

        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Payment result = paymentService.createPayment(
                orderId,
                customerId,
                49950L,
                "INR",
                "key-123"
        );

        assertEquals(orderId, result.getOrderId());
        assertEquals(customerId, result.getCustomerId());
        assertEquals(49950L, result.getAmount());
        assertEquals("INR", result.getCurrency());
        assertEquals(PaymentStatus.CREATED, result.getStatus());

        verify(paymentRepository).findByIdempotencyKey("key-123");
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void shouldReturnExistingPaymentForSameIdempotencyKey() {
        Payment existingPayment = new Payment();
        existingPayment.setPaymentId(UUID.randomUUID());
        existingPayment.setStatus(PaymentStatus.CREATED);

        when(paymentRepository.findByIdempotencyKey("key-123"))
                .thenReturn(Optional.of(existingPayment));

        Payment result = paymentService.createPayment(
                UUID.randomUUID(),
                UUID.randomUUID(),
                49950L,
                "INR",
                "key-123"
        );

        assertEquals(existingPayment.getPaymentId(), result.getPaymentId());

        verify(paymentRepository).findByIdempotencyKey("key-123");
        verify(paymentRepository, never()).save(any(Payment.class));
    }
}