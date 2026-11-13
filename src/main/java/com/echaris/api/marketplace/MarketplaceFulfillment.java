package com.echaris.api.marketplace;

import com.echaris.api.address.AddressType;

public record MarketplaceFulfillment(
        AddressType type,
        String pickupLocationId,
        String address,
        String phoneNumber
) {
}