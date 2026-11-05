package com.echaris.api.userauthmgt.user.request;

import com.echaris.api.utils.Gender;
import com.fasterxml.jackson.annotation.JsonProperty;



public record UserAuthRequest(
        @JsonProperty(required = true) String emailAddress,
        @JsonProperty(required = true) String password,
        Gender gender
) {
}
