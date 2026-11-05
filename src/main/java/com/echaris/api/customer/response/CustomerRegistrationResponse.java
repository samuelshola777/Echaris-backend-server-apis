package com.echaris.api.customer.response;

public record CustomerRegistrationResponse(
        CustomerResponse customer,
        String otpExpiresAt
) {
}