package com.bof.banking.service.impl;

import com.bof.banking.dto.account.AccountResponse;
import com.bof.banking.exception.BadRequestException;
import com.bof.banking.exception.ResourceNotFoundException;
import com.bof.banking.model.Account;
import com.bof.banking.model.Investment;
import com.bof.banking.model.User;
import com.bof.banking.repository.AccountRepository;
import com.bof.banking.repository.InvestmentRepository;
import com.bof.banking.repository.UserRepository;
import com.bof.banking.service.InvestmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of InvestmentService for investment operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvestmentServiceImpl implements InvestmentService {

    private final InvestmentRepository investmentRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    private static final BigDecimal DEFAULT_INVESTMENT_RATE = new BigDecimal("0.055"); // 5.5% annual

    @Override
    @Transactional
    public AccountResponse createInvestment(String userEmail, String investmentType,
                                            BigDecimal amount, Integer termMonths, Long linkedAccountId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Account linkedAccount = null;
        if (linkedAccountId != null) {
            linkedAccount = accountRepository.findById(linkedAccountId)
                    .orElseThrow(() -> new ResourceNotFoundException("Linked account not found"));

            if (linkedAccount.getBalance().compareTo(amount) < 0) {
                throw new BadRequestException("Insufficient funds in linked account");
            }

            linkedAccount.setBalance(linkedAccount.getBalance().subtract(amount));
            accountRepository.save(linkedAccount);
        }

        Investment investment = Investment.builder()
                .investmentNumber(generateInvestmentNumber())
                .investmentType(investmentType)
                .principalAmount(amount)
                .interestRate(DEFAULT_INVESTMENT_RATE)
                .currentValue(amount)
                .termMonths(termMonths)
                .isActive(true)
                .user(user)
                .linkedAccount(linkedAccount)
                .startDate(LocalDate.now())
                .maturityDate(termMonths != null ? LocalDate.now().plusMonths(termMonths) : null)
                .build();

        Investment savedInvestment = investmentRepository.save(investment);
        log.info("Investment {} created for user {}", savedInvestment.getInvestmentNumber(), userEmail);

        return AccountResponse.builder()
                .id(savedInvestment.getId())
                .accountNumber(savedInvestment.getInvestmentNumber())
                .accountName(savedInvestment.getInvestmentType())
                .balance(savedInvestment.getCurrentValue())
                .interestRate(savedInvestment.getInterestRate())
                .active(savedInvestment.isActive())
                .createdAt(savedInvestment.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns investment by id data.
     * @param id the unique identifier of the target record.
     * @return the result of the operation.
     */
    public AccountResponse getInvestmentById(Long id) {
        Investment investment = investmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Investment not found"));
        return toAccountResponse(investment);
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns investment by number data.
     * @param investmentNumber the investment Number.
     * @return the result of the operation.
     */
    public AccountResponse getInvestmentByNumber(String investmentNumber) {
        Investment investment = investmentRepository.findByInvestmentNumber(investmentNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Investment not found"));
        return toAccountResponse(investment);
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns investments by user data.
     * @param userEmail the email of the authenticated user.
     * @return the matching results.
     */
    public List<AccountResponse> getInvestmentsByUser(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return investmentRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::toAccountResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns investments by user data.
     * @param userEmail the email of the authenticated user.
     * @param pageable pagination and sorting settings.
     * @return a paged set of matching results.
     */
    public Page<AccountResponse> getInvestmentsByUser(String userEmail, Pageable pageable) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return investmentRepository.findByUser(user, pageable)
                .map(this::toAccountResponse);
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns active investments data.
     * @param userEmail the email of the authenticated user.
     * @return the matching results.
     */
    public List<AccountResponse> getActiveInvestments(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return investmentRepository.findByUserAndIsActiveTrue(user).stream()
                .map(this::toAccountResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    /**
     * Handles close investment.
     * @param investmentId the unique identifier of the target record.
     * @return the result of the operation.
     */
    public AccountResponse closeInvestment(Long investmentId) {
        Investment investment = investmentRepository.findById(investmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Investment not found"));

        if (!investment.isActive()) {
            throw new BadRequestException("Investment is already closed");
        }

        investment.setActive(false);

        // Transfer funds back to linked account if exists
        if (investment.getLinkedAccount() != null) {
            Account linkedAccount = investment.getLinkedAccount();
            linkedAccount.setBalance(linkedAccount.getBalance().add(investment.getCurrentValue()));
            accountRepository.save(linkedAccount);
        }

        Investment savedInvestment = investmentRepository.save(investment);
        log.info("Investment {} closed", savedInvestment.getInvestmentNumber());
        return toAccountResponse(savedInvestment);
    }

    @Override
    @Scheduled(cron = "0 0 0 * * *") // Run daily at midnight
    @Transactional
    /**
     * Handles process matured investments.
     */
    public void processMaturedInvestments() {
        List<Investment> maturedInvestments = investmentRepository
                .findByIsActiveTrueAndMaturityDateBefore(LocalDate.now());

        for (Investment investment : maturedInvestments) {
            try {
                // Calculate interest earned
                BigDecimal interest = investment.getPrincipalAmount()
                        .multiply(investment.getInterestRate())
                        .multiply(BigDecimal.valueOf(investment.getTermMonths()))
                        .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

                investment.setCurrentValue(investment.getPrincipalAmount().add(interest));
                investment.setActive(false);

                // Transfer to linked account if exists
                if (investment.getLinkedAccount() != null) {
                    Account linkedAccount = investment.getLinkedAccount();
                    linkedAccount.setBalance(linkedAccount.getBalance().add(investment.getCurrentValue()));
                    accountRepository.save(linkedAccount);
                }

                investmentRepository.save(investment);
                log.info("Processed matured investment {}", investment.getInvestmentNumber());
            } catch (Exception e) {
                log.error("Error processing investment {}: {}", investment.getInvestmentNumber(), e.getMessage());
            }
        }
    }

    private AccountResponse toAccountResponse(Investment investment) {
        return AccountResponse.builder()
                .id(investment.getId())
                .accountNumber(investment.getInvestmentNumber())
                .accountName(investment.getInvestmentType())
                .balance(investment.getCurrentValue())
                .interestRate(investment.getInterestRate())
                .active(investment.isActive())
                .createdAt(investment.getCreatedAt())
                .build();
    }

    private String generateInvestmentNumber() {
        return "INV" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
