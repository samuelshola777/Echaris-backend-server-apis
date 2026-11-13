package com.echaris.api.marketplace.request;

import com.echaris.api.marketplace.MarketplaceFulfillmentStatus;
import jakarta.validation.constraints.NotNull;

public record MarketplaceFulfillmentStatusRequest(
        @NotNull MarketplaceFulfillmentStatus status
) {
}