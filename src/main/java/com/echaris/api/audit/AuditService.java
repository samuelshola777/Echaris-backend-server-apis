package com.echaris.api.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final MongoTemplate mongoTemplate;

    public void record(
            String event,
            String actorUserId,
            String subjectUserId,
            String entityType,
            String entityId,
            String transactionId,
            String ip,
            Map<String, String> before,
            Map<String, String> after
    ) {
        AuditLog audit = new AuditLog();

        audit.setId(UUID.randomUUID().toString());
        audit.setEvent(event);
        audit.setActorType(actorUserId == null ? "SYSTEM" : "USER");
        audit.setActorUserId(actorUserId);
        audit.setSubjectUserId(subjectUserId);
        audit.setEntityType(entityType);
        audit.setEntityId(entityId);
        audit.setTransactionId(transactionId);
        audit.setOccurredAt(Instant.now());
        audit.setIpAddress(ip);
        audit.setBefore(before == null ? Map.of() : before);
        audit.setAfter(after == null ? Map.of() : after);

        mongoTemplate.insert(audit);
    }
}