package com.echaris.api.audit;

import com.echaris.api.utils.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@Document(collection = "audit_logs")
public class AuditLog extends BaseEntity {

    private String event;
    private String actorType;
    private String actorUserId;
    private String subjectUserId;
    private String entityType;
    private String entityId;
    private String transactionId;
    private Instant occurredAt;
    private String ipAddress;
    private Map<String, String> before;
    private Map<String, String> after;
}