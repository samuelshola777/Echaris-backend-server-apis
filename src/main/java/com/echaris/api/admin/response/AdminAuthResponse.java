package com.echaris.api.admin.response;

public record AdminAuthResponse(
        String accessToken,
        String refreshToken,
        AdminResponse admin
) {
}