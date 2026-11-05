package com.echaris.api.commitment;

import com.echaris.api.utils.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;

@Getter
@Setter
@Document(collection = "commitment_returns")
public class CommitmentReturn extends BaseEntity {



    private String commitmentId;
    private String userId;
    private int monthNumber;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal amount;

    private CommitmentReturnStatus status = CommitmentReturnStatus.AVAILABLE;
    private String transactionId;
}