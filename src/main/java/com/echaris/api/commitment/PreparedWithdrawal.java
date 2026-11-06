package com.echaris.api.commitment;

import com.echaris.api.payment.Transaction;

public record PreparedWithdrawal(
        Transaction transaction,
        boolean created
) {
}