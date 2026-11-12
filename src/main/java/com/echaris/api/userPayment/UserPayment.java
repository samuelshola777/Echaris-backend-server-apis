package com.echaris.api.userPayment;

import com.echaris.api.utils.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Document(collection = "user_payments")
public class UserPayment extends BaseEntity {

    private String userId;
    private UserPaymentPurpose purpose;
    private String targetId;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal amount;

    private List<String> receiptUrls = List.of();
    private String transferReference;
    private UserPaymentStatus status = UserPaymentStatus.PENDING;

    private String reviewedByUserId;
    private Instant reviewedAt;
    private Instant paidAt;

    @JsonIgnore
    private String bankCreditId;

    @JsonIgnore
    @Version
    private Long version;
}