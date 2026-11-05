package com.echaris.api.customer.request;

import com.echaris.api.utils.Gender;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.LocalDate;
@Builder
public record CustomerUpdateRequest(

        @Size(max = 100)
        String firstName,

        @Size(max = 100)
        String lastName,

        @Size(max = 30)
        String phoneNumber,

        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth,

        Gender gender,

        @Size(max = 500)
        String profilePictureUrl,

        Boolean marketingEmailsEnabled
) {
}