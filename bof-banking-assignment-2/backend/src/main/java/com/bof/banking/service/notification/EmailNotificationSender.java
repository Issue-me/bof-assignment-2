package com.bof.banking.service.notification;

/**
 * Abstraction for sending email notifications.
 */
public interface EmailNotificationSender {

    /**
     * Sends a notification email.
     *
     * @param recipientEmail recipient address
     * @param subject email subject
     * @param bodyHtml HTML email body
     */
    void send(String recipientEmail, String subject, String body);
}
