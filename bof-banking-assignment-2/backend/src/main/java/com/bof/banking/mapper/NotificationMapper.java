package com.bof.banking.mapper;

import com.bof.banking.dto.notification.NotificationResponse;
import com.bof.banking.model.Notification;
import org.springframework.stereotype.Component;

/**
 * Mapper for Notification entity to DTO conversions.
 */
@Component
public class NotificationMapper {

    /**
     * Handles to response.
     * @param notification the notification.
     * @return the result of the operation.
     */
    public NotificationResponse toResponse(Notification notification) {
        if (notification == null) {
            return null;
        }

        return NotificationResponse.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType())
                .createdAt(notification.getCreatedAt())
                .readAt(notification.getReadAt())
                .read(notification.getReadAt() != null)
                .build();
    }
}
