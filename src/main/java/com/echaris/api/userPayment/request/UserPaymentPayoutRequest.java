package com.echaris.api.userPayment.request;

import com.echaris.api.userPayment.UserPaymentPayoutType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record UserPaymentPayoutRequest(
        @NotBlank @Size(max = 128) String customerUserId,
        @NotNull @Min(0) Long payoutAccountVersion,
        @NotNull UserPaymentPayoutType payoutType,
        @NotBlank @Size(max = 128) String targetId,
        @NotNull BigDecimal amount,
        @NotNull @Size(min = 1, max = 10)
        List<@NotBlank @Size(max = 2048) String> receiptUrls,
        @NotBlank @Size(max = 128) String transferReference,
        @NotNull @PastOrPresent Instant paidAt
) {
}