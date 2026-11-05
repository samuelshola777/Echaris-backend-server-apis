package com.echaris.api.userauthmgt.user.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record PasswordChangeRequest(
        @JsonProperty(required = true) @NotBlank String oldPassword,
        @JsonProperty(required = true) @NotBlank String newPassword
) {
}
