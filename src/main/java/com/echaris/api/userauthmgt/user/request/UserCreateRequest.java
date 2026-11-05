package com.echaris.api.userauthmgt.user.request;

import com.echaris.api.userauthmgt.user.UserType;
import com.echaris.api.utils.Gender;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;

import java.time.LocalDate;

@Builder
public record UserCreateRequest(

        @JsonProperty(required = true) String firstName,
        @JsonProperty(required = true) String lastName,
        @JsonProperty(required = true) String emailAddress,
        @JsonProperty(required = true) String password,
        @JsonProperty(required = true) UserType userType,
        @JsonProperty(required = true) String profileColourCode,
        String profilePictureUrl,
        LocalDate dateOfBirth,
        String phoneNumber,
        Gender gender

                ) {
}
