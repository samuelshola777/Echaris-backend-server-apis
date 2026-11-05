package com.echaris.api.userauthmgt.user.response;

import lombok.Builder;

@Builder
public record UserForgotPasswordResponse(

        String userId
) {
}
