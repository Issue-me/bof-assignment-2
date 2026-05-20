package com.bof.banking.controller;

import com.bof.banking.exception.BadRequestException;
import com.bof.banking.model.InterestRate;
import com.bof.banking.model.enums.AccountType;
import com.bof.banking.repository.InterestRateRepository;
import com.bof.banking.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Manages the savings interest rate ladder.
 *
 * Default rate: 3.5% p.a. seeded by DataSeeder / SQL migration on fresh install.
 *
 * KEY RULES:
 *
 * 1. "Current rate" = the most recent entry whose effectiveFrom <= TODAY.
 *    A future-dated entry is SCHEDULED and must NOT appear as current rate.
 *
 * 2. No backdating: effectiveFrom must be >= today.
 *
 * 3. If interest_rates is empty, hasRate=false is returned and a WARN is logged
 *    so you can diagnose why DataSeeder / the SQL migration did not run.
 */
@Slf4j
@RestController
@RequestMapping("/api/interest-rates")
@RequiredArgsConstructor
public class InterestRateController {

    private final InterestRateRepository interestRateRepository;
    private final NotificationService    notificationService;

    // ── GET /api/interest-rates/current (PUBLIC — no auth required) ──────────
    //
    // Used by AccountsPage and TaxReportPage to show customers their savings rate.
    // Does NOT expose the full history or rate-management UI — read-only, single value.

    @GetMapping("/current")
    /**
        * Returns the rate that is currently in effect for savings accounts.
        *
        * This endpoint only reports the active rate (effectiveFrom <= today).
        * Future-dated rows are treated as scheduled and are not returned as current.
        *
        * @return a response map containing whether a rate exists, the annual and daily
        *         percentage values, and the date the current rate became effective.
     */
    public ResponseEntity<Map<String, Object>> getCurrentRate() {
        LocalDate today = LocalDate.now();
        Optional<InterestRate> currentOpt =
            interestRateRepository
                .findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(today);

        Map<String, Object> response = new LinkedHashMap<>();
        if (currentOpt.isPresent()) {
            InterestRate current = currentOpt.get();
            BigDecimal annual    = current.getAnnualRate();
            BigDecimal annualPct = annual.multiply(BigDecimal.valueOf(100))
                                         .setScale(4, RoundingMode.HALF_UP);
            BigDecimal dailyPct  = annual.divide(BigDecimal.valueOf(365), 8, RoundingMode.HALF_UP)
                                         .multiply(BigDecimal.valueOf(100))
                                         .setScale(6, RoundingMode.HALF_UP);
            response.put("hasRate",                true);
            response.put("currentAnnualRate",       annual);
            response.put("currentAnnualRatePct",    annualPct);    // percent e.g. 3.5000
            response.put("currentDailyRatePercent", dailyPct);
            response.put("effectiveSince",          current.getEffectiveFrom().toString());
        } else {
            response.put("hasRate",                false);
            response.put("currentAnnualRate",       null);
            response.put("currentAnnualRatePct",    null);
            response.put("currentDailyRatePercent", null);
            response.put("effectiveSince",          null);
        }
        return ResponseEntity.ok(response);
    }

