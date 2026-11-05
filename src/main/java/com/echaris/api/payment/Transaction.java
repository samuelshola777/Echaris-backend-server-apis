package com.echaris.api.payment;

import com.echaris.api.utils.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Document(collection = "transactions")
public class Transaction extends BaseEntity {




    private String commitmentId;
    private String userId;
    private String requestKey;
    private TransactionDirection direction;
    private TransactionPurpose purpose;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal amount;

    private String reference;
    private TransactionStatus status = TransactionStatus.PENDING;
    private String paymentUrl;

    @JsonIgnore
    private String recipientCode;

    @JsonIgnore
    private String requestFingerprint;

    private Instant completedAt;
    private String providerStatus;
}