package com.echaris.api.marketplace.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record MarketplaceRateRequest(
        @NotNull @DecimalMin("0.01") @DecimalMax("100.00")
        @Digits(integer = 3, fraction = 2) BigDecimal percentage,
        @Min(0) Long version
) {
}