    // ── GET /api/interest-rates ───────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    /**
        * Returns the full interest-rate view used by rate-management screens.
        *
        * The response includes the current active rate plus a computed history that
        * labels each row as SCHEDULED, ACTIVE, or SUPERSEDED.
        *
        * @return a response map containing current-rate details and historical entries.
     */
    public ResponseEntity<Map<String, Object>> getRates() {
        LocalDate today = LocalDate.now();

        Optional<InterestRate> currentOpt =
            interestRateRepository
                .findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(today);

        List<InterestRate> allRates =
            interestRateRepository.findAllByOrderByEffectiveFromDesc();

        // Diagnostic: log what we found so debugging is easy
        log.debug("GET /interest-rates: totalRows={} currentFound={} today={}",
                allRates.size(), currentOpt.isPresent(), today);

        if (allRates.isEmpty()) {
            log.warn("GET /interest-rates: interest_rates table is EMPTY. " +
                     "DataSeeder seed-demo-data flag may be false, or DB was not reset " +
                     "after DataSeeder changes. Teller should set a rate via Interest Rate Manager.");
        }

        Map<String, Object> response = new LinkedHashMap<>();

        if (currentOpt.isPresent()) {
            InterestRate current = currentOpt.get();
            BigDecimal annual    = current.getAnnualRate();
            BigDecimal annualPct = annual.multiply(BigDecimal.valueOf(100))
                                         .setScale(4, RoundingMode.HALF_UP);
            BigDecimal dailyPct  = annual.divide(BigDecimal.valueOf(365), 8, RoundingMode.HALF_UP)
                                         .multiply(BigDecimal.valueOf(100))
                                         .setScale(6, RoundingMode.HALF_UP);

            log.debug("Current rate: {}% effective {}", annualPct, current.getEffectiveFrom());

            response.put("hasRate",                true);
            response.put("currentAnnualRate",       annual);       // decimal  e.g. 0.035000
            response.put("currentAnnualRatePct",    annualPct);    // percent  e.g. 3.5000
            response.put("currentDailyRatePercent", dailyPct);     // percent  e.g. 0.009589
            response.put("effectiveSince",          current.getEffectiveFrom().toString());
        } else {
            response.put("hasRate",                false);
            response.put("currentAnnualRate",       null);
            response.put("currentAnnualRatePct",    null);
            response.put("currentDailyRatePercent", null);
            response.put("effectiveSince",          null);
        }

        response.put("history", buildHistory(allRates, today));
        return ResponseEntity.ok(response);
    }

