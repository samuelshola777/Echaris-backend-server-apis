package com.echaris.api.customer.request;



import com.echaris.api.utils.Gender;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public record CustomerFilterRequest(
        @Size(max = 1000)
        String searchParam,

        Boolean active,

        Boolean verified,

        Gender gender,

        Boolean marketingEmailsEnabled,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate registeredFrom,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate registeredTo
) {
}