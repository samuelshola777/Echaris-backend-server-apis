package com.echaris.api.customer.response;

public record CustomerOtpResponse(
        String userId,
        String emailAddress,
        String otpExpiresAt
) {
}