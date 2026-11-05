package com.echaris.api.userauthmgt.user.response;

import com.echaris.api.userauthmgt.userotp.UserOtpType;
import com.fasterxml.jackson.annotation.JsonProperty;

public record UserVerifyOtpRequest(
   @JsonProperty(required = true) String otp,
   @JsonProperty(required = true)  String userEmail,
   @JsonProperty(required = true) UserOtpType userOtpType
) {
}
