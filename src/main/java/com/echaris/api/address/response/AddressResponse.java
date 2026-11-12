package com.echaris.api.address.response;

import com.echaris.api.address.Address;
import com.echaris.api.address.AddressType;

public record AddressResponse(
        String addressId,
        String userId,
        AddressType addressType,
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
                address.getAddressType() == null ? AddressType.DELIVERY : address.getAddressType(),
                address.getCountry(),
                address.getState(),
                address.getCity(),
                address.getAddressLine(),
                address.getAddressLine2(),
                address.getPostalCode()
        );
    }
}