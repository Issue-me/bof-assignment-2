package com.bof.banking.service;

import com.bof.banking.dto.account.AccountResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service interface for investment operations.
 */
public interface InvestmentService {

    AccountResponse createInvestment(String userEmail, String investmentType,
                                     BigDecimal amount, Integer termMonths, Long linkedAccountId);

    AccountResponse getInvestmentById(Long id);

    AccountResponse getInvestmentByNumber(String investmentNumber);

    List<AccountResponse> getInvestmentsByUser(String userEmail);

    Page<AccountResponse> getInvestmentsByUser(String userEmail, Pageable pageable);

    List<AccountResponse> getActiveInvestments(String userEmail);

    AccountResponse closeInvestment(Long investmentId);

    void processMaturedInvestments();
}
