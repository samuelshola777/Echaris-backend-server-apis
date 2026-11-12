package com.echaris.api.accountdetails.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record EcharisAccountDetailsRequest(
        @NotBlank @Size(max = 120)
        String bankName,

        @NotBlank @Size(max = 120)
        String accountName,

        @NotBlank @Pattern(regexp = "[0-9]{10}")
        String accountNumber,

        @PositiveOrZero
        Long version
) {
}