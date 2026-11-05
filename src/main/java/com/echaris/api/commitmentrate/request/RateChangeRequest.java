package com.echaris.api.commitmentrate.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RateChangeRequest(
        @NotNull
        @DecimalMin("0.01")
        @DecimalMax("100.00")
        @Digits(integer = 3, fraction = 2)
        BigDecimal percentage,

        @NotNull
        Long version
) {
}