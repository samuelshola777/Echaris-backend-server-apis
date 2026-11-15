package com.echaris.api.marketplace;

import com.echaris.api.commitment.CommitmentRules;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketplaceReturnTestData {

    private static final String FIXTURE_PROFILE = "marketplace-return-fixture";
    private static final List<String> FINANCING_IDS = List.of(
            "6af8098915f37d31b1a84c6f",
            "6af80b41253913360155a07f"
    );

    private final MongoTemplate mongoTemplate;
    private final TransactionTemplate transactionTemplate;
    private final MarketplaceRateService rateService;
    private final Environment environment;

    @PostConstruct
    public void prepareDueReturns() {
        String activeProfiles = Arrays.toString(environment.getActiveProfiles());
        if (!environment.matchesProfiles(FIXTURE_PROFILE)) {
            log.info("Marketplace return fixture disabled: activeProfiles={}, requiredProfile={}",
                    activeProfiles, FIXTURE_PROFILE);
            return;
        }

        log.info("Starting marketplace return fixture: activeProfiles={}", activeProfiles);
        try {
            Instant dueAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            log.info("Marketplace return fixture enabled: database={}, financingIds={}, dueAt={}",
                    mongoTemplate.getDb().getName(), FINANCING_IDS, dueAt);

            rateService.seedDefaultRate();
            BigDecimal monthlyRate = rateService.getConfiguredPercentage();
            log.info("Marketplace return fixture rate: monthlyRatePercent={}", monthlyRate);

            Integer prepared = transactionTemplate.execute(status -> prepareReturns(dueAt, monthlyRate));
            log.info("Prepared marketplace test returns: count={}, dueAt={}", prepared, dueAt);
        } catch (RuntimeException exception) {
            log.error("Marketplace return fixture failed: financingIds={}", FINANCING_IDS, exception);
            throw exception;
        }
    }

    private int prepareReturns(Instant dueAt, BigDecimal monthlyRate) {
        LocalDateTime nextReturnAt = LocalDateTime.ofInstant(dueAt, ZoneOffset.UTC);
        int prepared = 0;

        for (String id : FINANCING_IDS) {
            MarketplaceFinancing financing = mongoTemplate.findById(id, MarketplaceFinancing.class);
            if (financing == null) {
                log.warn("Skipping marketplace test financing: financingId={}, reason=not found", id);
                continue;
            }
            if (financing.getStatus() != MarketplaceFinancingStatus.AWAITING_PAYMENT) {
                log.info("Skipping marketplace test financing: financingId={}, status={}, nextReturnAtUtc={}",
                        id, financing.getStatus(), financing.getNextReturnAt());
                continue;
            }

            MarketplaceReturn installment = new MarketplaceReturn();
            installment.setId(id + ":1");
            installment.setFinancingId(id);
            installment.setMonthNumber(1);
            installment.setAmount(CommitmentRules.monthlyReturn(financing.getCapital(), monthlyRate));
            installment.setDueAt(dueAt);
            installment.setStatus(MarketplaceReturnStatus.PENDING);
            mongoTemplate.insert(installment);

            financing.setApprovedAt(nextReturnAt.minusMonths(1));
            financing.setMonthlyRate(monthlyRate);
            financing.setNextReturnAt(nextReturnAt);
            financing.setStatus(MarketplaceFinancingStatus.ACCUMULATING);
            financing.setUpdatedAt(nextReturnAt);
            mongoTemplate.save(financing);

            log.info("Staged marketplace test return: financingId={}, returnId={}, amount={}, dueAt={}",
                    id, installment.getId(), installment.getAmount(), dueAt);
            prepared++;
        }

        return prepared;
    }
}