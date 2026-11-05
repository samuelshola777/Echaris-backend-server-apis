package com.echaris.api.admin.response;



import com.echaris.api.userauthmgt.user.UserType;
import com.echaris.api.utils.Gender;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdminResponse(
        String adminId,
        String userId,

        String firstName,
        String lastName,
        String emailAddress,
        String phoneNumber,
        String profilePictureUrl,
        LocalDate dateOfBirth,
        Gender gender,
        String age,
        UserType userType,
        boolean active,
        boolean userActive,
        boolean userVerified,

        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime lastLoginAt,
        boolean superAdmin
) {
}