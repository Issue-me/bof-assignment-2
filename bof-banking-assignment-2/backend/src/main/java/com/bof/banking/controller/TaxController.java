// package com.bof.banking.controller;

// import com.bof.banking.dto.tax.FrcsInterestSummaryReport;
// import com.bof.banking.dto.tax.TaxReportResponse;
// import com.bof.banking.dto.tax.TaxSubmitResponse;
// import com.bof.banking.scheduler.InterestSummaryScheduler;
// import com.bof.banking.service.TaxService;
// import lombok.RequiredArgsConstructor;
// import org.springframework.http.ResponseEntity;
// import org.springframework.security.access.prepost.PreAuthorize;
// import org.springframework.security.core.annotation.AuthenticationPrincipal;
// import org.springframework.security.core.userdetails.UserDetails;
// import org.springframework.web.bind.annotation.*;

// import java.util.Map;

// /**
//  * REST controller for tax report endpoints.
//  *
//  * <p>Follows the same authentication pattern as the rest of the main branch —
//  * {@code @AuthenticationPrincipal UserDetails} resolves to the logged-in
//  * {@link com.bof.banking.model.User} via their email address.
//  *
//  * <h3>User-facing endpoints</h3>
//  * <ul>
//  *   <li>{@code GET  /api/tax/report?year=2024}  – fetch the user's annual tax report</li>
//  *   <li>{@code POST /api/tax/submit?year=2024}  – submit the tax return to FRCS</li>
//  * </ul>
//  *
//  * <h3>Admin-only endpoints</h3>
//  * <ul>
//  *   <li>{@code GET  /api/tax/frcs/interest-summary?year=2024} – full FRCS interest report</li>
//  *   <li>{@code POST /api/tax/admin/recalculate?year=2024}     – force-recalculate all summaries</li>
//  * </ul>
//  */
// @RestController
// @RequestMapping("/api/tax")
// @RequiredArgsConstructor
// public class TaxController {

//     private final TaxService               taxService;
//     private final InterestSummaryScheduler interestSummaryScheduler;

//     // ── User endpoints ────────────────────────────────────────────────────

//     /**
//      * Returns the authenticated user's annual tax report.
//      * Always computed from live transactions — status is "DRAFT" until submitted.
//      *
//      * @param year defaults to the current calendar year
//      */
//     @GetMapping("/report")
//     public ResponseEntity<TaxReportResponse> getTaxReport(
//             @AuthenticationPrincipal UserDetails userDetails,
//             @RequestParam(defaultValue = "#{T(java.time.Year).now().getValue()}") int year) {

//         return ResponseEntity.ok(taxService.getReport(userDetails.getUsername(), year));
//     }

//     /**
//      * Submits the user's tax return for the given year.
//      * Persists the {@link com.bof.banking.model.UserInterestSummary} record
//      * and returns a unique FRCS reference number.
//      *
//      * @param year defaults to the current calendar year
//      */
//     @PostMapping("/submit")
//     public ResponseEntity<TaxSubmitResponse> submitTaxReturn(
//             @AuthenticationPrincipal UserDetails userDetails,
//             @RequestParam(defaultValue = "#{T(java.time.Year).now().getValue()}") int year) {

//         return ResponseEntity.ok(taxService.submitReturn(userDetails.getUsername(), year));
//     }

//     // ── Admin / FRCS endpoints ────────────────────────────────────────────

//     /**
//      * Generates the mandatory end-of-year FRCS interest summary report.
//      *
//      * <p>Uses persisted {@link com.bof.banking.model.UserInterestSummary} records
//      * written by the scheduler where available; falls back to live calculation.
//      * Restricted to {@code ADMIN} role.
//      *
//      * @param year defaults to the current calendar year
//      */
//     @GetMapping("/frcs/interest-summary")
//     @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
//     public ResponseEntity<FrcsInterestSummaryReport> getFrcsInterestSummary(
//             @RequestParam(defaultValue = "#{T(java.time.Year).now().getValue()}") int year) {

//         return ResponseEntity.ok(taxService.generateFrcsInterestSummary(year));
//     }