    // ── POST /api/interest-rates ──────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    /**
     * Creates a new savings interest-rate row effective on the requested date.
     *
     * Validation rules enforced here:
     * - annualRate must be present, numeric, and greater than zero.
     * - annualRate cannot exceed 25% (RBF guideline).
     * - effectiveFrom must be today or a future date (no backdating).
     * - only one rate is allowed per effective date.
     *
     * After saving, this method broadcasts the change to savings customers and
     * returns a summary payload for the UI.
     *
     * @param body request payload containing annualRate, effectiveFrom, and optional changeReason.
     * @param userDetails authenticated operator making the change.
     * @return a response map with the saved row details and status message.
     */
    public ResponseEntity<Map<String, Object>> setRate(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails userDetails) {

        Object rateObj = body.get("annualRate");
        if (rateObj == null) throw new BadRequestException("annualRate is required.");
        BigDecimal annualRate;
        try {
            annualRate = new BigDecimal(rateObj.toString()).setScale(6, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new BadRequestException("annualRate must be a valid number (e.g. 0.035 for 3.5%).");
        }
        if (annualRate.compareTo(BigDecimal.ZERO) <= 0)
            throw new BadRequestException("Annual rate must be greater than 0.");
        // if (annualRate.compareTo(BigDecimal.ONE) > 0)
        //     throw new BadRequestException("Annual rate cannot exceed 100% (1.0 decimal).");
            if (annualRate.compareTo(new BigDecimal("0.25")) > 0)
                throw new BadRequestException("Annual rate cannot exceed 25% per RBF guidelines.");
        Object dateObj = body.get("effectiveFrom");
        if (dateObj == null) throw new BadRequestException("effectiveFrom date is required.");
        LocalDate effectiveFrom;
        try { effectiveFrom = LocalDate.parse(dateObj.toString()); }
        catch (Exception e) { throw new BadRequestException("effectiveFrom must be ISO date e.g. 2026-04-01."); }

        LocalDate today = LocalDate.now();

        if (effectiveFrom.isBefore(today))
            throw new BadRequestException(
                "Backdating is not allowed. The effective date must be today ("
                + today + ") or a future date.");

        if (interestRateRepository.existsByEffectiveFrom(effectiveFrom))
            throw new BadRequestException(
                "A rate already exists for " + effectiveFrom + ". Choose a different date.");

        String reason = body.get("changeReason") != null
                ? body.get("changeReason").toString().trim() : null;
        if (reason != null && reason.isBlank()) reason = null;

        InterestRate saved = interestRateRepository.save(InterestRate.builder()
                .annualRate(annualRate)
                .effectiveFrom(effectiveFrom)
                .changeReason(reason)
                .setBy(userDetails.getUsername())
                .build());

        log.info("Interest rate saved: {}% effective {} by {}",
                annualRate.multiply(BigDecimal.valueOf(100)).toPlainString(),
                effectiveFrom, userDetails.getUsername());

        BigDecimal pct      = annualRate.multiply(BigDecimal.valueOf(100));
        boolean    isFuture = effectiveFrom.isAfter(today);
        String     status   = isFuture ? "SCHEDULED" : "ACTIVE";

        Optional<InterestRate> prevOpt =
            interestRateRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                effectiveFrom.minusDays(1));
        BigDecimal prevRate = prevOpt.map(InterestRate::getAnnualRate).orElse(BigDecimal.ZERO);

        try {
            notificationService.broadcastInterestRateChange(
                AccountType.SAVINGS.name(), prevRate, annualRate, effectiveFrom);
        } catch (Exception e) {
            log.error("Interest rate broadcast failed: {} — {}",
                    e.getClass().getSimpleName(), e.getMessage(), e);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id",            saved.getId());
        result.put("annualRate",    annualRate);
        result.put("annualRatePct", pct.setScale(4, RoundingMode.HALF_UP));
        result.put("effectiveFrom", effectiveFrom.toString());
        result.put("status",        status);
        result.put("message", isFuture
            ? "Rate of " + pct.stripTrailingZeros().toPlainString()
              + "% p.a. scheduled for " + effectiveFrom + ". All savings customers notified."
            : "Rate of " + pct.stripTrailingZeros().toPlainString()
              + "% p.a. is now active. All savings customers notified.");
        return ResponseEntity.ok(result);
    }

    // ── buildHistory ──────────────────────────────────────────────────────────

    /**
     * Builds a presentation-friendly history list from raw interest-rate rows.
     *
     * The input is expected in descending effectiveFrom order. Entries are split
     * into scheduled (future) and effective (today/past) groups, then each row is
     * labeled with a status and calculated effectiveTo when applicable.
     *
     * @param allRates all saved interest-rate rows ordered by effectiveFrom descending.
     * @param today reference date used to determine whether rows are scheduled or effective.
     * @return a list of history rows ready to serialize in API responses.
     */
    private List<Map<String, Object>> buildHistory(List<InterestRate> allRates, LocalDate today) {
        List<InterestRate> effective = new ArrayList<>();
        List<InterestRate> scheduled = new ArrayList<>();
        for (InterestRate r : allRates) {
            if (r.getEffectiveFrom().isAfter(today)) scheduled.add(r);
            else                                      effective.add(r);
        }

        List<Map<String, Object>> result = new ArrayList<>();

        for (InterestRate r : scheduled) {
            result.add(buildRow(r, "SCHEDULED", null));
        }

        for (int i = 0; i < effective.size(); i++) {
            InterestRate r         = effective.get(i);
            String       s         = (i == 0) ? "ACTIVE" : "SUPERSEDED";
            LocalDate    effectiveTo = null;
            if (i == 0) {
                if (!scheduled.isEmpty()) {
                    effectiveTo = scheduled.get(scheduled.size() - 1).getEffectiveFrom().minusDays(1);
                }
            } else {
                effectiveTo = effective.get(i - 1).getEffectiveFrom().minusDays(1);
            }
            result.add(buildRow(r, s, effectiveTo));
        }

        return result;
    }

    /**
     * Converts an {@link InterestRate} entity into the API row format.
     *
     * Derived percentage values are computed here so all callers return the same
     * numeric formatting and field shape.
     *
     * @param r the interest-rate row to transform.
     * @param status computed status label (SCHEDULED, ACTIVE, or SUPERSEDED).
     * @param effectiveTo optional derived end date for the row; null when open-ended.
     * @return a map representing one history entry in the response payload.
     */
    private Map<String, Object> buildRow(InterestRate r, String status, LocalDate effectiveTo) {
        BigDecimal annual    = r.getAnnualRate();
        BigDecimal annualPct = annual.multiply(BigDecimal.valueOf(100))
                                     .setScale(4, RoundingMode.HALF_UP);
        BigDecimal dailyRate = annual.divide(BigDecimal.valueOf(365), 8, RoundingMode.HALF_UP);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id",              r.getId());
        row.put("annualRate",      annual);
        row.put("annualRatePct",   annualPct);
        row.put("annualRatePercent", annualPct);
        row.put("dailyRate",       dailyRate);
        row.put("effectiveFrom",   r.getEffectiveFrom().toString());
        row.put("effectiveTo",     effectiveTo != null ? effectiveTo.toString() : null);
        row.put("status",          status);
        row.put("setBy",           r.getSetBy());
        row.put("changeReason",    r.getChangeReason());
        return row;
    }
}
