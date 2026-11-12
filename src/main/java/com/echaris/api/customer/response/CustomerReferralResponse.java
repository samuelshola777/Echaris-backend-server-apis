package com.echaris.api.customer.response;

import java.time.LocalDateTime;

public record CustomerReferralResponse(
        String customerId,
        LocalDateTime verifiedAt
) {
}