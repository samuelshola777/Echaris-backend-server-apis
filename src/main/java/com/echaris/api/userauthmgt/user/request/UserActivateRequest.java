package com.echaris.api.userauthmgt.user.request;

public record UserActivateRequest(

        String otp,
        String userId
) {
}
