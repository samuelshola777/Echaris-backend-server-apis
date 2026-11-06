package com.echaris.api.commitmentduration;

import com.echaris.api.admin.AdminService;
import com.echaris.api.audit.AuditService;
import com.echaris.api.commitmentduration.request.CommitmentDurationRequest;
import com.echaris.api.utils.exception.InvalidInputException;
import com.echaris.api.utils.exception.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommitmentDurationService {

    private final CommitmentDurationRepository durationRepository;
    private final MongoTemplate mongoTemplate;
    private final AuditService auditService;
    private final AdminService adminService;

    @PostConstruct
    public void initialize() {
        mongoTemplate.indexOps(CommitmentDuration.class)
                .createIndex(
                        new Index()
                                .on("durationMonths", Sort.Direction.ASC)
                                .unique()
                );

        for (int months : List.of(3, 6, 12)) {
            String id = "default-" + months;

            if (durationRepository.existsById(id)) {
                continue;
            }

            CommitmentDuration duration = new CommitmentDuration();
            duration.setId(id);
            duration.setDurationMonths(months);
            duration.setActive(true);

            try {
                durationRepository.insert(duration);
            } catch (DuplicateKeyException ignored) {
                // Another instance already created this ID or month option.
            }
        }
    }

    public List<CommitmentDuration> available() {
        return durationRepository
                .findAllByActiveTrueOrderByDurationMonthsAsc();
    }

    public List<CommitmentDuration> listAll() {
        adminService.getCurrentAdminProfile();

        return durationRepository
                .findAllByOrderByDurationMonthsAsc();
    }

    public void requireAvailable(int durationMonths) {
        if (durationMonths <= 0
                || !durationRepository
                .existsByDurationMonthsAndActiveTrue(durationMonths)) {
            throw new InvalidInputException(
                    "Select an available commitment duration"
            );
        }
    }

    @Transactional
    public CommitmentDuration create(
            CommitmentDurationRequest request
    ) {
        String adminUserId = adminService
                .getCurrentAdminProfile()
                .userId();

        validate(request);

        if (durationRepository.existsByDurationMonths(
                request.durationMonths()
        )) {
            throw duplicateDuration();
        }

        CommitmentDuration duration = new CommitmentDuration();
        duration.setId(UUID.randomUUID().toString());
        duration.setDurationMonths(request.durationMonths());
        duration.setActive(request.active());

        CommitmentDuration saved = save(duration);

        audit(
                "COMMITMENT_DURATION_CREATED",
                adminUserId,
                saved,
                Map.of()
        );

        return saved;
    }

    @Transactional
    public CommitmentDuration update(
            String durationId,
            CommitmentDurationRequest request
    ) {
        String adminUserId = adminService
                .getCurrentAdminProfile()
                .userId();

        validate(request);

        CommitmentDuration duration = find(durationId);

        if (durationRepository.existsByDurationMonthsAndIdNot(
                request.durationMonths(),
                durationId
        )) {
            throw duplicateDuration();
        }

        if (duration.getDurationMonths() == request.durationMonths()
                && duration.isActive() == request.active()) {
            return duration;
        }

        Map<String, String> before = snapshot(duration);

        duration.setDurationMonths(request.durationMonths());
        duration.setActive(request.active());
        duration.setUpdatedAt(LocalDateTime.now());

        CommitmentDuration saved = save(duration);

        audit(
                "COMMITMENT_DURATION_UPDATED",
                adminUserId,
                saved,
                before
        );

        return saved;
    }

    @Transactional
    public void remove(String durationId) {
        String adminUserId = adminService
                .getCurrentAdminProfile()
                .userId();

        CommitmentDuration duration = find(durationId);

        if (!duration.isActive()) {
            return;
        }

        Map<String, String> before = snapshot(duration);

        duration.setActive(false);
        duration.setUpdatedAt(LocalDateTime.now());

        CommitmentDuration saved = save(duration);

        audit(
                "COMMITMENT_DURATION_REMOVED",
                adminUserId,
                saved,
                before
        );
    }

    private CommitmentDuration find(String id) {
        return durationRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Commitment duration not found"
                        )
                );
    }

    private CommitmentDuration save(
            CommitmentDuration duration
    ) {
        try {
            return durationRepository.save(duration);

        } catch (DuplicateKeyException exception) {
            throw duplicateDuration();
        }
    }

    private void validate(
            CommitmentDurationRequest request
    ) {
        if (request == null
                || request.durationMonths() == null
                || request.durationMonths() <= 0
                || request.active() == null) {
            throw new InvalidInputException(
                    "Positive duration months and active status are required"
            );
        }
    }

    private InvalidInputException duplicateDuration() {
        return new InvalidInputException(
                "This duration already exists. "
                        + "Update the existing option to enable it if needed"
        );
    }

    private Map<String, String> snapshot(
            CommitmentDuration duration
    ) {
        return Map.of(
                "durationMonths",
                Integer.toString(duration.getDurationMonths()),
                "active",
                Boolean.toString(duration.isActive())
        );
    }

    private void audit(
            String event,
            String adminUserId,
            CommitmentDuration duration,
            Map<String, String> before
    ) {
        String ip = RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes
                ? attributes.getRequest().getRemoteAddr()
                : null;

        auditService.record(
                event,
                adminUserId,
                null,
                "COMMITMENT_DURATION",
                duration.getId(),
                null,
                ip,
                before,
                snapshot(duration)
        );
    }
}