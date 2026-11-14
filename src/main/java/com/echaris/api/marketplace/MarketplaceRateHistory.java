package com.echaris.api.marketplace;

import com.echaris.api.utils.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@Setter
@Document(collection = "marketplace_rate_history")
public class MarketplaceRateHistory extends BaseEntity {

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal previousPercentage;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal newPercentage;

    private String changedBy;
    private LocalDateTime changedAt;
}