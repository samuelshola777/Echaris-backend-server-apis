package com.echaris.api.customer.response;

import com.echaris.api.customer.Customer;
import com.echaris.api.customer.CustomerKycDocuments;

import java.util.Set;

public record CustomerKycResponse(
        String customerId,
        boolean doneKyc,
        boolean kycAccepted,
        Set<CustomerKycDocuments> kycDocuments
) {

    public static CustomerKycResponse from(Customer customer) {
        Set<CustomerKycDocuments> documents =
                customer.getKycDocuments() == null
                        ? Set.of()
                        : Set.copyOf(customer.getKycDocuments());

        return new CustomerKycResponse(
                customer.getId(),
                customer.isDoneKyc(),
                customer.isKycAccepted(),
                documents
        );
    }
}