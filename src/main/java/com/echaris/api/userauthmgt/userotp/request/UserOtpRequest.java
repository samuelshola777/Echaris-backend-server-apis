package com.echaris.api.userauthmgt.userotp.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record UserOtpRequest(
        @JsonProperty(required = true) @NotBlank String userId
) {
}
