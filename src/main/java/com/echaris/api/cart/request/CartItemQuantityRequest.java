package com.echaris.api.cart.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CartItemQuantityRequest(
        @NotNull
        @DecimalMin("1")
        @DecimalMax("2147483647")
        @Digits(integer = 10, fraction = 0)
        BigDecimal quantity
) {
}