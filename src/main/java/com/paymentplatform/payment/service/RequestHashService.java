package com.paymentplatform.payment.service;

import com.paymentplatform.payment.controller.dto.CreatePaymentRequest;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class RequestHashService {

    private final MoneyService moneyService;

    public RequestHashService(MoneyService moneyService) {
        this.moneyService = moneyService;
    }

    public String hash(CreatePaymentRequest request) {

        long amountInMinorUnits =
                moneyService.toMinorUnits(
                        request.amount(),
                        request.currency()
                );

        String normalizedCurrency =
                moneyService.normalizeCurrency(
                        request.currency()
                );

        String canonicalRequest = String.join(
                "|",
                request.orderId().toString(),
                request.customerId().toString(),
                Long.toString(amountInMinorUnits),
                normalizedCurrency
        );

        return sha256(canonicalRequest);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash =
                    digest.digest(
                            value.getBytes(StandardCharsets.UTF_8)
                    );

            StringBuilder result = new StringBuilder();

            for (byte b : hash) {
                result.append(
                        String.format("%02x", b)
                );
            }

            return result.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is not available",
                    e
            );
        }
    }
}