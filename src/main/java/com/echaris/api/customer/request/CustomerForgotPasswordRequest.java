package com.echaris.api.customer.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CustomerForgotPasswordRequest(

        @NotBlank(message = "Email address is required")
        @Email(message = "Enter a valid email address")
        String emailAddress
) {
}