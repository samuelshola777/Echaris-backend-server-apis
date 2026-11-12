package com.echaris.api.productcategory;

import com.echaris.api.utils.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "product_categories")
public class ProductCategory extends BaseEntity {

    private String name;

    @JsonIgnore
    private String nameKey;

    private boolean active;

    @JsonIgnore
    @Version
    private Long version;
}