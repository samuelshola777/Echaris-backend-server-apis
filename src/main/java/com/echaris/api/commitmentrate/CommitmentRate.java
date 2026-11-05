package com.echaris.api.commitmentrate;

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
@Document(collection = "commitment_rate")
public class CommitmentRate extends BaseEntity {

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal percentage;

    private String updatedBy;

    @Version
    private Long version;
}