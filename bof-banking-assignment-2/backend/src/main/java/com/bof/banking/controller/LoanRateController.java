package com.bof.banking.controller;

import com.bof.banking.exception.BadRequestException;
import com.bof.banking.model.LoanRate;
import com.bof.banking.repository.LoanRateRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manages default interest rates for each loan product type.
 *
 * GET  /api/loan-rates          — returns all loan type rates (public, read-only)
 * PATCH /api/loan-rates/{type}  — update the rate for one loan type (ADMIN/TELLER)
 *
 * Loan types: Personal Loan, Home Loan, Vehicle Loan, Business Loan
 */
@Slf4j
@RestController
@RequestMapping("/api/loan-rates")
@RequiredArgsConstructor
public class LoanRateController {

    // Default rates used if the DB row hasn't been seeded yet
    private static final Map<String, BigDecimal> DEFAULTS = Map.of(
        "Personal Loan",  new BigDecimal("0.085000"),
        "Home Loan",      new BigDecimal("0.065000"),
        "Vehicle Loan",   new BigDecimal("0.075000"),
        "Business Loan",  new BigDecimal("0.090000")
    );

    private final LoanRateRepository  loanRateRepository;
    private final NotificationService notificationService;

    // ── GET /api/loan-rates ───────────────────────────────────────────────

    @GetMapping
    /**
     * Returns all rates data.
     * @return the result of the operation.
     */
    public ResponseEntity<List<Map<String, Object>>> getAllRates() {
        List<LoanRate> saved = loanRateRepository.findAllByOrderByLoanTypeAsc();

        // Return all four loan types — fill in defaults for any missing rows
        List<Map<String, Object>> result = DEFAULTS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    String loanType = entry.getKey();
                    LoanRate row = saved.stream()
                            .filter(r -> r.getLoanType().equals(loanType))
                            .findFirst()
                            .orElse(null);

                    BigDecimal annual = row != null ? row.getAnnualRate() : entry.getValue();
                    BigDecimal annualPct = annual.multiply(BigDecimal.valueOf(100))
                            .setScale(4, RoundingMode.HALF_UP);

                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("loanType",      loanType);
                    m.put("annualRate",    annual);
                    m.put("annualRatePct", annualPct);
                    m.put("setBy",         row != null ? row.getSetBy()       : "system");
                    m.put("changeReason",  row != null ? row.getChangeReason() : null);
                    m.put("updatedAt",     row != null ? row.getUpdatedAt()    : null);
                    return m;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // ── PATCH /api/loan-rates/{loanType} ─────────────────────────────────
    // Body: { "annualRate": 0.075, "changeReason": "RBF directive Q2 2026" }

    @PatchMapping("/{loanType}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    public ResponseEntity<Map<String, Object>> updateRate(
            @PathVariable String loanType,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails ud) {

        // Decode URL-encoded type e.g. "Personal%20Loan" → "Personal Loan"
        String decodedType;
        try { decodedType = java.net.URLDecoder.decode(loanType, "UTF-8"); }
        catch (Exception e) { decodedType = loanType; }

        if (!DEFAULTS.containsKey(decodedType))
            throw new BadRequestException("Unknown loan type: " + decodedType +
                ". Valid types: " + String.join(", ", DEFAULTS.keySet()));

        Object rateObj = body.get("annualRate");
        if (rateObj == null)
            throw new BadRequestException("annualRate is required (decimal, e.g. 0.085 for 8.5%)");

        BigDecimal annualRate;
        try { annualRate = new BigDecimal(rateObj.toString()).setScale(6, RoundingMode.HALF_UP); }
        catch (NumberFormatException e) {
            throw new BadRequestException("annualRate must be a valid number e.g. 0.085");
        }

        if (annualRate.compareTo(BigDecimal.ZERO) <= 0)
            throw new BadRequestException("Annual rate must be greater than 0");
        if (annualRate.compareTo(new BigDecimal("1.0")) > 0)
            throw new BadRequestException("Annual rate cannot exceed 100% (1.0 decimal)");

        String reason = body.get("changeReason") != null
                ? body.get("changeReason").toString().trim() : null;
        if (reason != null && reason.isBlank()) reason = null;

        // Upsert — update existing row or create new one
        // Capture previous rate BEFORE overwriting so the notification can say
        // "changed from X to Y". null if this is the first time being set.
        LoanRate existing = loanRateRepository.findByLoanType(decodedType).orElse(null);
        BigDecimal prevRate = existing != null ? existing.getAnnualRate() : null;

        LoanRate row = existing != null ? existing : LoanRate.builder().loanType(decodedType).build();
        row.setAnnualRate(annualRate);
        row.setSetBy(ud.getUsername());
        row.setChangeReason(reason);
        row = loanRateRepository.save(row);

        BigDecimal annualPct = annualRate.multiply(BigDecimal.valueOf(100))
                .setScale(4, RoundingMode.HALF_UP);

        log.info("Loan rate updated: type='{}' rate={}% by={}",
                decodedType,
                annualPct.toPlainString(),
                ud.getUsername());

        // Notify customers who hold active loans of this type.
        // Non-fatal — a notification failure must not roll back the rate update.
        try {
            notificationService.notifyLoanRateChanged(
                    decodedType,
                    prevRate,     // old rate captured before save
                    annualRate,
                    ud.getUsername(),
                    reason
            );
        } catch (Exception ex) {
            log.error("notifyLoanRateChanged failed for type='{}': {}", decodedType, ex.getMessage(), ex);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("loanType",      row.getLoanType());
        result.put("annualRate",    row.getAnnualRate());
        result.put("annualRatePct", annualPct);
        result.put("setBy",         row.getSetBy());
        result.put("changeReason",  row.getChangeReason());
        result.put("updatedAt",     row.getUpdatedAt());
        result.put("message", "Rate for " + decodedType + " updated to "
                + annualPct.stripTrailingZeros().toPlainString() + "% p.a.");
        return ResponseEntity.ok(result);
    }
}
