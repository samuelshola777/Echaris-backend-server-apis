package com.echaris.api.commitmentduration.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CommitmentDurationRequest(
        @NotNull
        @Positive
        Integer durationMonths,

        @NotNull
        Boolean active
) {
}