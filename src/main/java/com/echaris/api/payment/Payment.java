package com.echaris.api.payment;

import com.echaris.api.utils.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Document(collection = "payments")
public class Payment extends BaseEntity {



    private String commitmentId;
    private String userId;
    private String transactionId;
    private TransactionDirection direction;
    private TransactionPurpose purpose;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal amount;

    private String reference;
    private Instant paidAt;
    private PaymentStatus status = PaymentStatus.SUCCESS;
    private Instant reversedAt;
}