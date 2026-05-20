package com.bof.banking.service.notification;

import com.bof.banking.dto.notification.NotificationResponse;
import com.bof.banking.model.BillPayment;
import com.bof.banking.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for customer notifications (in-app + email).
 */
public interface NotificationService {

    // ── Transaction / payment ──────────────────────────────────────────────

    void notifyBillPaymentProcessed(BillPayment payment);

    void notifyHighValueTransaction(String userEmail, Transaction transaction);

    /**
     * Notifies the receiver when funds have been credited to their account
     * from a transfer between BOF customers.
     */
    void notifyFundsReceived(Transaction transaction);

    /**
     * Notifies the sender after a successful transfer between BOF customers.
     */
    void notifyFundsSent(Transaction transaction);

    void notifyInterestCredited(String userEmail, String title, String message);

    void notifyNrwhtRefund(String userEmail, BigDecimal refundAmount, String tin, String refundRef);

    // ── Savings interest rate ──────────────────────────────────────────────

    void notifyInterestRateChanged(String userEmail, String accountNumber,
                                   BigDecimal oldRate, BigDecimal newRate,
                                   LocalDate effectiveDate);

    void notifyInterestRateUpcoming(String userEmail, String accountNumber,
                                    BigDecimal newRate, LocalDate effectiveDate);

    void broadcastInterestRateChange(String accountType,
                                     BigDecimal oldRate, BigDecimal newRate,
                                     LocalDate effectiveDate);

    // ── Loan product rate ──────────────────────────────────────────────────

    /**
     * Notifies all customers who currently hold an ACTIVE loan of the given
     * loan type that the default rate for new applications of that type has changed.
     *
     * Important: this does NOT change their existing loan rate — it only informs
     * them of the product rate change. Their own loan rate is fixed at origination
     * unless an admin explicitly updates it.
     *
     * Called by LoanRateController after a teller/admin saves a new rate.
     *
     * @param loanType   e.g. "Personal Loan", "Home Loan"
     * @param oldRate    previous annual rate as decimal (0.085 = 8.5%), null if first-time set
     * @param newRate    new annual rate as decimal
     * @param setBy      teller/admin email who made the change
     * @param reason     optional RBF reference or reason
     */
    void notifyLoanRateChanged(String loanType,
                                BigDecimal oldRate,
                                BigDecimal newRate,
                                String setBy,
                                String reason);

    // ── RIWT exemption ────────────────────────────────────────────────────

    void notifyRiwtExemptionApplied(String userEmail, int taxYear);

    void notifyRiwtExemptionRejected(String userEmail, int taxYear, String reason);

    // ── Notification management ────────────────────────────────────────────

    List<NotificationResponse> getUnreadNotifications(String userEmail);

    Page<NotificationResponse> getNotifications(String userEmail, Pageable pageable);

    NotificationResponse markAsRead(String userEmail, Long notificationId);

    void deleteNotification(String userEmail, Long notificationId);

    void clearAllNotifications(String userEmail);

    void notifyTransferOtp(String userEmail, String otpCode, BigDecimal amount,
                           String challengeId, LocalDateTime expiresAt);

    /**
     * Notify a customer that their tax return has been submitted to FRCS by the bank.
     * Sends in-app + email with the FRCS reference number.
     */
    void notifyFrcsTaxSubmitted(String userEmail, int taxYear, String frcsReference);
}