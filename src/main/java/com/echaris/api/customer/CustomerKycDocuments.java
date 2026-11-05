package com.echaris.api.customer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Transient;

import java.time.LocalDateTime;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "documentType")
public class CustomerKycDocuments {

    private CustomerKycDocumentType documentType;
    private String documentNumber;

    private String frontViewUrl;
    private String backViewUrl;

    private LocalDateTime submittedAt;
    private long submissionVersion;

    private boolean isApproved;
    private LocalDateTime approvedAt;
    private String approvedBy;

    private LocalDateTime rejectedAt;
    private String rejectedBy;
    private String rejectionReason;

    @Transient
    public CustomerKycReviewStatus getReviewStatus() {
        if (isApproved) {
            return CustomerKycReviewStatus.APPROVED;
        }

        if (rejectedAt != null) {
            return CustomerKycReviewStatus.REJECTED;
        }

        return CustomerKycReviewStatus.PENDING;
    }
}