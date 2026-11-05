package com.echaris.api.notification;

import java.time.LocalDateTime;
import java.util.Map;

public record NotificationResponse(
        String id,
        String type,
        String title,
        String message,
        String referenceType,
        String referenceId,
        Map<String, String> data,
        LocalDateTime createdAt,
        LocalDateTime readAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getReferenceType(),
                notification.getReferenceId(),
                notification.getData() == null
                        ? Map.of()
                        : Map.copyOf(notification.getData()),
                notification.getCreatedAt(),
                notification.getReadAt()
        );
    }
}