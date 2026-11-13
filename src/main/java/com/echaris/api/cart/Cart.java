package com.echaris.api.cart;

import com.echaris.api.utils.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Getter
@Setter
@Document(collection = "carts")
public class Cart extends BaseEntity {

    private String userId;
    private List<CartItem> items = List.of();

    @Version
    private Long version;
}