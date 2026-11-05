package com.echaris.api.customer.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CustomerVerifyOtpRequest(

        @NotBlank(message = "Email address is required") @Email(message = "Enter a valid email address") String emailAddress,

        @NotBlank(message = "OTP is required") @Pattern(regexp = "^\\d{6}$", message = "OTP must contain 6 digits") String otp) {
}