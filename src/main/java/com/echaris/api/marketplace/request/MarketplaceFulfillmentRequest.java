package com.echaris.api.marketplace.request;

import com.echaris.api.address.AddressType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MarketplaceFulfillmentRequest(
        @NotNull AddressType type,
        @Size(max = 128) String pickupLocationId,
        @Size(max = 500) String deliveryAddress,
        @Size(max = 32) String phoneNumber
) {
}