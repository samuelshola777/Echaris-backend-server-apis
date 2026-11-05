package com.echaris.api.admin.request;

import com.echaris.api.utils.Gender;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
public record AdminUpdateRequest(
        @Size(min = 1, max = 80) String firstName,
        @Size(min = 1, max = 80) String lastName,
        @Pattern(regexp = "\\+?[0-9]{7,15}") String phoneNumber,
        @Size(max = 500) String profilePictureUrl,
        @Past LocalDate dateOfBirth, Gender gender) {}