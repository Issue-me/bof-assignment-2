package com.bof.banking.controller;

import com.bof.banking.dto.notification.NotificationResponse;
import com.bof.banking.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for customer notification endpoints.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/unread")
    public ResponseEntity<List<NotificationResponse>> getUnreadNotifications(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(notificationService.getUnreadNotifications(userDetails.getUsername()));
    }

    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal UserDetails userDetails,
            Pageable pageable) {
        return ResponseEntity.ok(notificationService.getNotifications(userDetails.getUsername(), pageable));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long notificationId) {
        return ResponseEntity.ok(notificationService.markAsRead(userDetails.getUsername(), notificationId));
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> deleteNotification(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long notificationId) {
        notificationService.deleteNotification(userDetails.getUsername(), notificationId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/clear")
    /**
     * Removes all notifications data.
     * @param userDetails the authenticated user context.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<Void> clearAllNotifications(@AuthenticationPrincipal UserDetails userDetails) {
        notificationService.clearAllNotifications(userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
