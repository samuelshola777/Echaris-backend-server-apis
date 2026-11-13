package com.echaris.api.marketplace;

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
@Document(collection = "marketplace_returns")
public class MarketplaceReturn extends BaseEntity {

    private String financingId;
    private int monthNumber;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal amount;

    private Instant dueAt;
    private Instant confirmedAt;
    private MarketplaceReturnStatus status = MarketplaceReturnStatus.PENDING;
}