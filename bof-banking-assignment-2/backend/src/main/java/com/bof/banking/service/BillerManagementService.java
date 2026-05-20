package com.bof.banking.service;

import com.bof.banking.dto.biller.BillerUpsertRequest;
import com.bof.banking.model.Biller;

import java.util.List;

/**
 * Service interface for teller biller management operations.
 */
public interface BillerManagementService {

    List<Biller> getAllBillersForManagement();

    Biller getBillerById(Long id);

    Biller createBiller(BillerUpsertRequest request);

    Biller updateBiller(Long id, BillerUpsertRequest request);

    Biller deactivateBiller(Long id);

    Biller activateBiller(Long id);
}
