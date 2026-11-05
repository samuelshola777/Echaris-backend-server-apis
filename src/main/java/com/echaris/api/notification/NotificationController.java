package com.echaris.api.notification;

import com.echaris.api.utils.ApiResponse;
import com.echaris.api.utils.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final HttpServletRequest request;
    private final NotificationStreamService notificationStreamService;

    @GetMapping(
            value = "/private/stream",
            produces = "text/event-stream"
    )
    public ResponseEntity<SseEmitter> stream() {
        String userId = currentUserId();

        Claims claims = (Claims) request.getAttribute(
                "validatedClaims"
        );

        long remaining = claims.getExpiration() == null
                ? 60000L
                : claims.getExpiration().getTime()
                - System.currentTimeMillis();

        if (remaining <= 0) {
            throw new UnauthorizedException(
                    "Authentication has expired"
            );
        }

        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(
                        notificationStreamService.connect(
                                userId,
                                Math.min(
                                        remaining,
                                        60000L
                                )
                        )
                );
    }

    @GetMapping("/private")
    public ApiResponse<List<NotificationResponse>> getNotifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return new ApiResponse<>(
                true,
                "Notifications retrieved",
                notificationService.getNotifications(
                        currentUserId(),
                        page,
                        size
                )
        );
    }

    @GetMapping("/private/unread-count")
    public ApiResponse<Long> getUnreadCount() {
        return new ApiResponse<>(
                true,
                "Unread count retrieved",
                notificationService.getUnreadCount(
                        currentUserId()
                )
        );
    }

    @PatchMapping("/private/{notificationId}/read")
    public ApiResponse<NotificationResponse> markRead(
            @PathVariable String notificationId
    ) {
        return new ApiResponse<>(
                true,
                "Notification marked as read",
                notificationService.markRead(
                        currentUserId(),
                        notificationId
                )
        );
    }

    @PatchMapping("/private/read-all")
    public ApiResponse<Long> markAllRead() {
        return new ApiResponse<>(
                true,
                "Notifications marked as read",
                notificationService.markAllRead(
                        currentUserId()
                )
        );
    }

    @DeleteMapping("/private/{notificationId}")
    public ApiResponse<Void> delete(
            @PathVariable String notificationId
    ) {
        notificationService.delete(
                currentUserId(),
                notificationId
        );

        return new ApiResponse<>(
                true,
                "Notification deleted",
                null
        );
    }

    private String currentUserId() {
        Object value = request.getAttribute(
                "validatedClaims"
        );

        if (!(value instanceof Claims claims)) {
            throw new UnauthorizedException(
                    "Authentication is required"
            );
        }

        String userId = claims.get(
                "userId",
                String.class
        );

        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedException(
                    "Authenticated user ID is unavailable"
            );
        }

        return userId;
    }
}