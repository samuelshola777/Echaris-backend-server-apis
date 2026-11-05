package com.echaris.api.customer.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerCreateRequest(
        @NotBlank
        @Email
        @Size(max = 254)
        String emailAddress,

        @NotBlank
        @Size(min = 5, max = 100)
        String password,

        @Size(max = 100) String firstName,

        @Size(max = 100) String lastName,

        @Pattern(
                regexp = "^\\+?[0-9]{7,15}$",
                message = "phoneNumber must be 7–15 digits, optionally prefixed with '+'"
        )
        String phoneNumber,

        @AssertTrue(message = "You must accept the consent to create an account")
        boolean acceptedConsent,
        String referralCode
) {
}