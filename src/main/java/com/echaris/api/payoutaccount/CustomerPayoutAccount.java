package com.echaris.api.payoutaccount;

import com.echaris.api.utils.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Document(collection = "customer_payout_accounts")
public class CustomerPayoutAccount extends BaseEntity {

    private CustomerPayoutAccountDetails details;
    private List<CustomerPayoutAccountHistory> history = new ArrayList<>();

    @Version
    private Long version;
}