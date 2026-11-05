package com.echaris.api.notification;

import com.echaris.api.userauthmgt.user.UserType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Document(collection = "notifications")
public class Notification {

    @Id
    private String id;

    private String recipientUserId;
    private UserType recipientUserType;

    private String type;
    private String title;
    private String message;

    private String referenceType;
    private String referenceId;

    private Map<String, String> data;

    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}