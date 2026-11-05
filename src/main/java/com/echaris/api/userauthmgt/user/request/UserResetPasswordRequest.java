package com.echaris.api.userauthmgt.user.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record UserResetPasswordRequest(
        @JsonProperty(required = true) @NotBlank String otp,
        @JsonProperty(required = true) @NotBlank String newPassword,
        @JsonProperty(required = true) @NotBlank String emailAddress
) {
}
