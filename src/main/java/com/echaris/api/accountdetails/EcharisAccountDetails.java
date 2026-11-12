package com.echaris.api.accountdetails;

import com.echaris.api.utils.BaseEntity;
import com.echaris.api.utils.exception.InvalidInputException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Document(collection = "echaris_account_details")
public class EcharisAccountDetails extends BaseEntity {

    public static final String ACCOUNT_DETAILS_ID = "echaris-account-details";

    private String bankName;
    private String accountName;
    private String accountNumber;
    private Instant effectiveFrom;
    private String setByUserId;
    private String setByName;
    private List<EcharisAccountDetailsHistory> history = new ArrayList<>();

    @JsonIgnore
    @Version
    private Long version;

    public EcharisAccountDetails(
            String bankName,
            String accountName,
            String accountNumber,
            String setByUserId,
            String setByName,
            Instant effectiveFrom
    ) {
        setId(ACCOUNT_DETAILS_ID);
        this.bankName = requireText(bankName, "Bank name");
        this.accountName = requireText(accountName, "Account name");
        this.accountNumber = requireAccountNumber(accountNumber);
        this.setByUserId = requireText(setByUserId, "User ID");
        this.setByName = requireText(setByName, "User name");
        this.effectiveFrom = requireTime(effectiveFrom);
    }

    public List<EcharisAccountDetailsHistory> getHistory() {
        return List.copyOf(history);
    }

    public boolean changeDetails(
            String newBankName,
            String newAccountName,
            String newAccountNumber,
            String changedByUserId,
            String changedByName,
            Instant changedAt
    ) {
        String bank = requireText(newBankName, "Bank name");
        String name = requireText(newAccountName, "Account name");
        String number = requireAccountNumber(newAccountNumber);
        String actorId = requireText(changedByUserId, "User ID");
        String actorName = requireText(changedByName, "User name");
        Instant changeTime = requireTime(changedAt);

        if (changeTime.isBefore(effectiveFrom)) {
            throw new InvalidInputException("Change time cannot be before the current effective date");
        }

        if (bank.equals(bankName) && name.equals(accountName) && number.equals(accountNumber)) {
            return false;
        }

        history.add(new EcharisAccountDetailsHistory(
                bankName, accountName, accountNumber, effectiveFrom, setByUserId, setByName,
                bank, name, number, actorId, actorName, changeTime
        ));

        bankName = bank;
        accountName = name;
        accountNumber = number;
        effectiveFrom = changeTime;
        setByUserId = actorId;
        setByName = actorName;
        setUpdatedAt(LocalDateTime.now());
        return true;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidInputException(field + " is required");
        }
        return value.strip();
    }

    private static String requireAccountNumber(String value) {
        String number = requireText(value, "Account number");
        if (!number.matches("[0-9]{10}")) {
            throw new InvalidInputException("Account number must contain exactly 10 digits");
        }
        return number;
    }

    private static Instant requireTime(Instant value) {
        if (value == null) {
            throw new InvalidInputException("Effective time is required");
        }
        return value.truncatedTo(ChronoUnit.MILLIS);
    }
}