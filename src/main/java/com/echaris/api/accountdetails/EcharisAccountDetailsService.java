package com.echaris.api.accountdetails;

import com.echaris.api.accountdetails.request.EcharisAccountDetailsRequest;
import com.echaris.api.accountdetails.response.EcharisAccountDetailsResponse;
import com.echaris.api.admin.AdminService;
import com.echaris.api.admin.response.AdminResponse;
import com.echaris.api.utils.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class EcharisAccountDetailsService {

    private final MongoTemplate mongoTemplate;
    private final AdminService adminService;

    public EcharisAccountDetailsResponse getCurrent() {
        adminService.getCurrentAdminProfile();
        return EcharisAccountDetailsResponse.from(requireAccount());
    }

    public List<EcharisAccountDetailsHistory> getHistory() {
        adminService.getCurrentAdminProfile();
        return requireAccount().getHistory();
    }

    public EcharisAccountDetailsResponse createOrUpdate(EcharisAccountDetailsRequest request) {
        AdminResponse admin = adminService.getCurrentAdminProfile();
        EcharisAccountDetails account = findAccount();
        Long currentVersion = account == null ? null : account.getVersion();

        if (!Objects.equals(currentVersion, request.version())) {
            throw new OptimisticLockingFailureException("Account details changed. Reload before updating");
        }

        String name = displayName(admin);
        Instant now = Instant.now();

        if (account == null) {
            EcharisAccountDetails newAccount = new EcharisAccountDetails(
                    request.bankName(), request.accountName(), request.accountNumber(),
                    admin.userId(), name, now
            );
            try {
                return EcharisAccountDetailsResponse.from(mongoTemplate.insert(newAccount));
            } catch (DuplicateKeyException exception) {
                throw new OptimisticLockingFailureException("Account details were already created", exception);
            }
        }

        if (account.changeDetails(
                request.bankName(), request.accountName(), request.accountNumber(),
                admin.userId(), name, now
        )) {
            account = mongoTemplate.save(account);
        }

        return EcharisAccountDetailsResponse.from(account);
    }

    private EcharisAccountDetails requireAccount() {
        EcharisAccountDetails account = findAccount();
        if (account == null) {
            throw new ResourceNotFoundException("Echaris account details have not been configured");
        }
        return account;
    }

    private EcharisAccountDetails findAccount() {
        return mongoTemplate.findById(EcharisAccountDetails.ACCOUNT_DETAILS_ID, EcharisAccountDetails.class);
    }

    private String displayName(AdminResponse admin) {
        String firstName = Objects.toString(admin.firstName(), "").strip();
        String lastName = Objects.toString(admin.lastName(), "").strip();
        String fullName = (firstName + " " + lastName).strip();
        return fullName.isEmpty() ? admin.emailAddress() : fullName;
    }
}