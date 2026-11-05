package com.echaris.api.admin.request;

public record AdminLoginRequest(
        String emailAddress,
        String password
) {
}