//     /**
//      * Manually trigger a full recalculation of interest summaries for all
//      * users for the given year. Restricted to {@code ADMIN} role.
//      */
//     @PostMapping("/admin/recalculate")
//     @PreAuthorize("hasRole('ADMIN')")
//     public ResponseEntity<Map<String, Object>> recalculateInterestSummaries(
//             @RequestParam(defaultValue = "#{T(java.time.Year).now().getValue()}") int year) {

//         int count = interestSummaryScheduler.recalculateYear(year);
//         return ResponseEntity.ok(Map.of(
//                 "year", year,
//                 "usersProcessed", count,
//                 "message", "Interest summary recalculation complete for " + year
//         ));
//     }

//     /**
//      * Mark all interest summaries for the given year as submitted to FRCS.
//      * Sets {@code submittedToFrcs = true} and stamps today as the submission date
//      * on every {@link com.bof.banking.model.UserInterestSummary} for that year.
//      * Restricted to {@code ADMIN} role.
//      */
//     @PostMapping("/admin/mark-submitted")
//     @PreAuthorize("hasRole('ADMIN')")
//     public ResponseEntity<Map<String, Object>> markSubmittedToFrcs(
//             @RequestParam(defaultValue = "#{T(java.time.Year).now().getValue()}") int year) {

//         int count = interestSummaryScheduler.markAllSubmitted(year);
//         return ResponseEntity.ok(Map.of(
//                 "year", year,
//                 "summariesMarked", count,
//                 "message", "All " + year + " interest summaries marked as submitted to FRCS"
//         ));
//     }
// }

package com.bof.banking.controller;

import com.bof.banking.dto.tax.FrcsInterestSummaryReport;
import com.bof.banking.dto.tax.TaxReportResponse;
import com.bof.banking.dto.tax.TaxSubmitResponse;
import com.bof.banking.exception.BadRequestException;
import com.bof.banking.exception.ResourceNotFoundException;
import com.bof.banking.model.User;
import com.bof.banking.model.UserInterestSummary;
import com.bof.banking.repository.InterestSummaryRepository;
import com.bof.banking.repository.UserRepository;
import com.bof.banking.scheduler.InterestSummaryScheduler;
import com.bof.banking.service.TaxService;
import com.bof.banking.service.impl.InterestSummaryCalculator;
import com.bof.banking.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/tax")
@RequiredArgsConstructor
/**
 * REST controller for tax report endpoints. Follows the same authentication pattern as the rest of the main branch — @AuthenticationPrincipal UserDetails resolves to the logged-in User via their email address. User-facing endpoints GET /api/tax/report?year=2024 – fetch the user's annual tax report POST /api/tax/submit?year=2024 – submit the tax return to FRCS Admin-only endpoints GET /api/tax/frcs/interest-summary?year=2024 – full FRCS interest report POST /api/tax/admin/recalculate?year=2024 – force-recalculate all summaries
 */
public class TaxController {

    private final TaxService                taxService;
    private final InterestSummaryScheduler  interestSummaryScheduler;
    private final InterestSummaryRepository interestSummaryRepository;
    private final InterestSummaryCalculator interestSummaryCalculator;
    private final UserRepository            userRepository;
    private final NotificationService       notificationService;

    // ── Customer endpoints ────────────────────────────────────────────────

