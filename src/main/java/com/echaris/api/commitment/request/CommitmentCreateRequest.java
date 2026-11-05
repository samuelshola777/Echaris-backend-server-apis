package com.echaris.api.commitment.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CommitmentCreateRequest(
        @NotBlank
        @Size(max = 100)
        String name,

        @NotNull
        @DecimalMin("50000.00")
        BigDecimal capital,

        int durationMonths,

        @AssertTrue
        boolean acceptedAgreement
) {
}