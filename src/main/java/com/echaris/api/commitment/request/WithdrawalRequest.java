package com.echaris.api.commitment.request;

import com.echaris.api.payment.Transaction;
import com.echaris.api.payment.TransactionPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record WithdrawalRequest(
        @NotNull
        TransactionPurpose purpose,

        @Size(max = 120)
        Set<@NotBlank String> returnIds,

        @NotBlank
        @Pattern(regexp = "[0-9]{3,10}")
        String bankCode,

        @NotBlank
        @Pattern(regexp = "[0-9]{10}")
        String accountNumber
) {
}