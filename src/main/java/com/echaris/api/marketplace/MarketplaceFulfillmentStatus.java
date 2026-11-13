package com.echaris.api.marketplace;

import com.echaris.api.address.AddressType;

public enum MarketplaceFulfillmentStatus {
    AWAITING_RELEASE,
    READY_FOR_FULFILLMENT,
    PREPARING,
    PACKAGED,
    IN_TRANSIT,
    DELIVERED,
    READY_FOR_PICKUP,
    COLLECTED;

    boolean allowsCustomerUpdate() {
        return this == AWAITING_RELEASE || this == READY_FOR_FULFILLMENT;
    }

    boolean canChangeTo(MarketplaceFulfillmentStatus next, AddressType type) {
        return switch (this) {
            case READY_FOR_FULFILLMENT -> next == PREPARING;
            case PREPARING -> next == PACKAGED;
            case PACKAGED -> type == AddressType.DELIVERY ? next == IN_TRANSIT : next == READY_FOR_PICKUP;
            case IN_TRANSIT -> next == DELIVERED;
            case READY_FOR_PICKUP -> next == COLLECTED;
            default -> false;
        };
    }
}