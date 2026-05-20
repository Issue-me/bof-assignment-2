package com.bof.banking.controller;

import com.bof.banking.model.SavingsInterestTransaction;
import com.bof.banking.repository.InterestSummaryRepository;
import com.bof.banking.repository.SavingsInterestTransactionRepository;
import com.bof.banking.service.impl.SavingsInterestService;
import com.bof.banking.service.impl.SavingsInterestService.InterestRunResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/admin/interest")
@RequiredArgsConstructor
/**
 * Coordinates API workflows for ssavings interest controller and maps service outcomes to HTTP responses.
 */
public class SavingsInterestController {

    private final SavingsInterestService               interestService;
    private final SavingsInterestTransactionRepository interestTxRepository;
        private final InterestSummaryRepository            interestSummaryRepository;

    // ── POST /api/admin/interest/run ─────────────────────────────────────────

    @PostMapping("/run")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> runInterest(
            @RequestBody(required = false) Map<String, Integer> body) {

        Integer month = body != null ? body.get("month") : null;
        Integer year  = body != null ? body.get("year")  : null;

        log.info("Manual interest run requested: month={} year={}", month, year);
        InterestRunResult result = interestService.runManual(month, year);

        // FIX: Map.of() only supports up to 10 entries — use LinkedHashMap for 11+
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success",    true);
        response.put("summary",    result.summary());
        response.put("month",      result.month());
        response.put("year",       result.year());
        response.put("credited",   result.accountsCredited());
        response.put("skipped",    result.accountsSkipped() + result.zeroRateSkipped());
        response.put("duplicate",  result.duplicatesSkipped());
        response.put("totalGross", result.totalGrossInterest());
        response.put("totalRiwt",  result.totalRiwtDeducted());
        response.put("totalNet",   result.totalNetInterestPaid());
        return ResponseEntity.ok(response);
    }

    // ── GET /api/admin/interest/history?month=3&year=2026 ────────────────────

    @GetMapping("/history")
        @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<List<Map<String, Object>>> getRunHistory(
            @RequestParam(defaultValue = "0") int month,
            @RequestParam(defaultValue = "0") int year) {

        LocalDate now = LocalDate.now();
        int m = month > 0 ? month : now.getMonthValue();
        int y = year  > 0 ? year  : now.getYear();

        List<SavingsInterestTransaction> txns =
                interestTxRepository.findByInterestMonthAndInterestYear(m, y);

        // FIX: use LinkedHashMap — Map.of() with 11 keys exceeds the overload limit
        List<Map<String, Object>> rows = txns.stream().map(t -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id",              t.getId());
            row.put("accountNumber",   t.getAccount().getAccountNumber());
            row.put("customerEmail",   t.getAccount().getUser() != null
                                           ? t.getAccount().getUser().getEmail() : "");
            row.put("balanceSnapshot", t.getBalanceSnapshot());
            row.put("annualRate",      t.getAnnualRate());
            row.put("grossInterest",   t.getGrossInterest());
            row.put("riwtDeducted",    t.getRiwtDeducted());
            row.put("netInterest",     t.getNetInterest());
            row.put("riwtExempt",      t.isRiwtExempt());
            row.put("reference",       t.getReferenceNumber());
            row.put("creditedAt",      t.getCreditedAt().toString());
            return row;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(rows);
    }

    // ── GET /api/admin/interest/summary?year=2026 ────────────────────────────

    @GetMapping("/summary")
        @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<Map<String, Object>> getSummary(
            @RequestParam(defaultValue = "0") int year) {

        int y = year > 0 ? year : LocalDate.now().getYear();

        List<SavingsInterestTransaction> allThisYear =
                interestTxRepository.findAll().stream()
                        .filter(t -> t.getInterestYear() == y)
                        .collect(Collectors.toList());

        var totalGross = allThisYear.stream()
                .map(SavingsInterestTransaction::getGrossInterest)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        var totalRiwt = allThisYear.stream()
                .map(SavingsInterestTransaction::getRiwtDeducted)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        var totalNet = allThisYear.stream()
                .map(SavingsInterestTransaction::getNetInterest)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        var yearTotals = interestSummaryRepository.aggregateTotalsForYear(y);
        var totalNrwht = yearTotals != null && yearTotals.getTotalNrwht() != null
                ? yearTotals.getTotalNrwht()
                : java.math.BigDecimal.ZERO;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("year",       y);
        response.put("totalGross", totalGross);
        response.put("totalRiwt",  totalRiwt);
                response.put("totalNrwht", totalNrwht);
        response.put("totalNet",   totalNet);
        response.put("txnCount",   allThisYear.size());
        return ResponseEntity.ok(response);
    }
}
