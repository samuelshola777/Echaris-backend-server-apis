package com.echaris.api.commitment;

import com.echaris.api.payment.TransactionPurpose;
import com.echaris.api.utils.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;

@Getter
@Setter
@Document(collection = "withdrawal_items")
public class WithdrawalItem extends BaseEntity {

    private String commitmentId;
    private String transactionId;
    private String returnId;
    private TransactionPurpose purpose;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal amount;

    private WithdrawalItemStatus status =
            WithdrawalItemStatus.RESERVED;
}