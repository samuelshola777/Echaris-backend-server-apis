package com.echaris.api.product;

import com.echaris.api.utils.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Document(collection = "products")
public class Product extends BaseEntity {

    private String name;
    private String description;
    private String categoryId;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal price;

    private List<String> imageUrls = List.of();
    private int stockQuantity;
    private boolean madeToOrder;
    private boolean active;

    @JsonIgnore
    @Version
    private Long version;

    public String getStockStatus() {
        if (madeToOrder) {
            return "MADE_TO_ORDER";
        }

        if (stockQuantity == 0) {
            return "OUT_OF_STOCK";
        }

        return stockQuantity <= 5 ? "LOW_STOCK" : "IN_STOCK";
    }
}