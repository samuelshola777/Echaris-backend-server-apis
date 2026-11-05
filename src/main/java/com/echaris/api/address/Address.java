package com.echaris.api.address;

import com.echaris.api.utils.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "addresses")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Address extends BaseEntity {

    private String userId;
    private String country;
    private String state;
    private String city;
    private String addressLine;
    private String addressLine2;
    private String postalCode;
}