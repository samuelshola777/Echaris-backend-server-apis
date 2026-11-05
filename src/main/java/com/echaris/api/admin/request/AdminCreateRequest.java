package com.echaris.api.admin.request;


import com.echaris.api.utils.Gender;

public record AdminCreateRequest(
        String firstName,
        String lastName,
        String emailAddress,
        String password,
        String phoneNumber,
        String profilePictureUrl,
        String profileColourCode,
        Gender gender
) {
}