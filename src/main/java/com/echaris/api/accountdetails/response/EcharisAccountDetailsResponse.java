package com.echaris.api.accountdetails.response;

import com.echaris.api.accountdetails.EcharisAccountDetails;

import java.time.Instant;

public record EcharisAccountDetailsResponse(
        String bankName,
        String accountName,
        String accountNumber,
        Instant effectiveFrom,
        String setByUserId,
        String setByName,
        Long version
) {
    public static EcharisAccountDetailsResponse from(EcharisAccountDetails account) {
        return new EcharisAccountDetailsResponse(
                account.getBankName(),
                account.getAccountName(),
                account.getAccountNumber(),
                account.getEffectiveFrom(),
                account.getSetByUserId(),
                account.getSetByName(),
                account.getVersion()
        );
    }
}