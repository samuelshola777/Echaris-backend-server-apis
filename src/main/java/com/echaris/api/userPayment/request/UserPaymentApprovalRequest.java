package com.echaris.api.userPayment.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record UserPaymentApprovalRequest(
        @NotBlank @Size(max = 128) String bankCreditId,
        @NotNull BigDecimal verifiedAmount,
        @NotNull @PastOrPresent Instant paidAt
) {
}