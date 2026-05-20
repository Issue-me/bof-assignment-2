package com.bof.banking.scheduler;

import com.bof.banking.service.impl.SavingsInterestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Scheduled job for monthly savings account interest.
 *
 * Runs on the 1st of every month at 01:00 Fiji time (UTC+12).
 * Calls SavingsInterestService.runManual() which:
 *   - Calculates interest for every active SAVINGS account
 *   - Deducts RIWT (10%) unless customer has an approved exemption
 *   - Debits BOF90000001 (bank internal account)
 *   - Credits net interest to each customer account
 *   - Sends in-app + email notifications to each customer
 *   - Writes a SavingsInterestTransaction record for full auditability
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterestAccrualScheduler {

    private final SavingsInterestService savingsInterestService;

    /**
     * Credit monthly interest on the 1st of every month at 01:00 Fiji time.
     *
     * We pass the previous month explicitly because this fires on the 1st —
     * e.g. on 1 April at 01:00 we want to credit interest for March.
     *
     * cron format: second minute hour day-of-month month day-of-week
     */
    @Scheduled(cron = "0 0 1 1 * *", zone = "Pacific/Fiji")
    public void runMonthlyInterestCredit() {
        // At 01:00 on the 1st, "yesterday" is still in the previous month
        LocalDate yesterday = LocalDate.now().minusDays(1);
        int month = yesterday.getMonthValue();
        int year  = yesterday.getYear();

        log.info("=== Scheduled monthly interest credit starting: period={}/{} ===", month, year);
        try {
            SavingsInterestService.InterestRunResult result =
                    savingsInterestService.runManual(month, year);
            log.info("=== Monthly interest credit complete: {} ===", result.summary());
        } catch (Exception e) {
            log.error("=== Monthly interest credit FAILED for {}/{}: {} ===",
                    month, year, e.getMessage(), e);
        }
    }
}