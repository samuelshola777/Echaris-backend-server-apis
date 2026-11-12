package com.echaris.api.accountdetails;

import java.io.Serializable;
import java.time.Instant;

public record EcharisAccountDetailsHistory(
        String previousBankName,
        String previousAccountName,
        String previousAccountNumber,
        Instant previousEffectiveFrom,
        String previousSetByUserId,
        String previousSetByName,
        String newBankName,
        String newAccountName,
        String newAccountNumber,
        String changedByUserId,
        String changedByName,
        Instant changedAt
) implements Serializable {
}