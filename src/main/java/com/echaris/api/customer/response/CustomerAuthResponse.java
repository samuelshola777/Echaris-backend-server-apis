package com.echaris.api.customer.response;

public record CustomerAuthResponse(
        String accessToken,
        String refreshToken,
        CustomerResponse customer
) {
}