package com.bof.banking.service.impl;

import com.bof.banking.model.*;
import com.bof.banking.model.enums.*;
import com.bof.banking.repository.*;
import com.bof.banking.service.notification.NotificationService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * Encapsulates business rules for n rw ht re fu nd se rv ic e and keeps controller logic thin.
 */
public class NrwhtRefundService {

    @Value("${app.bank.internal-account-number:BOF90000001}")
    private String bankInternalAccountNumber;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final InterestSummaryRepository interestSummaryRepository;
    private final SavingsInterestTransactionRepository interestTxRepository;
    private final NotificationService notificationService;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    /**
     * Handles process refund on tin registration.
     * @param userId the unique identifier of the target record.
     * @param updatedBy the date or time value used by this operation.
     */
    public void processRefundOnTinRegistration(Long userId, String updatedBy) {
        int year = LocalDate.now().getYear();

        // 1. Fetch fresh user
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        log.info("🔄 NRWHT refund CHECK: {} resident={} tin='{}' by={}", 
            user.getEmail(), user.isResident(), user.getTinNumber(), updatedBy);

        if (!user.isResident()) {
            log.info("⏭️  SKIPPED: {} not resident", user.getEmail());
            return;
        }
        if (!user.hasTin()) {
            log.info("⏭️  SKIPPED: {} no TIN", user.getEmail());
            return;
        }

        // 2. Find savings accounts
        List<Account> savings = accountRepository.findByUser(user).stream()
            .filter(a -> AccountType.SAVINGS.equals(a.getAccountType()) && a.isActive())
            .toList();

        if (savings.isEmpty()) {
            log.info("⏭️  SKIPPED: {} no savings accounts", user.getEmail());
            return;
        }

        // 3. Calculate total NRWHT (sum riwt_deducted column from Jan-Mar)
        // BigDecimal totalNrwht = savings.stream()
        //     .map(a -> interestTxRepository.sumRiwtByAccountAndYear(a, year))
        //     .filter(Objects::nonNull)
        //     .reduce(BigDecimal.ZERO, BigDecimal::add)
        //     .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalNrwht = interestSummaryRepository
            .findByUserAndTaxYear(user, year)
            .map(s -> s.getNrwhtWithheld())
            .orElse(BigDecimal.ZERO);

        // Fallback for cases where summary is missing/stale at TIN registration time.
        if (totalNrwht == null || totalNrwht.compareTo(BigDecimal.ZERO) <= 0) {
            totalNrwht = savings.stream()
                .map(a -> interestTxRepository.sumRiwtByAccountAndYear(a, year))
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        }

        log.info("💰 Total NRWHT for {}: FJD {}", user.getEmail(), totalNrwht);

        if (totalNrwht.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("⏭️  SKIPPED: {} no NRWHT deductions", user.getEmail());
            return;
        }

        // Idempotency guard: do not post a second refund for the same account/year.
        boolean alreadyRefunded = savings.stream()
            .anyMatch(a -> !transactionRepository.findNrwhtRefundsByAccountAndYear(a, year).isEmpty());
        if (alreadyRefunded) {
            log.info("⏭️  SKIPPED: {} refund already exists for {}", user.getEmail(), year);
            return;
        }

        // 4. Get account IDs before clear()
        Long custId = savings.get(0).getId();
        Long bankId = accountRepository.findByAccountNumber(bankInternalAccountNumber)
            .map(Account::getId).orElseThrow();

        // 5. Flush + clear cache for fresh locks
        entityManager.flush();
        entityManager.clear();

        // 6. Lock accounts (pessimistic write lock)
        Account custLocked = accountRepository.findByIdForUpdate(custId)
            .orElseThrow(() -> new RuntimeException("Account lost: " + custId));
        Account bankLocked = accountRepository.findByIdForUpdate(bankId)
            .orElseThrow(() -> new RuntimeException("Bank account lost"));

        if (bankLocked.getBalance().compareTo(totalNrwht) < 0) {
            throw new RuntimeException("Bank insufficient: need " + totalNrwht);
        }

        // 7. Execute transfer
        String ref = "NRWHT-REFUND-" + year + "-" + custLocked.getAccountNumber();
        
        custLocked.setBalance(custLocked.getBalance().add(totalNrwht));
        bankLocked.setBalance(bankLocked.getBalance().subtract(totalNrwht));

        accountRepository.saveAndFlush(custLocked);
        accountRepository.saveAndFlush(bankLocked);

        // 8. Create refund transaction (triggers 💸 banner)
        transactionRepository.save(Transaction.builder()
            .referenceNumber(ref)
            .transactionType(TransactionType.DEPOSIT)
            .amount(totalNrwht)
            .description(String.format(
                "NRWHT Refund — TIN %s registered. FJD %.2f Jan-Mar %d refunded. By: %s",
                user.getTinNumber(), totalNrwht, year, updatedBy))
            .sourceAccount(bankLocked)
            .destinationAccount(custLocked)
            .status(PaymentStatus.COMPLETED)
            .balanceAfter(custLocked.getBalance())
            .transactionDate(LocalDateTime.now())
            .build());

        // 9. Persist refund state on annual interest summary
        interestSummaryRepository.findByUserAndTaxYear(user, year).ifPresent(summary -> {
            summary.setNrwhtRefunded(true);
            summary.setNrwhtRefundReference(ref);
            interestSummaryRepository.save(summary);
            log.info("UserInterestSummary updated: nrwhtRefunded=true ref={}", ref);
        });

        log.info("✅ REFUND COMPLETE: {} FJD {} ref={} | Cust:{} Bank:{}",
            user.getEmail(), totalNrwht, ref, custLocked.getBalance(), bankLocked.getBalance());

        // 10. Notify customer
        notificationService.notifyNrwhtRefund(
            user.getEmail(),
            totalNrwht,
            user.getTinNumber(),
            ref
        );
    }
}
