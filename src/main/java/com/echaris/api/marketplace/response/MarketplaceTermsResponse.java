package com.echaris.api.marketplace.response;

import java.math.BigDecimal;

public record MarketplaceTermsResponse(
        String agreementVersion,
        BigDecimal monthlyRate,
        BigDecimal minimumCapital,
        int productReleasePercent,
        int capitalPayoutPercent
) {
}