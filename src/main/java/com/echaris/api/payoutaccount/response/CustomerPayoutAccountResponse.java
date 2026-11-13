package com.echaris.api.payoutaccount.response;

import com.echaris.api.payoutaccount.CustomerPayoutAccount;
import com.echaris.api.payoutaccount.CustomerPayoutAccountDetails;

import java.time.LocalDateTime;

public record CustomerPayoutAccountResponse(
        CustomerPayoutAccountDetails details,
        Long version,
        LocalDateTime updatedAt
) {
    public static CustomerPayoutAccountResponse from(CustomerPayoutAccount account) {
        return new CustomerPayoutAccountResponse(
                account.getDetails(), account.getVersion(), account.getUpdatedAt()
        );
    }
}