package com.echaris.api.marketplace.response;

import com.echaris.api.userPayment.UserPaymentPurpose;

import java.math.BigDecimal;

public record MarketplacePaymentInstructionsResponse(
        String targetId,
        UserPaymentPurpose purpose,
        BigDecimal amount,
        String bankName,
        String accountName,
        String accountNumber
) {
}