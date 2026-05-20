package com.bof.banking.scheduler;

import com.bof.banking.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Scheduled job for SIMPLE_ACCESS monthly maintenance fee deductions.
 *
 * OLD BEHAVIOUR: ran once on the last day of every month and charged
 * every eligible account on that single fixed date.
 *
 * NEW BEHAVIOUR: runs every day at 01:00 Fiji time. For each active
 * SIMPLE_ACCESS account it checks whether today's day-of-month matches
 * the day-of-month the account was created (the "anniversary day").
 * Only matching accounts are charged on that run.
 *
 * Example:
 *   Perry opened BOF87654321 on 31 Mar 2026  → charged on the 31st of each month
 *   Adrian opened BOF55443322 on 3  Jan 2026  → charged on the  3rd of each month
 *
 * Edge case — months shorter than the account's creation day:
 *   Perry's account was created on the 31st. February has 28/29 days.
 *   We charge her on the LAST DAY of shorter months instead (28 Feb, etc.).
 *   This matches standard banking convention (e.g. ANZ, Westpac).
 *   AccountServiceImpl.applyMaintenanceFeesByAnniversaryDay() implements
 *   this logic using LocalDate.lengthOfMonth().
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaintenanceFeeScheduler {

    private final AccountService accountService;

    /**
     * Runs daily at 01:00 Fiji time (UTC+12).
     *
     * Passes today's date to the service so it can determine which
     * accounts have their anniversary on this day.
     *
     * cron: second minute hour day-of-month month day-of-week
     */
    @Scheduled(cron = "0 0 1 * * *", zone = "Pacific/Fiji")
    public void runDailyMaintenanceFeeCheck() {
        LocalDate today = LocalDate.now();
        log.info("=== Daily maintenance fee check starting for {} ===", today);
        try {
            int chargedCount = accountService.applyMaintenanceFeesByAnniversaryDay(today);
            log.info("=== Daily maintenance fee check complete: charged={} on {} ===",
                    chargedCount, today);
        } catch (Exception e) {
            log.error("=== Daily maintenance fee check FAILED for {}: {} ===",
                    today, e.getMessage(), e);
        }
    }
}