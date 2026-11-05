package com.echaris.api.admin.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AdminResetPasswordRequest(
        @NotBlank @Email String emailAddress,
        @NotBlank String otp,
        @NotBlank String newPassword
) {
}