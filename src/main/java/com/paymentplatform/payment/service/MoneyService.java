package com.paymentplatform.payment.service;

import com.paymentplatform.payment.exception.InvalidMoneyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

@Service
public class MoneyService {

    public long toMinorUnits(BigDecimal amount, String currencyCode) {
        if (amount == null) {
            throw new InvalidMoneyException("Amount is required");
        }

        if (amount.signum() <= 0) {
            throw new InvalidMoneyException("Amount must be greater than zero");
        }

        Currency currency = resolveCurrency(currencyCode);
        int fractionDigits = currency.getDefaultFractionDigits();

        if (fractionDigits < 0) {
            throw new InvalidMoneyException(
                    "Unsupported currency precision: " + currency.getCurrencyCode()
            );
        }

        try {
            BigDecimal normalizedAmount =
                    amount.setScale(fractionDigits, RoundingMode.UNNECESSARY);

            return normalizedAmount
                    .movePointRight(fractionDigits)
                    .longValueExact();

        } catch (ArithmeticException exception) {
            throw new InvalidMoneyException(
                    "Amount has too many decimal places for "
                            + currency.getCurrencyCode()
                            + ": "
                            + amount
            );
        }
    }

    public BigDecimal toMajorUnits(long minorUnits, String currencyCode) {
        Currency currency = resolveCurrency(currencyCode);

        int fractionDigits = currency.getDefaultFractionDigits();

        if (fractionDigits < 0) {
            throw new InvalidMoneyException(
                    "Unsupported currency precision: "
                            + currency.getCurrencyCode()
            );
        }

        return BigDecimal.valueOf(minorUnits, fractionDigits);
    }

    public String normalizeCurrency(String currencyCode) {
        return resolveCurrency(currencyCode).getCurrencyCode();
    }

    private Currency resolveCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new InvalidMoneyException("Currency is required");
        }

        try {
            return Currency.getInstance(
                    currencyCode.trim().toUpperCase()
            );
        } catch (IllegalArgumentException exception) {
            throw new InvalidMoneyException(
                    "Unsupported currency: " + currencyCode
            );
        }
    }
}