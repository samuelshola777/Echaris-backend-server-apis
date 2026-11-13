package com.echaris.api.userPayment.request;

import com.echaris.api.userPayment.UserPaymentPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record UserPaymentRequest(
        @NotNull UserPaymentPurpose purpose,
        @NotBlank @Size(max = 128) String targetId,
        @NotNull BigDecimal amount,
        @NotNull @Size(min = 1, max = 10)
        List<@NotBlank @Size(max = 2048) String> receiptUrls,
        @Size(max = 128) String transferReference,
        @NotBlank @Size(max = 120) String bankName,
        @NotBlank @Size(max = 120) String accountName,
        @NotBlank @Pattern(regexp = "[0-9]{10}") String accountNumber
) {
}