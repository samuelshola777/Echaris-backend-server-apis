package com.echaris.api.marketplace.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record MarketplaceFinancingRequest(
        @NotBlank @Size(max = 128) String productId,
        @NotNull @DecimalMin("1") @DecimalMax("2147483647")
        @Digits(integer = 10, fraction = 0) BigDecimal quantity,
        @NotBlank @Size(max = 150) String name,
        @NotNull @DecimalMin("50000") @Digits(integer = 15, fraction = 2) BigDecimal capital,
        @Valid @NotNull MarketplaceFulfillmentRequest fulfillment,
        @AssertTrue boolean agreementAccepted
) {
}