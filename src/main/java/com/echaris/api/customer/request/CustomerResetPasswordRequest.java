package com.echaris.api.customer.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerResetPasswordRequest(

        @NotBlank(message = "Email address is required")
        @Email(message = "Enter a valid email address")
        String emailAddress,

        @NotBlank(message = "OTP is required")
        @Pattern(
                regexp = "^\\d{6}$",
                message = "OTP must contain 6 digits"
        )
        String otp,

        @NotBlank(message = "New password is required")
        @Size(
                min = 8,
                max = 100,
                message = "Password must contain at least 8 characters"
        )
        String newPassword
) {
}