package com.echaris.api.payoutaccount;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.io.Serializable;
import java.math.BigDecimal;

public record CustomerPayoutAccountDetails(
        @NotBlank @Size(max = 120) String bankName,
        @NotBlank @Size(max = 120) String accountName,
        @NotBlank @Pattern(regexp = "[0-9]{10}") String accountNumber,
        @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2)
        @Field(targetType = FieldType.DECIMAL128) BigDecimal amount
) implements Serializable {
}