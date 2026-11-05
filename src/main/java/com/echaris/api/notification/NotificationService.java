package com.echaris.api.notification;

import com.echaris.api.userauthmgt.user.UserRepository;
import com.echaris.api.utils.exception.InvalidInputException;
import com.echaris.api.utils.exception.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;

    @PostConstruct
    public void ensureIndexes() {
        var indexes = mongoTemplate.indexOps(Notification.class);

        indexes.ensureIndex(
                new Index()
                        .on("recipientUserId", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.DESC)
                        .on("id", Sort.Direction.ASC)
                        .named("notification_recipient_created")
        );

        indexes.ensureIndex(
                new Index()
                        .on("recipientUserId", Sort.Direction.ASC)
                        .on("readAt", Sort.Direction.ASC)
                        .named("notification_recipient_read")
        );
    }

    // Called internally by application services.
    // Do not expose arbitrary recipient creation through an HTTP endpoint.
    public NotificationResponse create(
            String recipientUserId,
            String type,
            String title,
            String message,
            String referenceType,
            String referenceId,
            Map<String, String> data
    ) {
        String recipientId = text(
                recipientUserId,
                "Recipient user ID",
                200
        );

        var user = userRepository.findById(recipientId)
                .filter(account -> !account.isDeleted())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Recipient account not found"
                        )
                );

        if ((referenceType == null) != (referenceId == null)) {
            throw new InvalidInputException(
                    "Reference type and ID must be supplied together"
            );
        }

        Notification notification = new Notification();

        notification.setId(UUID.randomUUID().toString());
        notification.setRecipientUserId(user.getId());
        notification.setRecipientUserType(user.getUserType());

        notification.setType(
                text(type, "Notification type", 100)
        );

        notification.setTitle(
                text(title, "Title", 200)
        );

        notification.setMessage(
                text(message, "Message", 2000)
        );

        notification.setReferenceType(
                referenceType == null
                        ? null
                        : text(referenceType, "Reference type", 100)
        );

        notification.setReferenceId(
                referenceId == null
                        ? null
                        : text(referenceId, "Reference ID", 200)
        );

        notification.setData(copyData(data));
        notification.setCreatedAt(LocalDateTime.now());

        return NotificationResponse.from(
                mongoTemplate.insert(notification)
        );
    }

    // userId must come from authenticated server context.
    public List<NotificationResponse> getNotifications(
            String userId,
            int page,
            int size
    ) {
        if (page < 1 || page > 10000 || size < 1 || size > 100) {
            throw new InvalidInputException(
                    "Page must be 1-10000 and size must be 1-100"
            );
        }

        Query query = owned(userId)
                .with(
                        Sort.by(
                                Sort.Order.desc("createdAt"),
                                Sort.Order.asc("id")
                        )
                )
                .skip((long) (page - 1) * size)
                .limit(size);

        return mongoTemplate.find(query, Notification.class)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    public long getUnreadCount(String userId) {
        Query query = owned(userId)
                .addCriteria(
                        Criteria.where("readAt").is(null)
                );

        return mongoTemplate.count(
                query,
                Notification.class
        );
    }

    public NotificationResponse markRead(
            String userId,
            String notificationId
    ) {
        String id = text(
                notificationId,
                "Notification ID",
                200
        );

        Query query = owned(userId)
                .addCriteria(
                        Criteria.where("id").is(id)
                );

        Query unread = owned(userId)
                .addCriteria(
                        Criteria.where("id").is(id)
                                .and("readAt").is(null)
                );

        // Retried requests preserve the original read timestamp.
        mongoTemplate.updateFirst(
                unread,
                new Update().set("readAt", LocalDateTime.now()),
                Notification.class
        );

        Notification notification = mongoTemplate.findOne(
                query,
                Notification.class
        );

        if (notification == null) {
            throw new ResourceNotFoundException(
                    "Notification not found"
            );
        }

        return NotificationResponse.from(notification);
    }

    public long markAllRead(String userId) {
        Query query = owned(userId)
                .addCriteria(
                        Criteria.where("readAt").is(null)
                );

        return mongoTemplate.updateMulti(
                query,
                new Update().set("readAt", LocalDateTime.now()),
                Notification.class
        ).getModifiedCount();
    }

    public void delete(
            String userId,
            String notificationId
    ) {
        Query query = owned(userId)
                .addCriteria(
                        Criteria.where("id").is(
                                text(
                                        notificationId,
                                        "Notification ID",
                                        200
                                )
                        )
                );

        long deleted = mongoTemplate.remove(
                query,
                Notification.class
        ).getDeletedCount();

        if (deleted == 0) {
            throw new ResourceNotFoundException(
                    "Notification not found"
            );
        }
    }

    private Query owned(String userId) {
        return Query.query(
                Criteria.where("recipientUserId").is(
                        text(
                                userId,
                                "Recipient user ID",
                                200
                        )
                )
        );
    }

    private Map<String, String> copyData(
            Map<String, String> data
    ) {
        if (data == null || data.isEmpty()) {
            return Map.of();
        }

        if (data.size() > 20) {
            throw new InvalidInputException(
                    "Too many notification data fields"
            );
        }

        Map<String, String> copy = new LinkedHashMap<>();

        data.forEach((key, value) -> {
            if (key == null
                    || !key.matches("[A-Za-z][A-Za-z0-9_]{0,49}")
                    || value == null
                    || value.length() > 2000) {
                throw new InvalidInputException(
                        "Invalid notification data field"
                );
            }

            copy.put(key, value);
        });

        return copy;
    }

    private String text(
            String value,
            String label,
            int maximum
    ) {
        if (value == null
                || value.isBlank()
                || value.trim().length() > maximum) {
            throw new InvalidInputException(
                    label
                            + " is required and must not exceed "
                            + maximum
                            + " characters"
            );
        }

        return value.trim();
    }
}