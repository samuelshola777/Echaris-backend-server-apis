package com.echaris.api.customer.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CustomerKycReviewRequest(
        @NotNull(message = "Submission version is required")
        @Min(value = 0, message = "Submission version cannot be negative")
        Long submissionVersion,

        @Size(
                max = 1000,
                message = "Rejection reason must not exceed 1000 characters"
        )
        String rejectionReason
) {
}