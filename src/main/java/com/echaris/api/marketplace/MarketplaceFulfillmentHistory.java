package com.echaris.api.marketplace;

import java.time.Instant;

public record MarketplaceFulfillmentHistory(
        MarketplaceFulfillmentStatus previousStatus,
        MarketplaceFulfillmentStatus newStatus,
        String changedByUserId,
        Instant changedAt
) {
}