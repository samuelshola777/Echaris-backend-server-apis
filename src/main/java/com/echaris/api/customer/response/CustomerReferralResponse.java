package com.echaris.api.customer.response;

import com.echaris.api.customer.Customer;
import com.echaris.api.userauthmgt.user.User;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomerReferralResponse(
        String customerId,
        LocalDateTime verifiedAt,
        String firstName,
        String lastName,
        String fullName,
        String profilePictureUrl
) {
    public static CustomerReferralResponse from(Customer customer, User user) {
        if (user == null) {
            return new CustomerReferralResponse(
                    customer.getId(), customer.getReferralVerifiedAt(), null, null, null, null
            );
        }

        String fullName = Stream.of(user.getFirstName(), user.getLastName())
                .filter(name -> name != null && !name.isBlank())
                .map(String::strip)
                .collect(Collectors.joining(" "));

        return new CustomerReferralResponse(
                customer.getId(),
                customer.getReferralVerifiedAt(),
                user.getFirstName(),
                user.getLastName(),
                fullName.isEmpty() ? null : fullName,
                user.getProfilePictureUrl()
        );
    }
}