package com.bof.banking.service;

import com.bof.banking.dto.transaction.TransferLimitSummaryResponse;
import com.bof.banking.model.User;

import java.math.BigDecimal;

/**
 * Service for transfer limit enforcement and reporting.
 */
public interface TransferLimitService {

    void validateTransfer(User user, boolean ownAccountTransfer, BigDecimal amount);

    TransferLimitSummaryResponse getSummary(String userEmail);
}
