package com.echaris.api.customer;

import com.echaris.api.utils.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Set;

@Document(collection = "customers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer extends BaseEntity {

    @Indexed(unique = true)
    private String userId;

    @Indexed(unique = true)
    private String customerNumber;

    private boolean active;
    private boolean deleted;
    private boolean marketingEmailsEnabled;

    private LocalDateTime lastLoginAt;
    private LocalDateTime deletedAt;

    private Set<CustomerKycDocuments> kycDocuments;

    private Long kycRevision;
    private boolean doneKyc;
    private boolean kycAccepted;

    private boolean acceptedConsent;
    private LocalDateTime consentAcceptedAt;
}