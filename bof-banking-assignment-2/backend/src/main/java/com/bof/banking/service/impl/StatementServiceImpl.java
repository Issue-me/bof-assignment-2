package com.bof.banking.service.impl;

import com.bof.banking.dto.statement.StatementLineItemResponse;
import com.bof.banking.dto.statement.StatementMetadataResponse;
import com.bof.banking.dto.statement.StatementRequest;
import com.bof.banking.dto.statement.StatementResponse;
import com.bof.banking.exception.ResourceNotFoundException;
import com.bof.banking.exception.UnauthorizedException;
import com.bof.banking.model.Account;
import com.bof.banking.model.Transaction;
import com.bof.banking.model.User;
import com.bof.banking.repository.AccountRepository;
import com.bof.banking.repository.TransactionRepository;
import com.bof.banking.repository.UserRepository;
import com.bof.banking.service.PdfGeneratorService;
import com.bof.banking.service.StatementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of {@link StatementService} for bank statement operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatementServiceImpl implements StatementService {

    private static final int MAX_STATEMENT_YEARS = 5;
    private static final String BANK_NAME = "Bank of Fiji";
    private static final String BANK_ADDRESS = "Suva, Republic of Fiji";
    private static final String BANK_PHONE = "+679 331 3611";

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final PdfGeneratorService pdfGeneratorService;

    @Override
    @Transactional(readOnly = true)
    /**
     * Handles generate statement.
     * @param userEmail the email of the authenticated user.
     * @param request the request payload.
     * @return the result of the operation.
     */
    public StatementResponse generateStatement(String userEmail, StatementRequest request) {
        validateStatementRequest(request);

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (!account.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("You do not have access to this account");
        }

        String statementId = generateStatementId();
        LocalDateTime fromDateTime = request.getFromDate().atStartOfDay();
        LocalDateTime toDateTime = request.getToDate().atTime(23, 59, 59);

        List<Transaction> transactions = transactionRepository.findByAccountIdAndDateRange(
                account.getId(), fromDateTime, toDateTime);

        BigDecimal openingBalance = calculateOpeningBalance(account, request.getFromDate());
        List<StatementLineItemResponse> lineItems = new ArrayList<>();
        BigDecimal runningBalance = openingBalance;
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (Transaction transaction : transactions) {
            BigDecimal amount = transaction.getAmount();
            BigDecimal debit = BigDecimal.ZERO;
            BigDecimal credit = BigDecimal.ZERO;

            if (transaction.getSourceAccount() != null && 
                transaction.getSourceAccount().getId().equals(account.getId())) {
                debit = amount;
                totalDebits = totalDebits.add(amount);
                runningBalance = runningBalance.subtract(amount);
            } else if (transaction.getDestinationAccount() != null && 
                       transaction.getDestinationAccount().getId().equals(account.getId())) {
                credit = amount;
                totalCredits = totalCredits.add(amount);
                runningBalance = runningBalance.add(amount);
            }

            String description = buildTransactionDescription(transaction);
            StatementLineItemResponse lineItem = StatementLineItemResponse.builder()
                    .transactionDate(transaction.getTransactionDate().format(dateFormatter))
                    .description(description)
                    .type(transaction.getTransactionType())
                    .debit(debit.signum() > 0 ? debit : null)
                    .credit(credit.signum() > 0 ? credit : null)
                    .runningBalance(runningBalance)
                    .referenceNumber(transaction.getReferenceNumber())
                    .timestamp(transaction.getTransactionDate())
                    .build();

            lineItems.add(lineItem);
        }

        BigDecimal closingBalance = runningBalance;

        StatementResponse response = StatementResponse.builder()
                .statementId(statementId)
                .bankName(BANK_NAME)
                .bankAddress(BANK_ADDRESS)
                .bankPhone(BANK_PHONE)
                .customerName(user.getFirstName() + " " + user.getLastName())
                .customerEmail(user.getEmail())
                .customerPhone(user.getPhoneNumber())
                .accountNumber(account.getAccountNumber())
                .accountType(account.getAccountType().toString())
                .accountName(account.getAccountName())
                .statementFromDate(request.getFromDate())
                .statementToDate(request.getToDate())
                .generatedDate(LocalDate.now())
                .openingBalance(openingBalance)
                .closingBalance(closingBalance)
                .totalDebits(totalDebits)
                .totalCredits(totalCredits)
                .transactions(lineItems)
                .build();

        log.info("Generated statement {} for account {}", statementId, account.getAccountNumber());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns available statements data.
     * @param userEmail the email of the authenticated user.
     * @param accountId the unique identifier of the target record.
     * @return the matching results.
     */
    public List<StatementMetadataResponse> getAvailableStatements(String userEmail, Long accountId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (!account.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("You do not have access to this account");
        }

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusYears(MAX_STATEMENT_YEARS);
        List<StatementMetadataResponse> metadata = new ArrayList<>();
        YearMonth currentMonth = YearMonth.from(startDate);
        YearMonth endMonth = YearMonth.from(endDate);

        while (!currentMonth.isAfter(endMonth)) {
            LocalDate monthStart = currentMonth.atDay(1);
            LocalDate monthEnd = currentMonth.atEndOfMonth();
            LocalDateTime fromDateTime = monthStart.atStartOfDay();
            LocalDateTime toDateTime = monthEnd.atTime(23, 59, 59);
            
            long transactionCount = transactionRepository.countByAccountIdAndDateRange(
                    account.getId(), fromDateTime, toDateTime);

            if (transactionCount > 0) {
                String statementId = generateStatementIdForMonth(currentMonth);
                StatementMetadataResponse meta = StatementMetadataResponse.builder()
                        .statementId(statementId)
                        .accountNumber(account.getAccountNumber())
                        .accountName(account.getAccountName())
                        .periodStartDate(monthStart)
                        .periodEndDate(monthEnd)
                        .generatedDate(LocalDateTime.now())
                        .statementMonth(currentMonth.getMonth().toString())
                        .statementYear(String.valueOf(currentMonth.getYear()))
                        .build();
                metadata.add(meta);
            }

            currentMonth = currentMonth.plusMonths(1);
        }

        log.info("Retrieved {} statements for account {}", metadata.size(), account.getAccountNumber());
        return metadata;
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Handles generate statement pdf.
     * @param userEmail the email of the authenticated user.
     * @param request the request payload.
     * @return the result of the operation.
     */
    public byte[] generateStatementPdf(String userEmail, StatementRequest request) {
        StatementResponse statement = generateStatement(userEmail, request);
        return pdfGeneratorService.generateStatementPdf(statement);
    }

    private void validateStatementRequest(StatementRequest request) {
        if (request.getFromDate() == null || request.getToDate() == null) {
            throw new IllegalArgumentException("Date range is required");
        }

        if (request.getFromDate().isAfter(request.getToDate())) {
            throw new IllegalArgumentException("From date must be before or equal to to date");
        }

        LocalDate maxStartDate = LocalDate.now().minusYears(MAX_STATEMENT_YEARS);
        if (request.getFromDate().isBefore(maxStartDate)) {
            throw new IllegalArgumentException(
                    "Statement history limited to last " + MAX_STATEMENT_YEARS + " years");
        }

        if (request.getFromDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("From date cannot be in the future");
        }
    }

    private BigDecimal calculateOpeningBalance(Account account, LocalDate periodStart) {
        LocalDateTime beforeDate = periodStart.atStartOfDay();
        List<Transaction> beforeTransactions = transactionRepository.findTransactionsBefore(
                account.getId(), beforeDate);

        BigDecimal openingBalance = account.getBalance();

        for (Transaction transaction : beforeTransactions) {
            if (transaction.getSourceAccount() != null && 
                transaction.getSourceAccount().getId().equals(account.getId())) {
                openingBalance = openingBalance.add(transaction.getAmount());
            } else if (transaction.getDestinationAccount() != null && 
                       transaction.getDestinationAccount().getId().equals(account.getId())) {
                openingBalance = openingBalance.subtract(transaction.getAmount());
            }
        }

        return openingBalance;
    }

    private String buildTransactionDescription(Transaction transaction) {
        String baseDescription = transaction.getDescription() != null ? 
                transaction.getDescription() : 
                transaction.getTransactionType().toString();

        if (transaction.getSourceAccount() != null && transaction.getDestinationAccount() != null) {
            return "Transfer - " + baseDescription;
        } else if (transaction.getSourceAccount() != null) {
            return "Withdrawal - " + baseDescription;
        } else if (transaction.getDestinationAccount() != null) {
            return "Deposit - " + baseDescription;
        }

        return baseDescription;
    }

    private String generateStatementId() {
        return "STMT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String generateStatementIdForMonth(YearMonth yearMonth) {
        return "STMT-" + yearMonth.toString();
    }
}
