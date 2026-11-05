package com.echaris.api.customer.response;

public record CustomerForgotPasswordResponse(
        String userId,
        String emailAddress,
        String otpExpiresAt
) {
}