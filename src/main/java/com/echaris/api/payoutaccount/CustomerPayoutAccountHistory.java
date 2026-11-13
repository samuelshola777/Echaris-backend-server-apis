package com.echaris.api.payoutaccount;

import java.io.Serializable;
import java.time.Instant;

public record CustomerPayoutAccountHistory(
        String action,
        CustomerPayoutAccountDetails previousDetails,
        CustomerPayoutAccountDetails newDetails,
        String changedByUserId,
        Instant changedAt
) implements Serializable {
}