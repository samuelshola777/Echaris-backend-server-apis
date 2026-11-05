package com.echaris.api.address.response;

import com.echaris.api.address.Address;

public record AddressResponse(
        String addressId,
        String userId,
        String country,
        String state,
        String city,
        String addressLine,
        String addressLine2,
        String postalCode
) {

    public static AddressResponse from(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getUserId(),
                address.getCountry(),
                address.getState(),
                address.getCity(),
                address.getAddressLine(),
                address.getAddressLine2(),
                address.getPostalCode()
        );
    }
}