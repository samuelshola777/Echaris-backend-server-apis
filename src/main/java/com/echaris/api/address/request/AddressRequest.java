package com.echaris.api.address.request;

import com.echaris.api.address.AddressType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @Size(max = 24) String userId,
        @NotNull AddressType addressType,
        @NotBlank @Size(max = 100) String country,
        @NotBlank @Size(max = 100) String state,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 500) String addressLine,
        @Size(max = 500) String addressLine2,
        @Size(max = 500) String postalCode
) {
}