package com.echaris.api.marketplace;

import com.echaris.api.utils.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;

@Getter
@Setter
@Document(collection = "marketplace_rate")
public class MarketplaceRate extends BaseEntity {

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal percentage;

    private String updatedBy;

    @Version
    private Long version;
}