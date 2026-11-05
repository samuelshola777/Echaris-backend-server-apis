package com.echaris.api.admin.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminAccessRequest(
        @NotNull(message = "Active status is required")
        Boolean active,

        @NotBlank(message = "A reason is required")
        @Size(max = 500, message = "Reason must not exceed 500 characters")
        String reason
) {
}