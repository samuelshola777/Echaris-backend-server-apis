package com.echaris.api.payoutaccount;

import com.echaris.api.customer.CustomerService;
import com.echaris.api.customer.response.CustomerResponse;
import com.echaris.api.payoutaccount.response.CustomerPayoutAccountResponse;
import com.echaris.api.utils.exception.InvalidInputException;
import com.echaris.api.utils.exception.ResourceNotFoundException;
import com.echaris.api.utils.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CustomerPayoutAccountService {

    private final MongoTemplate mongoTemplate;
    private final CustomerService customerService;

    public CustomerPayoutAccountResponse createOrUpdate(CustomerPayoutAccountDetails request) {
        String userId = requireCustomerId();
        CustomerPayoutAccountDetails details = normalize(request);
        CustomerPayoutAccount account = mongoTemplate.findById(userId, CustomerPayoutAccount.class);

        if (account != null) {
            return saveDetails(account, details, userId);
        }

        account = new CustomerPayoutAccount();
        account.setId(userId);
        recordChange(account, "CREATED", details, userId);

        try {
            return CustomerPayoutAccountResponse.from(mongoTemplate.insert(account));
        } catch (DuplicateKeyException exception) {
            throw new OptimisticLockingFailureException(
                    "Payout account changed. Reload before trying again", exception);
        }
    }

    public CustomerPayoutAccountResponse get() {
        return CustomerPayoutAccountResponse.from(requireAccount(requireCustomerId()));
    }

    public CustomerPayoutAccountResponse update(long version, CustomerPayoutAccountDetails request) {
        String userId = requireCustomerId();
        CustomerPayoutAccount account = requireAccount(userId);
        requireVersion(account, version);
        return saveDetails(account, normalize(request), userId);
    }

    private CustomerPayoutAccountResponse saveDetails(
            CustomerPayoutAccount account, CustomerPayoutAccountDetails details, String userId
    ) {
        if (details.equals(account.getDetails())) {
            return CustomerPayoutAccountResponse.from(account);
        }

        String action = account.getDetails() == null ? "CREATED" : "UPDATED";
        recordChange(account, action, details, userId);
        return CustomerPayoutAccountResponse.from(mongoTemplate.save(account));
    }

    public void delete(long version) {
        String userId = requireCustomerId();
        CustomerPayoutAccount account = requireAccount(userId);
        requireVersion(account, version);
        recordChange(account, "DELETED", null, userId);
        mongoTemplate.save(account);
    }

    public List<CustomerPayoutAccountHistory> getHistory() {
        CustomerPayoutAccount account = mongoTemplate.findById(requireCustomerId(), CustomerPayoutAccount.class);

        if (account == null) {
            return List.of();
        }

        List<CustomerPayoutAccountHistory> history = new ArrayList<>(account.getHistory());
        Collections.reverse(history);
        return history;
    }

    private CustomerPayoutAccount requireAccount(String userId) {
        CustomerPayoutAccount account = mongoTemplate.findById(userId, CustomerPayoutAccount.class);

        if (account == null || account.getDetails() == null) {
            throw new ResourceNotFoundException("No payout account is set");
        }

        return account;
    }

    private void requireVersion(CustomerPayoutAccount account, long version) {
        if (version < 0) {
            throw new InvalidInputException("Payout account version must be zero or greater");
        }

        if (!Objects.equals(account.getVersion(), version)) {
            throw new OptimisticLockingFailureException("Payout account changed. Reload before trying again");
        }
    }

    private String requireCustomerId() {
        CustomerResponse customer = customerService.getCurrentProfile();

        if (!customer.active() || !customer.verified()) {
            throw new UnauthorizedException("An active, verified customer account is required");
        }

        return customer.userId();
    }

    private CustomerPayoutAccountDetails normalize(CustomerPayoutAccountDetails request) {
        return new CustomerPayoutAccountDetails(
                request.bankName().strip(),
                request.accountName().strip(),
                request.accountNumber(),
                request.amount().setScale(2)
        );
    }

    private void recordChange(
            CustomerPayoutAccount account, String action, CustomerPayoutAccountDetails newDetails, String userId
    ) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        account.getHistory().add(new CustomerPayoutAccountHistory(
                action, account.getDetails(), newDetails, userId, now
        ));
        account.setDetails(newDetails);
        account.setUpdatedAt(LocalDateTime.now());
    }
}