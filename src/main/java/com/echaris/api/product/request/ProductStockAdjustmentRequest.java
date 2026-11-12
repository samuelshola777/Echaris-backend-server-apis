package com.echaris.api.product.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProductStockAdjustmentRequest(
        @NotNull
        @Digits(integer = 10, fraction = 0)
        @DecimalMin("-2147483648")
        @DecimalMax("2147483647")
        BigDecimal quantityChange
) {
}