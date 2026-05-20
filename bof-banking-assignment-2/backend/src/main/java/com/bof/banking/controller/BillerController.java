package com.bof.banking.controller;

import com.bof.banking.dto.biller.BillerUpsertRequest;
import com.bof.banking.model.Biller;
import com.bof.banking.repository.BillerRepository;
import com.bof.banking.service.BillerManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for biller endpoints.
 */
@RestController
@RequestMapping("/api/billers")
@RequiredArgsConstructor
public class BillerController {

    private final BillerRepository billerRepository;
    private final BillerManagementService billerManagementService;

    /**
     * Retrieves all active billers.
     */
    @GetMapping
    public ResponseEntity<List<Biller>> getAllBillers() {
        List<Biller> billers = billerRepository.findByIsActiveTrue();
        return ResponseEntity.ok(billers);
    }

    @GetMapping("/management")
    @PreAuthorize("hasRole('TELLER')")
    /**
     * Returns all billers for management data.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<List<Biller>> getAllBillersForManagement() {
        return ResponseEntity.ok(billerManagementService.getAllBillersForManagement());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('TELLER')")
    /**
     * Returns biller by id data.
     * @param id the unique identifier of the target record.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<Biller> getBillerById(@PathVariable Long id) {
        return ResponseEntity.ok(billerManagementService.getBillerById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('TELLER')")
    /**
     * Creates biller data.
     * @param request the request payload.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<Biller> createBiller(@Valid @RequestBody BillerUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(billerManagementService.createBiller(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TELLER')")
    /**
     * Updates biller values.
     * @param id the unique identifier of the target record.
     * @param request the request payload.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<Biller> updateBiller(@PathVariable Long id, @Valid @RequestBody BillerUpsertRequest request) {
        return ResponseEntity.ok(billerManagementService.updateBiller(id, request));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('TELLER')")
    /**
     * Handles deactivate biller.
     * @param id the unique identifier of the target record.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<Biller> deactivateBiller(@PathVariable Long id) {
        return ResponseEntity.ok(billerManagementService.deactivateBiller(id));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('TELLER')")
    /**
     * Handles activate biller.
     * @param id the unique identifier of the target record.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<Biller> activateBiller(@PathVariable Long id) {
        return ResponseEntity.ok(billerManagementService.activateBiller(id));
    }
}
