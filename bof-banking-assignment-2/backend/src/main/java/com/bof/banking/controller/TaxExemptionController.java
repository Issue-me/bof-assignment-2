package com.bof.banking.controller;

import com.bof.banking.dto.tax.RiwtExemptionResponse;
import com.bof.banking.service.impl.RiwtExemptionServiceImpl;
import com.bof.banking.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/tax")
@RequiredArgsConstructor
/**
 * API endpoints for RIWT exemption workflows and related tax notifications.
 *
 * The controller keeps request validation and response shaping close to the API
 * while delegating business decisions to the exemption service.
 */
public class TaxExemptionController {

    private final RiwtExemptionServiceImpl exemptionService;
    private final NotificationService      notificationService;

    // ── CUSTOMER: upload certificate ──────────────────────────────────
    // POST /api/tax/riwt-exemption/upload
    @PostMapping(value = "/riwt-exemption/upload", consumes = "multipart/form-data")
    /**
     * Uploads an RIWT exemption certificate for the authenticated customer.
     *
     * Performs basic file/type/size checks before forwarding the request to the
     * service layer for persistence and workflow handling.
     *
     * @param userDetails authenticated user context.
     * @param file uploaded certificate file (PDF or image).
     * @param year target tax year; defaults to current year when omitted/zero.
     * @param riwtWithheld RIWT amount already withheld for the year.
     * @return success or validation message payload.
     */
    public ResponseEntity<?> uploadRiwtExemption(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "year",         defaultValue = "0") int year,
            @RequestParam(value = "riwtWithheld", defaultValue = "0") BigDecimal riwtWithheld) {

        String userEmail = userDetails.getUsername();
        int taxYear = year > 0 ? year : LocalDate.now().getYear();

        // Validate before touching the service
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "No file provided."));
        }
        if (file.getSize() > 10 * 1024 * 1024L) {
            return ResponseEntity.badRequest().body(Map.of("message", "File exceeds 10 MB."));
        }
        String ct = file.getContentType();
        if (ct == null || (!ct.startsWith("image/") && !ct.equals("application/pdf"))) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Only PDF, JPG, or PNG files are accepted."));
        }

        // Delegate to service — any exception propagates to GlobalExceptionHandler
        // which will return a proper JSON error body instead of a raw 500
        exemptionService.submit(userEmail, taxYear, file, riwtWithheld);

        log.info("RIWT certificate upload complete: user={} year={}", userEmail, taxYear);
        return ResponseEntity.ok(Map.of(
            "message", "Certificate uploaded successfully. "
                     + "Bank of Fiji will review and update your account within 2–3 business days."
        ));
    }

    // ── ADMIN / TELLER: list all submissions ──────────────────────────
    // GET /api/tax/riwt-exemption/pending?status=PENDING   (or omit for all)
    @GetMapping("/riwt-exemption/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    /**
     * Lists RIWT exemption submissions for review.
     *
     * @param status optional status filter (for example PENDING/APPROVED/REJECTED).
     * @return exemption submissions matching the filter.
     */
    public ResponseEntity<List<RiwtExemptionResponse>> listExemptions(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(exemptionService.findAll(status));
    }

    // ── ADMIN / TELLER: approve ───────────────────────────────────────
    // POST /api/tax/riwt-exemption/{customerEmail}/approve
    // Body (optional): { "taxYear": 2026 }
    @PostMapping("/riwt-exemption/{customerEmail}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    /**
     * Approves a customer's RIWT exemption for a given tax year.
     *
     * @param customerEmail customer email used to locate the exemption record.
     * @param body optional request body containing taxYear.
     * @param tellerDetails authenticated staff user performing the approval.
     * @return confirmation message for the approval action.
     */
    public ResponseEntity<Map<String, String>> approveRiwtExemption(
            @PathVariable String customerEmail,
            @RequestBody(required = false) Map<String, Object> body,
            @AuthenticationPrincipal UserDetails tellerDetails) {

        int taxYear = body != null && body.get("taxYear") instanceof Number n
                ? n.intValue() : LocalDate.now().getYear();

        exemptionService.approveByEmailAndYear(
                customerEmail, taxYear, tellerDetails.getUsername());

        return ResponseEntity.ok(Map.of(
            "message", "RIWT exemption approved for " + customerEmail
                     + " (tax year " + taxYear + "). Customer has been notified."
        ));
    }

    // ── ADMIN / TELLER: reject ────────────────────────────────────────
    // POST /api/tax/riwt-exemption/{customerEmail}/reject
    // Body: { "taxYear": 2026, "reason": "..." }
    @PostMapping("/riwt-exemption/{customerEmail}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    /**
     * Rejects a customer's RIWT exemption request with a reason.
     *
     * @param customerEmail customer email used to locate the exemption record.
     * @param body request body with taxYear and optional rejection reason.
     * @param tellerDetails authenticated staff user performing the rejection.
     * @return confirmation message for the rejection action.
     */
    public ResponseEntity<Map<String, String>> rejectRiwtExemption(
            @PathVariable String customerEmail,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails tellerDetails) {

        int taxYear = body.get("taxYear") instanceof Number n
                ? n.intValue() : LocalDate.now().getYear();

        String reason = body.getOrDefault("reason",
                "Your certificate could not be verified. "
              + "Please resubmit a valid FRCS Certificate of Exemption.").toString();

        exemptionService.rejectByEmailAndYear(
                customerEmail, taxYear, tellerDetails.getUsername(), reason);

        return ResponseEntity.ok(Map.of(
            "message", "RIWT exemption rejected for " + customerEmail
                     + " (tax year " + taxYear + "). Customer has been notified."
        ));
    }

    // ── ADMIN / TELLER: download uploaded certificate file ────────────
    // GET /api/tax/riwt-exemption/{id}/file
    @GetMapping("/riwt-exemption/{id}/file")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    /**
        * Streams the uploaded exemption file by submission id.
        *
        * Content type is inferred from filename extension for inline preview support.
        *
        * @param id exemption submission id.
        * @return binary file response with content-disposition and content-type headers.
     */
    public ResponseEntity<byte[]> downloadFile(@PathVariable Long id) {
        byte[] bytes    = exemptionService.loadFile(id);
        String fileName = exemptionService.getFileName(id);

        String ct = "application/octet-stream";
        if (fileName != null) {
            String lower = fileName.toLowerCase();
            if (lower.endsWith(".pdf"))                          ct = "application/pdf";
            else if (lower.endsWith(".png"))                     ct = "image/png";
            else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) ct = "image/jpeg";
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + (fileName != null ? fileName : "certificate") + "\"")
                .contentType(MediaType.parseMediaType(ct))
                .body(bytes);
    }

    // ── ADMIN: broadcast interest rate change ─────────────────────────
    // POST /api/tax/interest-rate-broadcast
    // Body: { "accountType":"SAVINGS","oldRate":"0.025","newRate":"0.030","effectiveDate":"2026-04-01" }
    @PostMapping("/interest-rate-broadcast")
    @PreAuthorize("hasRole('ADMIN')")
    /**
     * Sends an interest-rate change notification to account holders.
     *
     * @param body payload containing accountType, oldRate, newRate, and effectiveDate.
     * @return confirmation message for the broadcast operation.
     */
    public ResponseEntity<Map<String, String>> broadcastRateChange(
            @RequestBody Map<String, String> body) {

        String accountType  = body.get("accountType");
        String oldRateStr   = body.get("oldRate");
        String newRateStr   = body.get("newRate");
        String effectiveStr = body.get("effectiveDate");

        if (accountType == null || oldRateStr == null || newRateStr == null || effectiveStr == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "message", "accountType, oldRate, newRate, and effectiveDate are all required."
            ));
        }

        notificationService.broadcastInterestRateChange(
                accountType,
                new BigDecimal(oldRateStr),
                new BigDecimal(newRateStr),
                LocalDate.parse(effectiveStr)
        );

        return ResponseEntity.ok(Map.of(
            "message", "Broadcast sent to all " + accountType + " account holders."
        ));
    }
}
