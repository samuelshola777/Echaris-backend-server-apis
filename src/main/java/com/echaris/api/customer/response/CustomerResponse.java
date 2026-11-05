package com.echaris.api.customer.response;

import com.echaris.api.customer.Customer;
import com.echaris.api.userauthmgt.user.User;
import com.echaris.api.utils.Gender;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Builder
public record CustomerResponse(
        String customerId,
        String customerNumber,
        String userId,
        String firstName,
        String lastName,
        String emailAddress,
        String phoneNumber,
        String profilePictureUrl,
        LocalDate dateOfBirth,
        String age,
        Gender gender,
        boolean active,
        boolean verified,
        boolean marketingEmailsEnabled,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static CustomerResponse from(
            Customer customer,
            User user
    ) {
        return CustomerResponse.builder()
                .customerId(customer.getId())
                .customerNumber(customer.getCustomerNumber())
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .emailAddress(user.getEmailAddress())
                .phoneNumber(user.getPhoneNumber())
                .profilePictureUrl(user.getProfilePictureUrl())
                .dateOfBirth(user.getDateOfBirth())
                .age(user.getAge())
                .gender(user.getGender())
                .active(
                        customer.isActive()
                                && user.isActive()
                )
                .verified(user.isVerified())
                .marketingEmailsEnabled(
                        customer.isMarketingEmailsEnabled()
                )
                .lastLoginAt(customer.getLastLoginAt())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .build();
    }
}