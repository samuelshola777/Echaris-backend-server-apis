package com.echaris.api.cart.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CartItemQuantityRequest(
        @NotBlank
        @Size(max = 128)
        String productId,

        @NotNull
        @DecimalMin("1")
        @DecimalMax("2147483647")
        @Digits(integer = 10, fraction = 0)
        BigDecimal quantity
) {
}