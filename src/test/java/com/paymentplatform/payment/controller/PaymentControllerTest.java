package com.paymentplatform.payment.controller;

import com.paymentplatform.payment.domain.enums.PaymentStatus;
import com.paymentplatform.payment.domain.model.Payment;
import com.paymentplatform.payment.exception.GlobalExceptionHandler;
import com.paymentplatform.payment.service.MoneyService;
import com.paymentplatform.payment.service.PaymentAttemptService;
import com.paymentplatform.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.*;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.paymentplatform.security.SecurityConfig;
import org.springframework.context.annotation.Import;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private PaymentAttemptService paymentAttemptService;

    @MockitoBean
    private MoneyService moneyService;

    @Test
    void shouldReturnPaymentById() throws Exception {

        UUID paymentId =
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        UUID merchantId =
                UUID.fromString("11111111-1111-1111-1111-111111111111");

        UUID orderId =
                UUID.fromString("22222222-2222-2222-2222-222222222222");

        UUID customerId =
                UUID.fromString("33333333-3333-3333-3333-333333333333");

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setMerchantId(merchantId);
        payment.setOrderId(orderId);
        payment.setCustomerId(customerId);
        payment.setAmount(49950L);
        payment.setCurrency("INR");
        payment.setStatus(PaymentStatus.CREATED);
        payment.setIdempotencyKey("payment-test-key");
        payment.setCreatedAt(OffsetDateTime.parse("2026-09-21T10:00:00Z"));
        payment.setUpdatedAt(OffsetDateTime.parse("2026-09-21T10:00:00Z"));

        when(paymentService.getPayment(paymentId))
                .thenReturn(payment);

        when(moneyService.toMajorUnits(49950L, "INR"))
                .thenReturn(new java.math.BigDecimal("499.50"));

        mockMvc.perform(
                        get("/api/v1/payments/{paymentId}", paymentId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.amount").value(499.50))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.idempotencyKey").value("payment-test-key"));
    }

    @Test
    void shouldReturn404WhenPaymentDoesNotExist() throws Exception {

        UUID paymentId =
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        when(paymentService.getPayment(paymentId))
                .thenThrow(
                        new com.paymentplatform.payment.exception.PaymentNotFoundException(
                                "Payment not found: " + paymentId
                        )
                );

        mockMvc.perform(
                        get("/api/v1/payments/{paymentId}", paymentId)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"))
                .andExpect(
                        jsonPath("$.message")
                                .value("Payment not found: " + paymentId)
                );
    }
}