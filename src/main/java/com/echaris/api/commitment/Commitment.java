package com.echaris.api.commitment;

import com.echaris.api.utils.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Document(collection = "commitments")
public class Commitment extends BaseEntity {



    private String customerId;
    private String userId;
    private String name;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal capital;

    private int durationMonths;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal monthlyRate;

    private CommitmentStatus status = CommitmentStatus.AWAITING_PAYMENT;
    private Instant fundedAt;
    private Instant maturityAt;
    private Instant capitalWithdrawnAt;
    private Instant agreementAcceptedAt;
    private String activeTransactionId;

    @Version
    private Long version;
}