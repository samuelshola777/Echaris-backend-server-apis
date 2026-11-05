package com.echaris.api.commitment;

import com.echaris.api.utils.exception.InvalidInputException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;

public final class CommitmentRules {

    private CommitmentRules() {
    }

    public static long kobo(BigDecimal amount) {
        try {
            long value = amount.movePointRight(2).longValueExact();

            if (value <= 0) {
                throw new ArithmeticException();
            }

            return value;

        } catch (ArithmeticException | NullPointerException exception) {
            throw new InvalidInputException(
                    "Amount must be positive, within range and have at most two decimal places"
            );
        }
    }

    public static BigDecimal monthlyReturn(
            BigDecimal capital,
            BigDecimal percentage
    ) {
        return capital.multiply(percentage)
                .divide(
                        new BigDecimal("100"),
                        2,
                        RoundingMode.HALF_UP
                );
    }

    public static Instant maturity(
            Instant fundedAt,
            int months
    ) {
        return fundedAt.atZone(ZoneId.of("Africa/Lagos"))
                .plusMonths(months)
                .toInstant();
    }

    public static boolean matured(
            Instant maturityAt,
            Instant now
    ) {
        return maturityAt != null
                && !now.isBefore(maturityAt);
    }
}