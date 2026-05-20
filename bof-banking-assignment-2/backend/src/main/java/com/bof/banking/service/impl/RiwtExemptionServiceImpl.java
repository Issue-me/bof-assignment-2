package com.bof.banking.service.impl;

import com.bof.banking.dto.tax.RiwtExemptionResponse;
import com.bof.banking.exception.BadRequestException;
import com.bof.banking.exception.ResourceNotFoundException;
import com.bof.banking.model.RiwtExemption;
import com.bof.banking.model.User;
import com.bof.banking.model.enums.RiwtExemptionStatus;
import com.bof.banking.repository.RiwtExemptionRepository;
import com.bof.banking.repository.UserRepository;
import com.bof.banking.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RiwtExemptionServiceImpl
 *
 * FIX: File content is now stored as a BLOB in the database (fileContent byte[])
 * rather than trying to write to a file path on disk. This fixes the issue where
 * filePath was never saved to the DB because Files.createDirectories() failed on
 * Windows or the path resolved to an unwritable location.
 *
 * The RiwtExemption entity needs a `fileContent` byte[] column — see the SQL migration
 * add_riwt_file_content_column.sql.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiwtExemptionServiceImpl {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm");

    private final RiwtExemptionRepository exemptionRepository;
    private final UserRepository          userRepository;
    private final NotificationService     notificationService;

    // ── Customer: submit certificate ──────────────────────────────────────────

    @Transactional
    public RiwtExemptionResponse submit(String userEmail,
                                         int taxYear,
                                         MultipartFile file,
                                         BigDecimal riwtWithheld) {
        User user = byEmail(userEmail);

        // Block re-submission if already approved
        if (exemptionRepository.existsByUserAndTaxYearAndStatus(
                user, taxYear, RiwtExemptionStatus.APPROVED)) {
            throw new BadRequestException(
                "Your RIWT exemption for " + taxYear + " has already been approved.");
        }

        // Delete existing PENDING/REJECTED record so customer can resubmit
        exemptionRepository.findByUserAndTaxYear(user, taxYear).ifPresent(existing -> {
            log.info("Replacing {} exemption id={} for user={} year={}",
                    existing.getStatus(), existing.getId(), userEmail, taxYear);
            exemptionRepository.delete(existing);
            exemptionRepository.flush();
        });

        // FIX: store file as bytes in DB — no disk path needed
        byte[] fileContent = null;
        try {
            fileContent = file.getBytes();
        } catch (Exception e) {
            log.warn("Could not read file bytes for user={} year={}: {}", userEmail, taxYear, e.getMessage());
        }

        RiwtExemption saved = exemptionRepository.save(
            RiwtExemption.builder()
                .user(user)
                .taxYear(taxYear)
                .status(RiwtExemptionStatus.PENDING)
                .fileName(file.getOriginalFilename())
                .fileContent(fileContent)          // stored in DB as BLOB
                .filePath(null)                    // no longer used — file is in DB
                .riwtWithheld(riwtWithheld != null ? riwtWithheld : BigDecimal.ZERO)
                .build()
        );

        log.info("RIWT exemption saved: id={} user={} year={} file={}",
                saved.getId(), userEmail, taxYear, file.getOriginalFilename());
        return toResponse(saved);
    }

    // ── Admin: list all ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    /**
     * Returns all data.
     * @param statusFilter the status Filter.
     * @return the matching results.
     */
    public List<RiwtExemptionResponse> findAll(String statusFilter) {
        List<RiwtExemption> rows;
        if (statusFilter != null && !statusFilter.isBlank()) {
            try {
                RiwtExemptionStatus s = RiwtExemptionStatus.valueOf(statusFilter.toUpperCase());
                rows = exemptionRepository.findByStatusOrderBySubmittedAtDesc(s);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid status filter: " + statusFilter);
            }
        } else {
            rows = exemptionRepository.findAllByOrderBySubmittedAtDesc();
        }
        return rows.stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Admin: approve ────────────────────────────────────────────────────────

    @Transactional
    public RiwtExemptionResponse approveByEmailAndYear(String customerEmail,
                                                        int taxYear,
                                                        String tellerEmail) {
        RiwtExemption ex = getByEmailAndYear(customerEmail, taxYear);
        if (ex.getStatus() != RiwtExemptionStatus.PENDING) {
            throw new BadRequestException(
                "Only PENDING submissions can be approved. Current status: " + ex.getStatus());
        }
        ex.setStatus(RiwtExemptionStatus.APPROVED);
        ex.setReviewedBy(tellerEmail);
        ex.setReviewedAt(LocalDateTime.now());
        ex = exemptionRepository.save(ex);

        notificationService.notifyRiwtExemptionApplied(ex.getUser().getEmail(), ex.getTaxYear());
        log.info("RIWT exemption APPROVED: id={} by={}", ex.getId(), tellerEmail);
        return toResponse(ex);
    }

    // ── Admin: reject ─────────────────────────────────────────────────────────

    @Transactional
    public RiwtExemptionResponse rejectByEmailAndYear(String customerEmail,
                                                       int taxYear,
                                                       String tellerEmail,
                                                       String reason) {
        RiwtExemption ex = getByEmailAndYear(customerEmail, taxYear);
        if (ex.getStatus() != RiwtExemptionStatus.PENDING) {
            throw new BadRequestException(
                "Only PENDING submissions can be rejected. Current status: " + ex.getStatus());
        }
        ex.setStatus(RiwtExemptionStatus.REJECTED);
        ex.setReviewedBy(tellerEmail);
        ex.setReviewedAt(LocalDateTime.now());
        ex.setRejectionReason(reason);
        ex = exemptionRepository.save(ex);

        notificationService.notifyRiwtExemptionRejected(
                ex.getUser().getEmail(), ex.getTaxYear(), reason);
        log.info("RIWT exemption REJECTED: id={} by={}", ex.getId(), tellerEmail);
        return toResponse(ex);
    }

    // ── File download ──────────────────────────────────────────────────────────

    /**
     * Returns file data.
     * @param id the unique identifier of the target record.
     * @return the result of the operation.
     */
    public byte[] loadFile(Long id) {
        RiwtExemption ex = exemptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exemption not found"));
        if (ex.getFileContent() == null || ex.getFileContent().length == 0) {
            throw new ResourceNotFoundException("No file stored for this exemption");
        }
        return ex.getFileContent();
    }

    /**
     * Returns file name data.
     * @param id the unique identifier of the target record.
     * @return the resulting text value.
     */
    public String getFileName(Long id) {
        return exemptionRepository.findById(id)
                .map(RiwtExemption::getFileName)
                .orElse("certificate");
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private RiwtExemption getByEmailAndYear(String customerEmail, int taxYear) {
        User user = byEmail(customerEmail);
        return exemptionRepository.findByUserAndTaxYear(user, taxYear)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "No exemption submission found for " + customerEmail + " / " + taxYear));
    }

    private User byEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    private RiwtExemptionResponse toResponse(RiwtExemption ex) {
        User u = ex.getUser();
        String name = null;
        String email = null;
        String customerId = null;
        String tin = null;

        if (u != null) {
            email = u.getEmail();
            try { name = u.getFullName(); } catch (Exception ignored) {}
            if (name == null || name.isBlank()) {
                String fn = safeGet(u::getFirstName);
                String ln = safeGet(u::getLastName);
                name = ((fn != null ? fn : "") + " " + (ln != null ? ln : "")).trim();
            }
            customerId = safeGet(u::getCustomerId);
            tin = safeGet(u::getTinNumber);
        }

        return RiwtExemptionResponse.builder()
                .id(ex.getId())
                .customerEmail(email)
                .customerName(name)
                .customerId(customerId)
                .tinNumber(tin)
                .taxYear(ex.getTaxYear())
                .status(ex.getStatus().name())
                .fileName(ex.getFileName())
                // FIX: fileUrl uses /file endpoint which serves content from DB
                .fileUrl(ex.getFileContent() != null && ex.getFileContent().length > 0
                        ? "/api/tax/riwt-exemption/" + ex.getId() + "/file"
                        : null)
                .riwtWithheld(ex.getRiwtWithheld())
                .rejectionReason(ex.getRejectionReason())
                .reviewedBy(ex.getReviewedBy())
                .submittedDate(ex.getSubmittedAt() != null
                        ? ex.getSubmittedAt().format(FMT) : null)
                .reviewedDate(ex.getReviewedAt() != null
                        ? ex.getReviewedAt().format(FMT) : null)
                .build();
    }

    private <T> T safeGet(java.util.function.Supplier<T> s) {
        try { return s.get(); } catch (Exception e) { return null; }
    }
}
