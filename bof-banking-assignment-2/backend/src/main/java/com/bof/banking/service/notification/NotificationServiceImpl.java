package com.bof.banking.service.notification;

import com.bof.banking.dto.notification.NotificationResponse;
import com.bof.banking.exception.BadRequestException;
import com.bof.banking.exception.ResourceNotFoundException;
import com.bof.banking.mapper.NotificationMapper;
import com.bof.banking.model.Account;
import com.bof.banking.model.BillPayment;
import com.bof.banking.model.Notification;
import com.bof.banking.model.Transaction;
import com.bof.banking.model.User;
import com.bof.banking.model.enums.AccountType;
import com.bof.banking.model.enums.LoanStatus;
import com.bof.banking.model.enums.NotificationType;
import com.bof.banking.repository.AccountRepository;
import com.bof.banking.repository.LoanRepository;
import com.bof.banking.repository.NotificationRepository;
import com.bof.banking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * Encapsulates business rules for n ot if ic at io ns er vi ce im pl and keeps controller logic thin.
 */
public class NotificationServiceImpl implements NotificationService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private final NotificationRepository  notificationRepository;
    private final UserRepository          userRepository;
    private final AccountRepository       accountRepository;
    private final LoanRepository          loanRepository;
    private final NotificationMapper      notificationMapper;
    private final EmailNotificationSender emailNotificationSender;

    @Value("${app.notification.high-value-threshold:5000}")
    private BigDecimal highValueThreshold;

    // ── Bill payment ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    /**
     * Handles notify bill payment processed.
     * @param payment the payment.
     */
    public void notifyBillPaymentProcessed(BillPayment payment) {
        if (payment.getUser() == null) return;
        String billerName = payment.getBiller() != null
                ? payment.getBiller().getBillerName() : "your biller";
        String title   = "Bill payment processed";
        String message = String.format(
                "Your bill payment of $%s to %s was processed on %s. Ref: %s",
                payment.getAmount(), billerName,
                fmtDt(payment.getProcessedDate()), payment.getPaymentReference());
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Biller",    billerName);
        details.put("Amount",    "$" + payment.getAmount());
        details.put("Status",    payment.getStatus().name());
        details.put("Reference", payment.getPaymentReference());
        send(payment.getUser(), NotificationType.BILL_PAYMENT_PROCESSED, title, message, details);
    }

    // ── High-value transaction ─────────────────────────────────────────────────
    @Async
    @Override
    @Transactional
    /**
     * Handles notify high value transaction.
     * @param userEmail the email of the authenticated user.
     * @param transaction the transaction.
     */
    public void notifyHighValueTransaction(String userEmail, Transaction transaction) {
        if (transaction.getAmount().compareTo(highValueThreshold) <= 0) return;
        User user = byEmail(userEmail);
        String title   = "High-value transaction alert";
        String message = String.format(
                "A %s transaction of $%s was processed on %s. Ref: %s",
                transaction.getTransactionType().name(), transaction.getAmount(),
                fmtDt(transaction.getTransactionDate()), transaction.getReferenceNumber());
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Transaction type", transaction.getTransactionType().name());
        details.put("Amount",           "$" + transaction.getAmount());
        details.put("Threshold",        "$" + highValueThreshold);
        details.put("Reference",        transaction.getReferenceNumber());
        send(user, NotificationType.HIGH_VALUE_TRANSACTION, title, message, details);
    }

    // ── Funds received (transfer receiver) ─────────────────────────────────────

    @Async
    @Override
    @Transactional
    /**
     * Handles notify funds received.
     * @param transaction the transaction.
     */
    public void notifyFundsReceived(Transaction transaction) {
        if (transaction == null || transaction.getDestinationAccount() == null) return;

        Account destination = transaction.getDestinationAccount();
        User receiver = destination.getUser();
        if (receiver == null) return;

        Account sourceAccount = transaction.getSourceAccount();
        User sender = sourceAccount != null ? sourceAccount.getUser() : null;

        // Skip self-transfers (same customer as sender and receiver)
        if (sender != null && sender.getId() != null
                && sender.getId().equals(receiver.getId())) {
            return;
        }

        String senderName = sender != null ? sender.getFullName() : "Bank of Fiji customer";
        String fromAccount = sourceAccount != null ? sourceAccount.getAccountNumber() : "N/A";
        String toAccount = destination.getAccountNumber();

        String title = "Funds received";
        String message = String.format(
                "You have received a transfer of $%s from %s (account %s) to your account %s on %s. Ref: %s",
                transaction.getAmount(),
                senderName,
                fromAccount,
                toAccount,
                fmtDt(transaction.getTransactionDate()),
                transaction.getReferenceNumber());

        Map<String, String> details = new LinkedHashMap<>();
        details.put("Amount",       "$" + transaction.getAmount());
        details.put("Sender",       senderName);
        details.put("From account", fromAccount);
        details.put("To account",   toAccount);
        details.put("Reference",    transaction.getReferenceNumber());
        details.put("Date",         fmtDt(transaction.getTransactionDate()));

        send(receiver, NotificationType.TRANSACTION, title, message, details);
    }

    // ── Funds sent (transfer sender) ───────────────────────────────────────────

    @Override
    @Transactional
    @Async
    /**
     * Handles notify funds sent.
     * @param transaction the transaction.
     */
    public void notifyFundsSent(Transaction transaction) {
        if (transaction == null || transaction.getSourceAccount() == null) return;

        Account source = transaction.getSourceAccount();
        User sender = source.getUser();
        if (sender == null) return;

        Account destinationAccount = transaction.getDestinationAccount();
        User receiver = destinationAccount != null ? destinationAccount.getUser() : null;

        // Skip self-transfers (same customer as sender and receiver)
        if (receiver != null && receiver.getId() != null
                && receiver.getId().equals(sender.getId())) {
            return;
        }

        String receiverName = receiver != null ? receiver.getFullName() : "Bank of Fiji customer";
        String fromAccount = source.getAccountNumber();
        String toAccount = destinationAccount != null ? destinationAccount.getAccountNumber() : "N/A";

        String title = "Funds sent";
        String message = String.format(
                "You have sent a transfer of $%s to %s (account %s) from your account %s on %s. Ref: %s",
                transaction.getAmount(),
                receiverName,
                toAccount,
                fromAccount,
                fmtDt(transaction.getTransactionDate()),
                transaction.getReferenceNumber());

        Map<String, String> details = new LinkedHashMap<>();
        details.put("Amount",        "$" + transaction.getAmount());
        details.put("Recipient",     receiverName);
        details.put("From account",  fromAccount);
        details.put("To account",    toAccount);
        details.put("Reference",     transaction.getReferenceNumber());
        details.put("Date",          fmtDt(transaction.getTransactionDate()));

        send(sender, NotificationType.TRANSACTION, title, message, details);
    }

    // ── Interest credited ──────────────────────────────────────────────────────

    @Override
    @Transactional
    /**
     * Handles notify interest credited.
     * @param userEmail the email of the authenticated user.
     * @param title the title.
     * @param message the message.
     */
    public void notifyInterestCredited(String userEmail, String title, String message) {
        User user = byEmail(userEmail);
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Type",    "Monthly savings interest credit");
        details.put("Account", "See message for details");
        details.put("Date",    fmtDt(LocalDateTime.now()));
        send(user, NotificationType.TRANSACTION, title, message, details);
    }

    @Override
    @Transactional
    /**
     * Handles notify nrwht refund.
     * @param userEmail the email of the authenticated user.
     * @param refundAmount the monetary value used by this operation.
     * @param tin the tin.
     * @param refundRef the refund Ref.
     */
    public void notifyNrwhtRefund(String userEmail, BigDecimal refundAmount, String tin, String refundRef) {
        User user = byEmail(userEmail);
        String title = "NRWHT Refund Credited to Your Account";
        String message = String.format(
                "FJD %.2f NRWHT has been refunded to your savings account after your TIN %s was registered. Reference: %s.",
                refundAmount, tin, refundRef);

        Map<String, String> details = new LinkedHashMap<>();
        details.put("Refund amount", String.format("FJD %.2f", refundAmount));
        details.put("TIN", tin);
        details.put("Reference", refundRef);
        details.put("Date", fmtDt(LocalDateTime.now()));

        send(user, NotificationType.NRWHT_REFUND_CREDITED, title, message, details);
        log.info("NRWHT refund notification sent: user={} amount={} ref={}", userEmail, refundAmount, refundRef);
    }

    // ── Interest rate — single customer, changed ───────────────────────────────

    @Override
    @Transactional
    public void notifyInterestRateChanged(String userEmail, String accountNumber,
                                           BigDecimal oldRate, BigDecimal newRate,
                                           LocalDate effectiveDate) {
        User user = byEmail(userEmail);
        boolean up = newRate.compareTo(oldRate) > 0;
        String title   = "Savings interest rate " + (up ? "increased" : "updated");
        String message = String.format(
                "The interest rate on your savings account has changed from %s to %s, effective %s.",
                pct(oldRate), pct(newRate), effectiveDate.format(DATE_FMT));
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Previous rate",  pct(oldRate));
        details.put("New rate",       pct(newRate));
        details.put("Effective date", effectiveDate.format(DATE_FMT));
        details.put("Change",         up ? "▲ Increased" : "▼ Decreased");
        send(user, NotificationType.INTEREST_RATE_CHANGE, title, message, details);
    }

    // ── Interest rate — advance notice ─────────────────────────────────────────

    @Override
    @Transactional
    public void notifyInterestRateUpcoming(String userEmail, String accountNumber,
                                            BigDecimal newRate, LocalDate effectiveDate) {
        User user = byEmail(userEmail);
        String title   = "Upcoming savings interest rate change";
        String message = String.format(
                "Bank of Fiji will update the savings interest rate to %s from %s.",
                pct(newRate), effectiveDate.format(DATE_FMT));
        Map<String, String> details = new LinkedHashMap<>();
        details.put("New rate",       pct(newRate));
        details.put("Effective date", effectiveDate.format(DATE_FMT));
        send(user, NotificationType.INTEREST_RATE_CHANGE, title, message, details);
    }

    // ── Savings interest rate broadcast ────────────────────────────────────────

    @Override
    @Transactional
    public void broadcastInterestRateChange(String accountType,
                                             BigDecimal oldRate, BigDecimal newRate,
                                             LocalDate effectiveDate) {
        AccountType type;
        try {
            type = AccountType.valueOf(accountType.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("broadcastInterestRateChange: unknown accountType '{}', skipping", accountType);
            return;
        }

        List<User> users = accountRepository.findDistinctUsersByAccountType(type);
        if (users.isEmpty()) {
            log.info("broadcastInterestRateChange: no active accounts of type {}", accountType);
            return;
        }

        boolean isFuture = effectiveDate.isAfter(LocalDate.now());
        long count = 0;
        for (User user : users) {
            try {
                String title, message;
                if (isFuture) {
                    title = "Upcoming savings rate change — " + pct(newRate)
                            + " from " + effectiveDate.format(DATE_FMT);
                    message = String.format(
                            "Bank of Fiji will change the savings account interest rate "
                            + "from %s to %s, effective %s. "
                            + "This rate has been set per Reserve Bank of Fiji directives.",
                            pct(oldRate), pct(newRate), effectiveDate.format(DATE_FMT));
                } else {
                    title = "Savings interest rate updated to " + pct(newRate) + " p.a.";
                    message = String.format(
                            "Bank of Fiji has updated the savings account interest rate "
                            + "from %s to %s, effective today. "
                            + "This rate has been set per Reserve Bank of Fiji directives.",
                            pct(oldRate), pct(newRate));
                }
                Map<String, String> details = new LinkedHashMap<>();
                details.put("Previous rate",  pct(oldRate));
                details.put("New rate",       pct(newRate));
                details.put("Effective date", effectiveDate.format(DATE_FMT));
                details.put("Account type",   "Savings");
                send(user, NotificationType.INTEREST_RATE_CHANGE, title, message, details);
                count++;
            } catch (Exception ex) {
                log.warn("Failed to notify {} of rate change: {}", user.getEmail(), ex.getMessage());
            }
        }
        log.info("Interest rate broadcast done: type={} notified={}", accountType, count);
    }


    @Override
    @Transactional
    public void notifyLoanRateChanged(String loanType,
                                       BigDecimal oldRate,
                                       BigDecimal newRate,
                                       String setBy,
                                       String reason) {

   
        List<com.bof.banking.model.Loan> activeLoans =
            loanRepository.findByLoanTypeAndStatusWithUser(loanType, LoanStatus.ACTIVE);

        if (activeLoans.isEmpty()) {
            log.info("notifyLoanRateChanged: no active '{}' loans to notify", loanType);
            return;
        }

        boolean isIncrease = newRate.compareTo(oldRate != null ? oldRate : BigDecimal.ZERO) > 0;

     
        Map<Long, UserLoanSummary> byUser = new LinkedHashMap<>();
        for (com.bof.banking.model.Loan loan : activeLoans) {
            if (loan.getUser() == null) continue;
            Long userId = loan.getUser().getId();
            byUser.computeIfAbsent(userId, id -> new UserLoanSummary(loan.getUser()))
                  .addLoan(loan.getLoanNumber(), loan.getInterestRate());
        }

        long count = 0;
        for (UserLoanSummary summary : byUser.values()) {
            try {
                String loanNumbers = String.join(", ", summary.loanNumbers);
                String ownRateStr  = pct(summary.ownRate);

                String title = "Bank of Fiji — " + loanType + " product rate "
                        + (isIncrease ? "increased" : "updated")
                        + " to " + pct(newRate) + " p.a.";

                String message = String.format(
                        "Bank of Fiji has updated the %s product rate from %s to %s p.a. "
                        + "This applies to new applications only — "
                        + "your existing %s (%s) remains at your agreed rate of %s p.a. "
                        + "Your monthly repayment amount is not affected.",
                        loanType,
                        oldRate != null ? pct(oldRate) : "the previous rate",
                        pct(newRate),
                        loanType.toLowerCase(),
                        loanNumbers,
                        ownRateStr);

                Map<String, String> details = new LinkedHashMap<>();
                details.put("Loan type",        loanType);
                details.put("New product rate",  pct(newRate) + " p.a. (new applications)");
                if (oldRate != null)
                    details.put("Previous rate", pct(oldRate) + " p.a.");
                details.put("Your loan(s)",      loanNumbers);
                details.put("Your rate",         ownRateStr + " p.a. (unchanged)");
                details.put("Effect on you",     "No change — your repayments stay the same");
                if (reason != null && !reason.isBlank())
                    details.put("Reason",        reason);
                details.put("Updated by",        setBy);
                details.put("Date",              fmtDt(LocalDateTime.now()));

                // FIX 4: Each send() is independent — an exception for one user
                // does not prevent notifications for subsequent users.
                send(summary.user, NotificationType.INTEREST_RATE_CHANGE, title, message, details);
                count++;
            } catch (Exception ex) {
                log.warn("notifyLoanRateChanged: failed to notify user {}: {}",
                        summary.user.getEmail(), ex.getMessage());
            }
        }

        log.info("notifyLoanRateChanged: type='{}' newRate={} notified={}/{}",
                loanType, pct(newRate), count, byUser.size());
    }

    /** Accumulates loan numbers and own rate for a single user during the batch. */
    private static class UserLoanSummary {
        final User user;
        final List<String> loanNumbers = new java.util.ArrayList<>();
        BigDecimal ownRate = BigDecimal.ZERO;

        UserLoanSummary(User user) { this.user = user; }

        void addLoan(String loanNumber, BigDecimal rate) {
            loanNumbers.add(loanNumber);
            if (ownRate.compareTo(BigDecimal.ZERO) == 0 && rate != null) {
                ownRate = rate; // use the first loan's rate as representative
            }
        }
    }

    // ── RIWT exemption APPROVED ────────────────────────────────────────────────

    @Override
    @Transactional
    /**
     * Handles notify riwt exemption applied.
     * @param userEmail the email of the authenticated user.
     * @param taxYear the monetary value used by this operation.
     */
    public void notifyRiwtExemptionApplied(String userEmail, int taxYear) {
        User user = byEmail(userEmail);
        String title   = "RIWT exemption approved for " + taxYear;
        String message = "Bank of Fiji has approved your Resident Interest Withholding Tax (RIWT) "
                + "exemption for the " + taxYear + " tax year. "
                + "RIWT will no longer be deducted from your interest income.";
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Status",   "Approved ✓");
        details.put("Tax year", String.valueOf(taxYear));
        details.put("Effect",   "No RIWT will be deducted from future interest payments");
        details.put("Date",     fmtDt(LocalDateTime.now()));
        send(user, NotificationType.RIWT_EXEMPTION_APPLIED, title, message, details);
    }

    // ── RIWT exemption REJECTED ────────────────────────────────────────────────

    @Override
    @Transactional
    /**
     * Handles notify riwt exemption rejected.
     * @param userEmail the email of the authenticated user.
     * @param taxYear the monetary value used by this operation.
     * @param reason the reason.
     */
    public void notifyRiwtExemptionRejected(String userEmail, int taxYear, String reason) {
        User user = byEmail(userEmail);
        String title   = "RIWT exemption not approved — action required";
        String message = "Your RIWT exemption application for " + taxYear
                + " could not be approved. Reason: " + reason
                + ". Please resubmit via your Tax Report page or visit tpos.frcs.org.fj.";
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Status",    "Not approved");
        details.put("Tax year",  String.valueOf(taxYear));
        details.put("Reason",    reason);
        details.put("Next step", "Resubmit via Tax Report → Upload Certificate");
        send(user, NotificationType.SYSTEM, title, message, details);
    }

    // ── Transfer OTP ───────────────────────────────────────────────────────────
    @Async
    @Override
    @Transactional
    public void notifyTransferOtp(String userEmail, String otpCode, BigDecimal amount,
                                   String challengeId, LocalDateTime expiresAt) {
        User user = byEmail(userEmail);
        String title   = "Transfer OTP verification code";
        String message = String.format(
                "Use OTP %s to approve your high-value transfer of $%s. Expires at %s.",
                otpCode, amount, fmtDt(expiresAt));
        Map<String, String> details = new LinkedHashMap<>();
        details.put("OTP code",     otpCode);
        details.put("Amount",       "$" + amount);
        details.put("Challenge ID", challengeId);
        details.put("Expires at",   fmtDt(expiresAt));

        String emailHtml = buildEmailHtml(user, NotificationType.HIGH_VALUE_TRANSACTION, title, message, details);
        emailNotificationSender.send(user.getEmail(), title, emailHtml);
    }

    // ── Read / delete ──────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns unread notifications data.
     * @param userEmail the email of the authenticated user.
     * @return the matching results.
     */
    public List<NotificationResponse> getUnreadNotifications(String userEmail) {
        return notificationRepository
                .findByUserAndReadAtIsNullOrderByCreatedAtDesc(byEmail(userEmail))
                .stream().map(notificationMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns notifications data.
     * @param userEmail the email of the authenticated user.
     * @param pageable pagination and sorting settings.
     * @return a paged set of matching results.
     */
    public Page<NotificationResponse> getNotifications(String userEmail, Pageable pageable) {
        return notificationRepository
                .findByUserOrderByCreatedAtDesc(byEmail(userEmail), pageable)
                .map(notificationMapper::toResponse);
    }

    @Override
    @Transactional
    /**
     * Handles mark as read.
     * @param userEmail the email of the authenticated user.
     * @param id the unique identifier of the target record.
     * @return the result of the operation.
     */
    public NotificationResponse markAsRead(String userEmail, Long id) {
        Notification n = owned(byEmail(userEmail), id);
        if (n.getReadAt() == null) {
            n.setReadAt(LocalDateTime.now());
            n = notificationRepository.save(n);
        }
        return notificationMapper.toResponse(n);
    }

    @Override
    @Transactional
    /**
     * Removes notification data.
     * @param userEmail the email of the authenticated user.
     * @param id the unique identifier of the target record.
     */
    public void deleteNotification(String userEmail, Long id) {
        notificationRepository.delete(owned(byEmail(userEmail), id));
    }

    @Override
    @Transactional
    /**
     * Removes all notifications data.
     * @param userEmail the email of the authenticated user.
     */
    public void clearAllNotifications(String userEmail) {
        notificationRepository.deleteByUser(byEmail(userEmail));
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private void send(User user, NotificationType type,
                      String title, String message, Map<String, String> details) {
        notificationRepository.save(Notification.builder()
                .user(user).type(type).title(title).message(message).build());
        emailNotificationSender.send(user.getEmail(), title,
                buildEmailHtml(user, type, title, message, details));
        log.info("Notification: type={} user={}", type, user.getEmail());
    }

    private User byEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    private Notification owned(User user, Long id) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (!n.getUser().getId().equals(user.getId()))
            throw new BadRequestException("Notification does not belong to this user");
        return n;
    }

    private String pct(BigDecimal rate) {
        if (rate == null) return "0%";
        return rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%";
    }

    private String fmtDt(LocalDateTime dt) {
        return (dt != null ? dt : LocalDateTime.now()).format(TIME_FMT);
    }

    private String escHtml(String v) {
        if (v == null) return "";
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String buildEmailHtml(User user, NotificationType type,
                                   String title, String message,
                                   Map<String, String> detailFields) {
        String name = user.getFirstName() != null && !user.getFirstName().isBlank()
                ? user.getFirstName() : "Customer";
        StringBuilder rows = new StringBuilder();
        rows.append(row("Type",      type.name().replace('_', ' ')));
        rows.append(row("Timestamp", fmtDt(LocalDateTime.now())));
        if (detailFields != null) detailFields.forEach((k, v) -> rows.append(row(k, v)));
        return """
            <!doctype html><html><body style="margin:0;padding:0;background:#f3f6fc;font-family:Arial,sans-serif;">
            <table width="100%" cellspacing="0" cellpadding="0" style="padding:24px 0;"><tr><td align="center">
            <table width="620" cellspacing="0" cellpadding="0" style="max-width:620px;background:#fff;border-radius:14px;border:1px solid #d9e4fa;">
            <tr><td style="background:linear-gradient(120deg,#003a8c,#0057c7);padding:20px 24px;color:#fff;border-radius:14px 14px 0 0;">
              <div style="font-size:12px;opacity:.8;text-transform:uppercase;letter-spacing:.08em;">Bank of Fiji</div>
              <div style="font-size:20px;font-weight:700;margin-top:4px;">BOF Notifications</div>
            </td></tr>
            <tr><td style="padding:22px 24px;">
              <p style="margin:0 0 12px;font-size:15px;">Dear __NAME__,</p>
              <h2 style="margin:0 0 10px;font-size:20px;color:#0c2f6a;line-height:1.3;">__TITLE__</h2>
              <p style="margin:0 0 16px;font-size:14px;line-height:1.6;color:#1f355e;">__MESSAGE__</p>
              <table width="100%" cellspacing="0" cellpadding="0" style="border:1px solid #e0e9f9;border-radius:10px;overflow:hidden;">
                <tr><td style="padding:9px 12px;background:#f1f6ff;color:#31558f;font-weight:700;font-size:13px;">Details</td></tr>
                <tr><td style="padding:0;"><table width="100%" cellspacing="0" cellpadding="0" style="border-collapse:collapse;">
                __ROWS__
                </table></td></tr>
              </table>
              <p style="margin:16px 0 0;font-size:12px;color:#5b6c89;">
                Need help? Email <a href="mailto:support@bof.com.fj" style="color:#0b5ed7;">support@bof.com.fj</a>
                or call +679 132 333.
              </p>
            </td></tr>
            <tr><td style="padding:12px 24px;background:#f5f8ff;color:#6b7e9f;font-size:11px;border-radius:0 0 14px 14px;">
              Automated notification from Bank of Fiji. Do not reply to this email.
            </td></tr>
            </table></td></tr></table></body></html>
            """
            .replace("__NAME__",    escHtml(name))
            .replace("__TITLE__",   escHtml(title))
            .replace("__MESSAGE__", escHtml(message))
            .replace("__ROWS__",    rows.toString());
    }

    private String row(String label, String value) {
        return "<tr><td style=\"padding:7px 12px;border-bottom:1px solid #e9eef8;color:#5a6f95;font-weight:600;font-size:13px;\">"
                + escHtml(label) + "</td><td style=\"padding:7px 12px;border-bottom:1px solid #e9eef8;color:#0f1f3d;font-size:13px;\">"
                + escHtml(value) + "</td></tr>";
    }

    @Override
    @Transactional
    /**
     * Handles notify frcs tax submitted.
     * @param userEmail the email of the authenticated user.
     * @param taxYear the monetary value used by this operation.
     * @param frcsReference the frcs Reference.
     */
    public void notifyFrcsTaxSubmitted(String userEmail, int taxYear, String frcsReference) {
        User user = byEmail(userEmail);
        String title   = "Your " + taxYear + " tax return has been submitted to FRCS";
        String message = "Bank of Fiji has submitted your " + taxYear
                + " annual interest tax return to the Fiji Revenue & Customs Service. "
                + "Your FRCS reference number is: " + frcsReference
                + ". Please keep this number for your records.";
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Tax year",        String.valueOf(taxYear));
        details.put("FRCS reference",  frcsReference);
        details.put("Submitted by",    "Bank of Fiji");
        details.put("Submission date", LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy")));
        details.put("Status",          "Submitted ✓");
        send(user, NotificationType.FRCS_TAX_SUBMITTED, title, message, details);
    }
}
