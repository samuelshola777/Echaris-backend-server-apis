package com.echaris.api.customer.request;




import com.echaris.api.userauthmgt.userotp.UserOtpType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CustomerResendOtpRequest(

        @NotBlank(message = "Email address is required")
        @Email(message = "Enter a valid email address")
        String emailAddress,
        UserOtpType userOtpType
) {
}