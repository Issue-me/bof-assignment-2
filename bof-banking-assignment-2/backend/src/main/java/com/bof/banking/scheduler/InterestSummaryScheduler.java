package com.bof.banking.scheduler;

import com.bof.banking.model.User;
import com.bof.banking.model.UserInterestSummary;
import com.bof.banking.repository.InterestSummaryRepository;
import com.bof.banking.repository.UserRepository;
import com.bof.banking.service.impl.InterestSummaryCalculator;
import com.bof.banking.service.notification.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Scheduled jobs for mandatory FRCS end-of-year interest summary reporting.
 *
 * <ul>
 *   <li><b>28 Dec 00:00</b> – preliminary run so teller can review figures early.</li>
 *   <li><b>1 Jan 02:00</b>  – final run on the closed year; stamps records as ready for FRCS.</li>
 * </ul>
 *
 * Both jobs are idempotent — re-running overwrites existing records.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterestSummaryScheduler {

    private final UserRepository            userRepository;
    private final InterestSummaryCalculator calculator;
    private final InterestSummaryRepository interestSummaryRepository;
    private final NotificationService notificationService;
    
    /** Preliminary run — 28 December at midnight. */
    @Scheduled(cron = "0 0 0 28 12 *")
    @Transactional
    /**
     * Handles run preliminary summary.
     */
    public void runPreliminarySummary() {
        int year = LocalDate.now().getYear();
        log.info("=== Interest Summary PRELIMINARY run starting for year {} ===", year);
        processAllUsers(year);
        log.info("=== Interest Summary PRELIMINARY run complete for year {} ===", year);
    }

    /** Final run — 1 January at 02:00 for the just-closed year. */
    @Scheduled(cron = "0 0 2 1 1 *")
    @Transactional
    /**
     * Handles run final year end summary.
     */
    public void runFinalYearEndSummary() {
        int closedYear = LocalDate.now().getYear() - 1;
        log.info("=== Interest Summary FINAL run starting for closed year {} ===", closedYear);
        processAllUsers(closedYear);
        markReadyForFrcsSubmission(closedYear);
        log.info("=== Interest Summary FINAL run complete for closed year {} ===", closedYear);
    }

    /**
     * Manual trigger — called from {@link com.bof.banking.controller.TaxController}
     * or integration tests.
     *
     * @return count of users processed
     */
    @Transactional
    public int recalculateYear(int year) {
        log.info("Manual interest summary recalculation triggered for year {}", year);
        int count = processAllUsers(year);
        log.info("Manual recalculation complete: {} users processed for year {}", count, year);
        return count;
    }

    /**
     * Mark all summaries for the given year as submitted to FRCS.
     * Sets {@code submittedToFrcs = true} and stamps today as the submission date.
     * Called by the admin "Mark as Submitted to FRCS" button via
     * {@link com.bof.banking.controller.TaxController#markSubmittedToFrcs}.
     *
     * @param year the tax year to mark
     * @return count of summaries updated
     */


    @Transactional
    public int markAllSubmitted(int year) {
    List<UserInterestSummary> summaries = interestSummaryRepository.findByTaxYear(year);
    for (UserInterestSummary summary : summaries) {
        summary.setSubmittedToFrcs(true);
        summary.setFrcsSubmissionDate(LocalDate.now());
        interestSummaryRepository.save(summary);

        // Notify the customer — fire and forget, non-fatal
        try {
            notificationService.notifyFrcsTaxSubmitted(
                summary.getUser().getEmail(),
                year,
                summary.getFrcsReference() != null
                    ? summary.getFrcsReference()
                    : "FRCS-" + year + "-BATCH"
            );
        } catch (Exception e) {
            log.warn("Failed to notify {} of FRCS submission: {}", 
                summary.getUser().getEmail(), e.getMessage());
        }
    }
    return summaries.size();
}

    // ─────────────────────────────────────────────────────────────────────

    private int processAllUsers(int year) {
        List<User> users = userRepository.findAll();
        int processed = 0;
        for (User user : users) {
            try {
                calculator.calculateAndSave(user, year);
                processed++;
            } catch (Exception ex) {
                log.error("Failed to calculate interest summary for userId={} year={}: {}",
                        user.getId(), year, ex.getMessage(), ex);
            }
        }
        log.info("processAllUsers year={}: {}/{} succeeded", year, processed, users.size());
        return processed;
    }

    private void markReadyForFrcsSubmission(int year) {
        List<UserInterestSummary> pending =
                interestSummaryRepository.findByTaxYearAndSubmittedToFrcsFalse(year);
        LocalDate today = LocalDate.now();
        pending.forEach(s -> s.setFrcsSubmissionDate(today));
        interestSummaryRepository.saveAll(pending);
        log.info("Marked {} summaries ready for FRCS submission (year={})", pending.size(), year);
    }
}
