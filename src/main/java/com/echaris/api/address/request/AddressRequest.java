package com.echaris.api.address.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank(message = "User ID is required")
        String userId,

        @Size(max = 100) String country,
        @Size(max = 100) String state,
        @Size(max = 100) String city,
        @Size(max = 500) String addressLine,
        @Size(max = 500) String addressLine2,
        @Size(max = 500) String postalCode
) {
}