    @GetMapping("/report")
    public ResponseEntity<TaxReportResponse> getTaxReport(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "#{T(java.time.Year).now().getValue()}") int year) {
        return ResponseEntity.ok(taxService.getReport(userDetails.getUsername(), year));
    }

    @PostMapping("/submit")
    public ResponseEntity<TaxSubmitResponse> submitTaxReturn(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "#{T(java.time.Year).now().getValue()}") int year) {
        return ResponseEntity.ok(taxService.submitReturn(userDetails.getUsername(), year));
    }

    // ── Admin / FRCS endpoints ────────────────────────────────────────────

    @GetMapping("/frcs/interest-summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    public ResponseEntity<FrcsInterestSummaryReport> getFrcsInterestSummary(
            @RequestParam(defaultValue = "#{T(java.time.Year).now().getValue()}") int year) {
        return ResponseEntity.ok(taxService.generateFrcsInterestSummary(year));
    }

    @PostMapping("/admin/recalculate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> recalculateInterestSummaries(
            @RequestParam(defaultValue = "#{T(java.time.Year).now().getValue()}") int year) {
        int count = interestSummaryScheduler.recalculateYear(year);
        return ResponseEntity.ok(Map.of(
                "year", year,
                "usersProcessed", count,
                "message", "Interest summary recalculation complete for " + year
        ));
    }

    @PostMapping("/admin/mark-submitted")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> markSubmittedToFrcs(
            @RequestParam(defaultValue = "#{T(java.time.Year).now().getValue()}") int year) {
        int count = interestSummaryScheduler.markAllSubmitted(year);
        return ResponseEntity.ok(Map.of(
                "year", year,
                "summariesMarked", count,
                "message", "All " + year + " interest summaries marked as submitted to FRCS"
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────────
// Replace ONLY the getSubmissions() method in your TaxController.java
//
// THE BUG: the existing code calls calculateAndSave() for ALL users when the
// summaries list is empty. calculateAndSave() recalculates from the user's
// CURRENT profile. After Adrian registers a TIN, the profile shows
// resident=true + tin=set → the calculator classifies everything as RIWT,
// overwriting the correct NRWHT data that DataSeeder seeded.
//
// THE FIX: only call calculateAndSave() for users who have NO existing summary.
// Never overwrite a summary that already exists.
// ─────────────────────────────────────────────────────────────────────────────

    @GetMapping("/admin/submissions")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    public ResponseEntity<List<Map<String, Object>>> getSubmissions(
            @RequestParam(defaultValue = "#{T(java.time.Year).now().getValue()}") int year) {

        List<UserInterestSummary> summaries = interestSummaryRepository.findByTaxYear(year);

        // Only generate summaries for users who don't have one yet.
        // NEVER overwrite existing summaries — they contain historical data
        // (e.g. NRWHT charged before TIN was registered) that cannot be
        // recalculated correctly from the current user profile.
        if (summaries.isEmpty()) {
            List<User> allUsers = userRepository.findAll();
            for (User u : allUsers) {
                try {
                    // KEY FIX: check before calculating — skip if summary exists
                    boolean alreadyExists = interestSummaryRepository
                            .findByUserAndTaxYear(u, year).isPresent();
                    if (!alreadyExists) {
                        interestSummaryCalculator.calculateAndSave(u, year);
                    }
                } catch (Exception ex) {
                    log.warn("Could not calculate summary for userId={}: {}",
                            u.getId(), ex.getMessage());
                }
            }
            summaries = interestSummaryRepository.findByTaxYear(year);
        }

        List<Map<String, Object>> result = summaries.stream()
            .filter(s -> s.getUser() != null)
            .filter(s -> s.getGrossInterestEarned() != null
                      && s.getGrossInterestEarned().compareTo(java.math.BigDecimal.ZERO) > 0)
            .map(s -> {
                User u = s.getUser();
                String status;
                if (Boolean.TRUE.equals(s.isSubmittedToFrcs()))   status = "SUBMITTED";
                else if (Boolean.TRUE.equals(s.isCustomerSubmitted())) status = "PENDING_FRCS";
                else                                                   status = "DRAFT";

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("userEmail",             u.getEmail());
                row.put("customerId",            u.getCustomerId());
                row.put("fullName",              u.getFullName());
                row.put("tinNumber",             u.getTinNumber());
                row.put("isResident",            u.isResident());
                row.put("status",                status);
                row.put("frcsReference",         s.getFrcsReference());
                row.put("frcsSubmissionDate",    s.getFrcsSubmissionDate());
                row.put("customerSubmittedDate", s.getCustomerSubmittedDate());
                row.put("interestEarned",        s.getGrossInterestEarned());
                row.put("riwtWithheld",          s.getRiwtWithheld());
                row.put("nrwhtWithheld",         s.getNrwhtWithheld());
                row.put("netInterestPaid",       s.getNetInterestPaid());
                row.put("totalTaxOwed",          s.getRiwtWithheld().add(s.getNrwhtWithheld()));
                row.put("exemptionReason",       s.getExemptionReason());
                return row;
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // // ── NEW: POST /api/tax/admin/submit-to-frcs ───────────────────────────
    // // Admin submits one customer's report to FRCS.
    // // Generates FRCS receipt, saves it, fires FRCS_TAX_SUBMITTED notification.
    // // Body: { "userEmail": "customer@email.com", "year": 2026 }

    // @PostMapping("/admin/submit-to-frcs")
    // @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    // @Transactional
    // public ResponseEntity<Map<String, Object>> adminSubmitToFrcs(
    //         @RequestBody Map<String, Object> body,
    //         @AuthenticationPrincipal UserDetails adminDetails) {

    //     String userEmail = body.get("userEmail") instanceof String s ? s.trim() : null;
    //     int    year      = body.get("year") instanceof Number n ? n.intValue() : LocalDate.now().getYear();

    //     if (userEmail == null || userEmail.isBlank())
    //         throw new BadRequestException("userEmail is required.");

    //     User customer = userRepository.findByEmail(userEmail)
    //             .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEmail));

    //     // Ensure the interest summary exists — create it if not
    //     // interestSummaryCalculator.calculateAndSave(customer, year);
    //     boolean summaryExists = interestSummaryRepository.findByUserAndTaxYear(customer, year).isPresent();
    //     if (!summaryExists) {
    //         interestSummaryCalculator.calculateAndSave(customer, year);
    //     }

    //     UserInterestSummary summary = interestSummaryRepository
    //             .findByUserAndTaxYear(customer, year)
    //             .orElseThrow(() -> new ResourceNotFoundException(
    //                     "No interest summary found for user=" + userEmail + " year=" + year));

    //     if (Boolean.TRUE.equals(summary.isSubmittedToFrcs())) {
    //         // Already submitted — return the existing reference
    //         Map<String, Object> already = new LinkedHashMap<>();
    //         already.put("frcsReference",     summary.getFrcsReference());
    //         already.put("userEmail",         userEmail);
    //         already.put("year",              year);
    //         already.put("frcsSubmissionDate", summary.getFrcsSubmissionDate());
    //         already.put("message", "Already submitted to FRCS. Ref: " + summary.getFrcsReference());
    //         already.put("alreadySubmitted",  true);
    //         return ResponseEntity.ok(already);
    //     }

    //     // Generate unique FRCS receipt number
    //     String frcsReference = "FRCS-" + year + "-"
    //             + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();

    //     // Persist the submission
    //     summary.setSubmittedToFrcs(true);
    //     summary.setFrcsSubmissionDate(LocalDate.now());
    //     summary.setFrcsReference(frcsReference);
    //     interestSummaryRepository.save(summary);

    //     log.info("FRCS submission: admin={} customer={} year={} ref={}",
    //             adminDetails.getUsername(), userEmail, year, frcsReference);

    //     // ── Send FRCS_TAX_SUBMITTED notification to the customer ──────────
    //     // This uses NotificationType.FRCS_TAX_SUBMITTED — shows up in customer's
    //     // notification bell and sends a formatted email with the receipt number.
    //     try {
    //         notificationService.notifyFrcsTaxSubmitted(userEmail, year, frcsReference);
    //         log.info("FRCS notification sent: customer={} ref={}", userEmail, frcsReference);
    //     } catch (Exception e) {
    //         // Non-fatal — the submission is saved; notification failure is logged only
    //         log.error("FRCS notification FAILED for customer={}: {}", userEmail, e.getMessage(), e);
    //     }

    //     Map<String, Object> result = new LinkedHashMap<>();
    //     result.put("frcsReference",      frcsReference);
    //     result.put("userEmail",          userEmail);
    //     result.put("customerName",       customer.getFullName());
    //     result.put("year",               year);
    //     result.put("frcsSubmissionDate", LocalDate.now().toString());
    //     result.put("alreadySubmitted",   false);
    //     result.put("message",
    //             "Report submitted to FRCS. Customer notified. Ref: " + frcsReference);
    //     return ResponseEntity.ok(result);
    // }

    // The original called calculateAndSave() unconditionally, which overwrites
    // Adrian's seeded NRWHT summary with a freshly computed RIWT summary (because
    // her profile now has a TIN). Add the exists-check guard.

    @PostMapping("/admin/submit-to-frcs")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Transactional
    public ResponseEntity<Map<String, Object>> adminSubmitToFrcs(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails adminDetails) {

        String userEmail = body.get("userEmail") instanceof String s ? s.trim() : null;
        int    year      = body.get("year") instanceof Number n ? n.intValue() : LocalDate.now().getYear();

        if (userEmail == null || userEmail.isBlank())
            throw new BadRequestException("userEmail is required.");

        User customer = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEmail));

        // ── GUARD: only create a summary if none exists ───────────────────────────
        // NEVER call calculateAndSave() when a summary already exists — it would
        // recalculate from the current user profile and overwrite historical NRWHT
        // data with RIWT for customers who registered their TIN mid-year.
        boolean summaryExists = interestSummaryRepository
                .findByUserAndTaxYear(customer, year).isPresent();
        if (!summaryExists) {
            interestSummaryCalculator.calculateAndSave(customer, year);
        }

        UserInterestSummary summary = interestSummaryRepository
                .findByUserAndTaxYear(customer, year)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No interest summary found for user=" + userEmail + " year=" + year));

        if (Boolean.TRUE.equals(summary.isSubmittedToFrcs())) {
            Map<String, Object> already = new LinkedHashMap<>();
            already.put("frcsReference",      summary.getFrcsReference());
            already.put("userEmail",          userEmail);
            already.put("year",               year);
            already.put("frcsSubmissionDate", summary.getFrcsSubmissionDate());
            already.put("message", "Already submitted. Ref: " + summary.getFrcsReference());
            already.put("alreadySubmitted",   true);
            return ResponseEntity.ok(already);
        }

        String frcsReference = "FRCS-" + year + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();

        summary.setSubmittedToFrcs(true);
        summary.setFrcsSubmissionDate(LocalDate.now());
        summary.setFrcsReference(frcsReference);
        interestSummaryRepository.save(summary);

        log.info("FRCS submission: admin={} customer={} year={} ref={}",
                adminDetails.getUsername(), userEmail, year, frcsReference);

        try {
            notificationService.notifyFrcsTaxSubmitted(userEmail, year, frcsReference);
        } catch (Exception e) {
            log.error("FRCS notification failed for {}: {}", userEmail, e.getMessage(), e);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("frcsReference",      frcsReference);
        result.put("userEmail",          userEmail);
        result.put("customerName",       customer.getFullName());
        result.put("year",               year);
        result.put("frcsSubmissionDate", LocalDate.now().toString());
        result.put("alreadySubmitted",   false);
        result.put("message", "Submitted to FRCS. Customer notified. Ref: " + frcsReference);
        return ResponseEntity.ok(result);
    }

    // ── NEW: GET /api/tax/admin/report?userEmail=x&year=2026 ─────────────
    // Returns the full TaxReportResponse for any customer.
    // Used by the AdminTaxReport side drawer.

    @GetMapping("/admin/report")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    public ResponseEntity<TaxReportResponse> getCustomerReport(
            @RequestParam String userEmail,
            @RequestParam(defaultValue = "#{T(java.time.Year).now().getValue()}") int year) {
        return ResponseEntity.ok(taxService.getReport(userEmail, year));
    }

    @GetMapping("/users/{userId}/nrwht-refund-preview")
    /**
     * Returns nrwht refund preview data.
     * @param userId the unique identifier of the target record.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<BigDecimal> getNrwhtRefundPreview(@PathVariable Long userId) {
        return ResponseEntity.of(taxService.getPendingNrwhtRefundAmount(userId));
    }
}
