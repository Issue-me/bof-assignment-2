package com.bof.banking.service.impl;

import com.bof.banking.dto.biller.BillerUpsertRequest;
import com.bof.banking.exception.BadRequestException;
import com.bof.banking.exception.ResourceNotFoundException;
import com.bof.banking.model.Biller;
import com.bof.banking.repository.AccountRepository;
import com.bof.banking.repository.BillerRepository;
import com.bof.banking.service.BillerManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation of teller biller management operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillerManagementServiceImpl implements BillerManagementService {

    private final BillerRepository billerRepository;
    private final AccountRepository accountRepository;

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns all billers for management data.
     * @return the matching results.
     */
    public List<Biller> getAllBillersForManagement() {
        return billerRepository.findAllByOrderByBillerNameAsc();
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns biller by id data.
     * @param id the unique identifier of the target record.
     * @return the result of the operation.
     */
    public Biller getBillerById(Long id) {
        return billerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Biller not found for ID: " + id));
    }

    @Override
    @Transactional
    /**
     * Creates biller data.
     * @param request the request payload.
     * @return the result of the operation.
     */
    public Biller createBiller(BillerUpsertRequest request) {
        String billerCode = normalizeCode(request.getBillerCode());
        String settlementAccountNumber = normalizeAccountNumber(request.getSettlementAccountNumber());

        if (billerRepository.findByBillerCodeIgnoreCase(billerCode).isPresent()) {
            throw new BadRequestException("Biller code already exists: " + billerCode);
        }

        if (billerRepository.findBySettlementAccountNumber(settlementAccountNumber).isPresent()) {
            throw new BadRequestException("Settlement account number is already used by another biller");
        }

        ensureSettlementAccountExists(settlementAccountNumber);

        Biller biller = Biller.builder()
                .billerName(request.getBillerName().trim())
                .billerCode(billerCode)
                .category(request.getCategory().trim())
                .settlementAccountNumber(settlementAccountNumber)
                .isActive(true)
                .build();

        Biller saved = billerRepository.save(biller);
        log.info("Created biller {} ({})", saved.getBillerName(), saved.getBillerCode());
        return saved;
    }

    @Override
    @Transactional
    /**
     * Updates biller values.
     * @param id the unique identifier of the target record.
     * @param request the request payload.
     * @return the result of the operation.
     */
    public Biller updateBiller(Long id, BillerUpsertRequest request) {
        Biller existing = getBillerById(id);

        String billerCode = normalizeCode(request.getBillerCode());
        String settlementAccountNumber = normalizeAccountNumber(request.getSettlementAccountNumber());

        billerRepository.findByBillerCodeIgnoreCase(billerCode)
                .filter(found -> !found.getId().equals(id))
                .ifPresent(found -> {
                    throw new BadRequestException("Biller code already exists: " + billerCode);
                });

        billerRepository.findBySettlementAccountNumber(settlementAccountNumber)
                .filter(found -> !found.getId().equals(id))
                .ifPresent(found -> {
                    throw new BadRequestException("Settlement account number is already used by another biller");
                });

        ensureSettlementAccountExists(settlementAccountNumber);

        existing.setBillerName(request.getBillerName().trim());
        existing.setBillerCode(billerCode);
        existing.setCategory(request.getCategory().trim());
        existing.setSettlementAccountNumber(settlementAccountNumber);

        Biller saved = billerRepository.save(existing);
        log.info("Updated biller {} ({})", saved.getBillerName(), saved.getBillerCode());
        return saved;
    }

    @Override
    @Transactional
    /**
     * Handles deactivate biller.
     * @param id the unique identifier of the target record.
     * @return the result of the operation.
     */
    public Biller deactivateBiller(Long id) {
        Biller existing = getBillerById(id);
        existing.setActive(false);
        Biller saved = billerRepository.save(existing);
        log.info("Deactivated biller {} ({})", saved.getBillerName(), saved.getBillerCode());
        return saved;
    }

    @Override
    @Transactional
    /**
     * Handles activate biller.
     * @param id the unique identifier of the target record.
     * @return the result of the operation.
     */
    public Biller activateBiller(Long id) {
        Biller existing = getBillerById(id);
        existing.setActive(true);
        Biller saved = billerRepository.save(existing);
        log.info("Activated biller {} ({})", saved.getBillerName(), saved.getBillerCode());
        return saved;
    }

    private void ensureSettlementAccountExists(String settlementAccountNumber) {
        if (!accountRepository.existsByAccountNumber(settlementAccountNumber)) {
            throw new BadRequestException("Settlement account not found: " + settlementAccountNumber);
        }
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase();
    }

    private String normalizeAccountNumber(String accountNumber) {
        return accountNumber.trim();
    }
}
