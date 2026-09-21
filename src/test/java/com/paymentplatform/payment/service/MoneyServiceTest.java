package com.paymentplatform.payment.service;

import com.paymentplatform.payment.exception.InvalidMoneyException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyServiceTest {

    private final MoneyService moneyService = new MoneyService();

    @Test
    void shouldConvertInrAmountToPaise() {
        long result = moneyService.toMinorUnits(
                new BigDecimal("499.50"),
                "INR"
        );

        assertEquals(49950L, result);
    }

    @Test
    void shouldTreatTrailingZerosAsEquivalent() {
        long first = moneyService.toMinorUnits(
                new BigDecimal("499.50"),
                "INR"
        );

        long second = moneyService.toMinorUnits(
                new BigDecimal("499.500"),
                "INR"
        );

        assertEquals(first, second);
    }

    @Test
    void shouldRejectTooManyDecimalPlacesForInr() {
        InvalidMoneyException exception =
                assertThrows(
                        InvalidMoneyException.class,
                        () -> moneyService.toMinorUnits(
                                new BigDecimal("499.505"),
                                "INR"
                        )
                );

        assertTrue(
                exception.getMessage()
                        .contains("too many decimal places")
        );
    }

    @Test
    void shouldConvertMinorUnitsBackToMajorUnits() {
        BigDecimal result =
                moneyService.toMajorUnits(49950L, "INR");

        assertEquals(
                new BigDecimal("499.50"),
                result
        );
    }

    @Test
    void shouldSupportZeroDecimalCurrency() {
        long result =
                moneyService.toMinorUnits(
                        new BigDecimal("500"),
                        "JPY"
                );

        assertEquals(500L, result);
    }

    @Test
    void shouldRejectFractionalAmountForZeroDecimalCurrency() {
        assertThrows(
                InvalidMoneyException.class,
                () -> moneyService.toMinorUnits(
                        new BigDecimal("500.50"),
                        "JPY"
                )
        );
    }

    @Test
    void shouldSupportThreeDecimalCurrency() {
        long result =
                moneyService.toMinorUnits(
                        new BigDecimal("1.234"),
                        "KWD"
                );

        assertEquals(1234L, result);
    }

    @Test
    void shouldNormalizeCurrency() {
        assertEquals(
                "INR",
                moneyService.normalizeCurrency(" inr ")
        );
    }

    @Test
    void shouldRejectUnsupportedCurrency() {
        assertThrows(
                InvalidMoneyException.class,
                () -> moneyService.toMinorUnits(
                        new BigDecimal("100.00"),
                        "XYZ"
                )
        );
    }
}