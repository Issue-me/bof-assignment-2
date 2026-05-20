package com.bof.banking.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Sends customer email alerts using SMTP (Gmail supported).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GmailSmtpEmailNotificationSender implements EmailNotificationSender {

    private final JavaMailSender mailSender;

    @Value("${app.notification.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.notification.email.from:${spring.mail.username:}}")
    private String fromEmail;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    @Override
    /**
     * Handles send.
     * @param recipientEmail the email of the authenticated user.
     * @param subject the subject.
     * @param body the request payload.
     */
    public void send(String recipientEmail, String subject, String body) {
        if (!emailEnabled) {
            return;
        }

        if (smtpUsername == null || smtpUsername.isBlank() || smtpPassword == null || smtpPassword.isBlank()) {
            log.error(
                    "Skipping email notification to {} because spring.mail credentials are missing. " +
                            "Set MAIL_USERNAME and MAIL_APP_PASSWORD in your env file.",
                    recipientEmail);
            return;
        }

        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("Skipping notification email because recipient is empty.");
            return;
        }

        if (recipientEmail.endsWith("@example.com")) {
            log.warn("Notification recipient {} is a demo address and may not receive real email.", recipientEmail);
        }

        try {
            var message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(recipientEmail);
            helper.setSubject(subject);
            helper.setText(body, true);
            String effectiveFrom = (fromEmail != null && !fromEmail.isBlank()) ? fromEmail : smtpUsername;
            helper.setFrom("Bank Of Fiji <" + effectiveFrom + ">");
            mailSender.send(message);
        } catch (MessagingException ex) {
            log.error("Unable to build notification email for {}: {}", recipientEmail, ex.getMessage());
        } catch (MailAuthenticationException ex) {
            log.error(
                    "Unable to send notification email to {}: Authentication failed. " +
                    "For Gmail SMTP, enable 2-Step Verification and use a 16-character App Password (no spaces). " +
                    "SMTP username in use: {}",
                recipientEmail,
                smtpUsername);
        } catch (MailException ex) {
            log.error("Unable to send notification email to {}: {}", recipientEmail, ex.getMessage());
        }
    }
}
