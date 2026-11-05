package com.echaris.api.commitmentrate;

import com.echaris.api.admin.AdminRepository;
import com.echaris.api.audit.AuditService;
import com.echaris.api.commitmentrate.request.RateChangeRequest;
import com.echaris.api.notification.NotificationService;
import com.echaris.api.userauthmgt.user.User;
import com.echaris.api.userauthmgt.user.UserRepository;
import com.echaris.api.userauthmgt.user.UserType;
import com.echaris.api.utils.emailsenderservice.EmailSenderService;
import com.echaris.api.utils.exception.InvalidOperationException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommitmentRateService {

    private final MongoTemplate mongoTemplate;
    private final AuditService auditService;
    private final AdminRepository adminRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EmailSenderService emailSenderService;

    @PostConstruct
    public void initialize() {
        if (mongoTemplate.findById(
                "current",
                CommitmentRate.class
        ) != null) {
            return;
        }

        CommitmentRate rate = new CommitmentRate();

        rate.setId("current");
        rate.setPercentage(new BigDecimal("5.00"));

        try {
            mongoTemplate.insert(rate);
        } catch (DuplicateKeyException ignored) {
            // Another instance already initialized the same record.
        }
    }

    public CommitmentRate current() {
        return mongoTemplate.findById(
                "current",
                CommitmentRate.class
        );
    }

    @Transactional
    public CommitmentRate change(
            RateChangeRequest request,
            String adminUserId,
            String ip
    ) {
        CommitmentRate rate = current();

        if (!Objects.equals(
                rate.getVersion(),
                request.version()
        )) {
            throw new InvalidOperationException(
                    "Rate changed. Refresh before updating it"
            );
        }

        if (rate.getPercentage()
                .compareTo(request.percentage()) == 0) {
            return rate;
        }

        BigDecimal previous = rate.getPercentage();

        CommitmentRateHistory history =
                new CommitmentRateHistory();

        history.setId(UUID.randomUUID().toString());
        history.setPreviousPercentage(previous);
        history.setNewPercentage(request.percentage());
        history.setChangedBy(adminUserId);
        history.setChangedAt(Instant.now());

        mongoTemplate.insert(history);

        rate.setPercentage(request.percentage());
        rate.setUpdatedBy(adminUserId);
        rate.setUpdatedAt(LocalDateTime.now());

        mongoTemplate.save(rate);

        auditService.record(
                "COMMITMENT_RATE_CHANGED",
                adminUserId,
                null,
                "COMMITMENT_RATE",
                rate.getId(),
                null,
                ip,
                Map.of(
                        "percentage",
                        previous.toPlainString()
                ),
                Map.of(
                        "percentage",
                        rate.getPercentage().toPlainString()
                )
        );

        Set<String> recipients = new HashSet<>();

        adminRepository.findAllByDeleted(false)
                .stream()
                .filter(admin -> admin.isActive())
                .forEach(admin ->
                        recipients.add(admin.getUserId())
                );

        String message =
                "Monthly commitment rate changed from "
                        + previous.toPlainString()
                        + "% to "
                        + rate.getPercentage().toPlainString()
                        + "% by admin user ID "
                        + adminUserId
                        + ". Existing commitments retain their agreed rate.";

        for (User user :
                userRepository.findAllById(recipients)) {

            if (user.getUserType() != UserType.ADMIN
                    || user.isDeleted()
                    || !user.isActive()
                    || !user.isVerified()) {
                continue;
            }

            notificationService.create(
                    user.getId(),
                    "COMMITMENT_RATE_CHANGED",
                    "Commitment rate changed",
                    message,
                    "COMMITMENT_RATE",
                    rate.getId(),
                    Map.of("changedBy", adminUserId)
            );

            emailSenderService.sendTemplateEmailAfterCommit(
                    user.getEmailAddress(),
                    "Commitment rate changed",
                    "commitment-event",
                    Map.of(
                            "name",
                            user.getFirstName() == null
                                    ? "there"
                                    : user.getFirstName(),
                            "title",
                            "Commitment rate changed",
                            "message",
                            message
                    )
            );
        }

        return rate;
    }
}