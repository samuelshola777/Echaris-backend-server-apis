package com.echaris.api.customer.request;

import com.echaris.api.customer.CustomerKycDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CustomerKycRequest(
        @NotNull(message = "Document type is required")
        CustomerKycDocumentType documentType,

        @NotBlank(message = "Document number is required")
        @Size(max = 100)
        String documentNumber,

        @NotBlank(message = "Front document URL is required")
        @Size(max = 2048)
        String frontViewUrl,

        @NotBlank(message = "Back document URL is required")
        @Size(max = 2048)
        String backViewUrl
) {
}