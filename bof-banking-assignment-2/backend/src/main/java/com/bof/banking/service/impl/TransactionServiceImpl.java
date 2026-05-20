package com.bof.banking.service.impl;

import com.bof.banking.dto.transaction.TransactionRequest;
import com.bof.banking.dto.transaction.TransactionResponse;
import com.bof.banking.dto.transaction.TransferInitiationResponse;
import com.bof.banking.dto.transaction.TransferLimitSummaryResponse;
import com.bof.banking.dto.transaction.TransferOtpVerificationRequest;
import com.bof.banking.exception.BadRequestException;
import com.bof.banking.exception.InsufficientFundsException;
import com.bof.banking.exception.ResourceNotFoundException;
import com.bof.banking.mapper.TransactionMapper;
import com.bof.banking.model.Account;
import com.bof.banking.model.Transaction;
import com.bof.banking.model.User;
import com.bof.banking.model.enums.PaymentStatus;
import com.bof.banking.model.enums.TransactionType;
import com.bof.banking.repository.AccountRepository;
import com.bof.banking.repository.TransactionRepository;
import com.bof.banking.repository.UserRepository;
import com.bof.banking.service.TransactionService;
import com.bof.banking.service.TransferLimitService;
import com.bof.banking.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of TransactionService for transaction operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionMapper transactionMapper;
        private final NotificationService notificationService;
        private final TransferLimitService transferLimitService;

        @Value("${app.transfer.otp.high-value-threshold:${app.notification.high-value-threshold:5000}}")
        private BigDecimal otpHighValueThreshold;

        @Value("${app.transfer.otp.expiry-minutes:5}")
        private int otpExpiryMinutes;

        @Value("${app.transfer.otp.max-attempts:3}")
        private int otpMaxAttempts;

        private final Map<String, PendingTransferChallenge> pendingTransferChallenges = new ConcurrentHashMap<>();

    @Override
    @Transactional
    /**
     * Creates transaction data.
     * @param userEmail the email of the authenticated user.
     * @param request the request payload.
     * @return the result of the operation.
     */
    public TransactionResponse createTransaction(String userEmail, TransactionRequest request) {
        return switch (request.getTransactionType()) {
            case DEPOSIT -> deposit(userEmail, request.getDestinationAccountId(), request);
            case WITHDRAWAL -> withdraw(userEmail, request.getSourceAccountId(), request);
                        case TRANSFER -> {
                                if (isHighValueTransfer(request)) {
                                        throw new BadRequestException("OTP verification is required for this transfer. Use /api/transactions/transfer/initiate.");
                                }
                                yield transfer(userEmail, request);
                        }
            default -> throw new BadRequestException("Unsupported transaction type");
        };
    }

        @Override
        @Transactional
        /**
         * Initiates a transfer and determines whether OTP verification is required.
         * @param userEmail the email of the authenticated user.
         * @param request the request payload.
         * @return the transfer initiation result, including OTP challenge details when needed.
         */
        public TransferInitiationResponse initiateTransfer(String userEmail, TransactionRequest request) {
                enforceTransferLimits(userEmail, request);

                if (!isHighValueTransfer(request)) {
                        TransactionResponse response = transfer(userEmail, request);
                        return TransferInitiationResponse.builder()
                                        .otpRequired(false)
                                        .message("Transfer completed successfully.")
                                        .transaction(response)
                                        .build();
                }

                String challengeId = UUID.randomUUID().toString();
                String otpCode = generateOtpCode();
                LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(otpExpiryMinutes);

                pendingTransferChallenges.put(
                                challengeId,
                                new PendingTransferChallenge(userEmail, copyTransferRequest(request), otpCode, expiresAt, 0));

                notificationService.notifyTransferOtp(userEmail, otpCode, request.getAmount(), challengeId, expiresAt);

                return TransferInitiationResponse.builder()
                                .otpRequired(true)
                                .challengeId(challengeId)
                                .expiresAt(expiresAt)
                                .message("A verification OTP has been sent to your email. Enter the OTP to complete this transfer.")
                                .build();
        }

        @Override
        @Transactional
        /**
         * Checks whether transfer otp is valid.
         * @param userEmail the email of the authenticated user.
         * @param request the request payload.
         * @return the result of the operation.
         */
        public TransactionResponse verifyTransferOtp(String userEmail, TransferOtpVerificationRequest request) {
                PendingTransferChallenge challenge = pendingTransferChallenges.get(request.getChallengeId());
                if (challenge == null) {
                        throw new BadRequestException("Invalid OTP challenge.");
                }

                if (!challenge.userEmail().equalsIgnoreCase(userEmail)) {
                        throw new BadRequestException("This OTP challenge does not belong to the authenticated user.");
                }

                if (challenge.expiresAt().isBefore(LocalDateTime.now())) {
                        pendingTransferChallenges.remove(request.getChallengeId());
                        throw new BadRequestException("OTP has expired. Please initiate the transfer again.");
                }

                if (challenge.attemptCount() >= otpMaxAttempts) {
                        pendingTransferChallenges.remove(request.getChallengeId());
                        throw new BadRequestException("Maximum OTP attempts exceeded. Please initiate the transfer again.");
                }

                if (!challenge.otpCode().equals(request.getOtpCode().trim())) {
                        int nextAttempt = challenge.attemptCount() + 1;
                        if (nextAttempt >= otpMaxAttempts) {
                                pendingTransferChallenges.remove(request.getChallengeId());
                                throw new BadRequestException("Invalid OTP. Maximum attempts exceeded. Please initiate the transfer again.");
                        }

                        pendingTransferChallenges.put(
                                        request.getChallengeId(),
                                        new PendingTransferChallenge(
                                                        challenge.userEmail(),
                                                        challenge.transferRequest(),
                                                        challenge.otpCode(),
                                                        challenge.expiresAt(),
                                                        nextAttempt));
                        throw new BadRequestException("Invalid OTP. Please try again.");
                }

                pendingTransferChallenges.remove(request.getChallengeId());
                return transfer(userEmail, challenge.transferRequest());
        }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    /**
     * Handles deposit.
     * @param userEmail the email of the authenticated user.
     * @param accountId the unique identifier of the target record.
     * @param request the request payload.
     * @return the result of the operation.
     */
    public TransactionResponse deposit(String userEmail, Long accountId, TransactionRequest request) {
        Transaction existing = findExistingTransaction(request);
        if (existing != null) {
            log.info("Duplicate deposit detected: {}", existing.getReferenceNumber());
            return transactionMapper.toResponse(existing);
        }

        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        account.setBalance(account.getBalance().add(request.getAmount()));
        accountRepository.save(account);

        Transaction transaction = Transaction.builder()
                .referenceNumber(resolveReferenceNumber(request))
                .transactionType(TransactionType.DEPOSIT)
                .amount(request.getAmount())
                .description(request.getDescription())
                .destinationAccount(account)
                .status(PaymentStatus.COMPLETED)
                .balanceAfter(account.getBalance())
                .transactionDate(LocalDateTime.now())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);
       // notificationService.notifyFundsReceived(savedTransaction);
        notificationService.notifyHighValueTransaction(userEmail, savedTransaction);
        log.info("Deposit of {} to account {}", request.getAmount(), account.getAccountNumber());
        return transactionMapper.toResponse(savedTransaction);
    }

        @Override
        @Transactional(isolation = Isolation.READ_COMMITTED)
        /**
         * Handles withdraw.
         * @param userEmail the email of the authenticated user.
         * @param accountId the unique identifier of the target record.
         * @param request the request payload.
         * @return the result of the operation.
         */
        public TransactionResponse withdraw(String userEmail, Long accountId, TransactionRequest request) {
                Transaction existing = findExistingTransaction(request);
                if (existing != null) {
                        log.info("Duplicate withdrawal detected: {}", existing.getReferenceNumber());
                        return transactionMapper.toResponse(existing);
                }

                Account account = accountRepository.findByIdForUpdate(accountId)
                                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (account.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException("Insufficient funds in account");
        }

        account.setBalance(account.getBalance().subtract(request.getAmount()));
        accountRepository.save(account);

        Transaction transaction = Transaction.builder()
                .referenceNumber(resolveReferenceNumber(request))
                .transactionType(TransactionType.WITHDRAWAL)
                .amount(request.getAmount())
                .description(request.getDescription())
                .sourceAccount(account)
                .status(PaymentStatus.COMPLETED)
                .balanceAfter(account.getBalance())
                .transactionDate(LocalDateTime.now())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);
        notificationService.notifyHighValueTransaction(userEmail, savedTransaction);
        log.info("Withdrawal of {} from account {}", request.getAmount(), account.getAccountNumber());
        return transactionMapper.toResponse(savedTransaction);
    }

        @Override
        @Transactional(isolation = Isolation.READ_COMMITTED)
        /**
         * Handles transfer.
         * @param userEmail the email of the authenticated user.
         * @param request the request payload.
         * @return the result of the operation.
         */
        public TransactionResponse transfer(String userEmail, TransactionRequest request) {
                Transaction existing = findExistingTransaction(request);
                if (existing != null) {
                        log.info("Duplicate transfer detected: {}", existing.getReferenceNumber());
                        return transactionMapper.toResponse(existing);
                }

                Account sourceAccount = accountRepository.findById(request.getSourceAccountId())
                                .orElseThrow(() -> new ResourceNotFoundException("Source account not found"));

        Account destinationAccount;
        if (request.getDestinationAccountId() != null) {
            destinationAccount = accountRepository.findById(request.getDestinationAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Destination account not found"));
        } else if (request.getDestinationAccountNumber() != null) {
            destinationAccount = accountRepository.findByAccountNumber(request.getDestinationAccountNumber())
                    .orElseThrow(() -> new ResourceNotFoundException("Destination account not found"));
        } else {
            throw new BadRequestException("Destination account must be specified");
        }

                validateTransferLimitForDestination(userEmail, destinationAccount, request.getAmount());

        List<Long> sortedIds = List.of(sourceAccount.getId(), destinationAccount.getId())
                .stream()
                .sorted()
                .toList();

        List<Account> lockedAccounts = accountRepository.findByIdsForUpdate(sortedIds);
        if (lockedAccounts.size() != 2) {
            throw new ResourceNotFoundException("Account not found");
        }

        Account lockedSource = lockedAccounts.stream()
                .filter(account -> account.getId().equals(sourceAccount.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Source account not found"));
        Account lockedDestination = lockedAccounts.stream()
                .filter(account -> account.getId().equals(destinationAccount.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Destination account not found"));

        if (lockedSource.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException("Insufficient funds in source account");
        }

        lockedSource.setBalance(lockedSource.getBalance().subtract(request.getAmount()));
        lockedDestination.setBalance(lockedDestination.getBalance().add(request.getAmount()));
        accountRepository.saveAll(List.of(lockedSource, lockedDestination));

        Transaction transaction = Transaction.builder()
                .referenceNumber(resolveReferenceNumber(request))
                .transactionType(TransactionType.TRANSFER)
                .amount(request.getAmount())
                .description(request.getDescription())
                .sourceAccount(lockedSource)
                .destinationAccount(lockedDestination)
                .status(PaymentStatus.COMPLETED)
                .balanceAfter(lockedSource.getBalance())
                .transactionDate(LocalDateTime.now())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);
        notificationService.notifyFundsReceived(savedTransaction);
        notificationService.notifyFundsSent(savedTransaction);
        notificationService.notifyHighValueTransaction(userEmail, savedTransaction);
        log.info("Transfer of {} from {} to {}", request.getAmount(),
                lockedSource.getAccountNumber(), lockedDestination.getAccountNumber());
        return transactionMapper.toResponse(savedTransaction);
    }

        @Override
        @Transactional(readOnly = true)
        /**
         * Returns transfer limit summary data.
         * @param userEmail the email of the authenticated user.
         * @return the result of the operation.
         */
        public TransferLimitSummaryResponse getTransferLimitSummary(String userEmail) {
                return transferLimitService.getSummary(userEmail);
        }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns transaction by id data.
     * @param id the unique identifier of the target record.
     * @return the result of the operation.
     */
    public TransactionResponse getTransactionById(Long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        return transactionMapper.toResponse(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns transaction by reference data.
     * @param referenceNumber the reference Number.
     * @return the result of the operation.
     */
    public TransactionResponse getTransactionByReference(String referenceNumber) {
        Transaction transaction = transactionRepository.findByReferenceNumber(referenceNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        return transactionMapper.toResponse(transaction);
    }

        @Override
        @Transactional(readOnly = true)
        public Page<TransactionResponse> getRecentTransactions(
                        String userEmail, Long accountId, String entryType, Pageable pageable) {
                User user = userRepository.findByEmail(userEmail)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                List<Account> userAccounts = accountRepository.findByUser(user);
                if (userAccounts.isEmpty()) {
                        return Page.empty(pageable);
                }

                Set<Long> userAccountIds = userAccounts.stream()
                                .map(Account::getId)
                                .collect(Collectors.toSet());

                Account selectedAccount = null;
                if (accountId != null) {
                        if (!userAccountIds.contains(accountId)) {
                                throw new BadRequestException("Account does not belong to the authenticated user");
                        }
                        selectedAccount = userAccounts.stream()
                                        .filter(account -> account.getId().equals(accountId))
                                        .findFirst()
                                        .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
                }

                String normalizedEntryType = normalizeEntryType(entryType);
                LocalDateTime startDate = LocalDateTime.now().minusMonths(3);

                Page<Transaction> transactionsPage;
                if (selectedAccount != null) {
                        transactionsPage = getRecentTransactionsForSingleAccount(
                                        selectedAccount, normalizedEntryType, startDate, pageable);
                } else {
                        transactionsPage = getRecentTransactionsForAllAccounts(
                                        userAccounts, normalizedEntryType, startDate, pageable);
                }

                Long selectedAccountId = selectedAccount != null ? selectedAccount.getId() : null;
                return transactionsPage.map(transaction -> {
                        TransactionResponse response = transactionMapper.toResponse(transaction);
                        response.setEntryType(determineEntryType(transaction, userAccountIds, selectedAccountId));
                        return response;
                });
        }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns transactions by account data.
     * @param accountId the unique identifier of the target record.
     * @return the matching results.
     */
    public List<TransactionResponse> getTransactionsByAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        return transactionRepository.findBySourceAccountOrDestinationAccountOrderByTransactionDateDesc(account, account)
                .stream()
                .map(transactionMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns transactions by account data.
     * @param accountId the unique identifier of the target record.
     * @param pageable pagination and sorting settings.
     * @return a paged set of matching results.
     */
    public Page<TransactionResponse> getTransactionsByAccount(Long accountId, Pageable pageable) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        return transactionRepository.findBySourceAccountOrDestinationAccount(account, account, pageable)
                .map(transactionMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsByAccountAndDateRange(
            Long accountId, LocalDateTime startDate, LocalDateTime endDate) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        return transactionRepository.findByAccountAndDateRange(account, startDate, endDate)
                .stream()
                .map(transactionMapper::toResponse)
                .collect(Collectors.toList());
    }

@Override
@Transactional(readOnly = true)
public Page<TransactionResponse> getTransactionsForMonitoring(
        String search,
        TransactionType transactionType,
        PaymentStatus status,
        LocalDateTime from,
        LocalDateTime to,
        Pageable pageable) {

String normalizedSearch = (search == null || search.isBlank())
        ? null
        : "%" + search.trim().toLowerCase() + "%";

String transactionTypeValue = transactionType != null ? transactionType.name() : null;
String statusValue = status != null ? status.name() : null;
Pageable monitoringPageable = normalizeMonitoringPageable(pageable);

return transactionRepository
        .searchForMonitoring(normalizedSearch, transactionTypeValue, statusValue, from, to, monitoringPageable)
        .map(transactionMapper::toResponse);
}

private Pageable normalizeMonitoringPageable(Pageable pageable) {
        int pageNumber = pageable != null ? pageable.getPageNumber() : 0;
        int pageSize = pageable != null ? pageable.getPageSize() : 15;

        List<Sort.Order> mappedOrders = new ArrayList<>();
        if (pageable != null && pageable.getSort().isSorted()) {
                for (Sort.Order order : pageable.getSort()) {
                        String mappedProperty = mapMonitoringSortProperty(order.getProperty());
                        if (mappedProperty != null) {
                                mappedOrders.add(new Sort.Order(order.getDirection(), mappedProperty));
                        }
                }
        }

        if (mappedOrders.isEmpty()) {
                mappedOrders.add(Sort.Order.desc("transaction_date"));
        }

        return PageRequest.of(pageNumber, pageSize, Sort.by(mappedOrders));
}

private String mapMonitoringSortProperty(String property) {
        if (property == null || property.isBlank()) {
                return null;
        }

        return switch (property) {
                case "transactionDate", "transaction_date" -> "transaction_date";
                case "referenceNumber", "reference_number" -> "reference_number";
                case "transactionType", "transaction_type" -> "transaction_type";
                case "status" -> "status";
                case "amount" -> "amount";
                default -> null;
        };
}

private String generateReferenceNumber() {
return "TXN" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
}

        private Page<Transaction> getRecentTransactionsForSingleAccount(
                        Account account, String entryType, LocalDateTime startDate, Pageable pageable) {
                if ("CREDIT".equals(entryType)) {
                        return transactionRepository.findRecentCreditsForAccount(account, startDate, pageable);
                }
                if ("DEBIT".equals(entryType)) {
                        return transactionRepository.findRecentDebitsForAccount(account, startDate, pageable);
                }
                return transactionRepository.findRecentTransactionsForAccount(account, startDate, pageable);
        }

        private Page<Transaction> getRecentTransactionsForAllAccounts(
                        List<Account> accounts, String entryType, LocalDateTime startDate, Pageable pageable) {
                if ("CREDIT".equals(entryType)) {
                        return transactionRepository.findRecentCreditsForAccounts(accounts, startDate, pageable);
                }
                if ("DEBIT".equals(entryType)) {
                        return transactionRepository.findRecentDebitsForAccounts(accounts, startDate, pageable);
                }
                return transactionRepository.findRecentTransactionsForAccounts(accounts, startDate, pageable);
        }

        private String normalizeEntryType(String entryType) {
                if (entryType == null || entryType.isBlank()) {
                        return null;
                }

                String normalized = entryType.trim().toUpperCase(Locale.ROOT);
                if (!"CREDIT".equals(normalized) && !"DEBIT".equals(normalized)) {
                        throw new BadRequestException("entryType must be CREDIT or DEBIT");
                }
                return normalized;
        }

        private String determineEntryType(Transaction transaction, Set<Long> userAccountIds, Long selectedAccountId) {
                Long sourceAccountId = transaction.getSourceAccount() != null ? transaction.getSourceAccount().getId() : null;
                Long destinationAccountId = transaction.getDestinationAccount() != null ? transaction.getDestinationAccount().getId() : null;

                if (selectedAccountId != null) {
                        if (selectedAccountId.equals(sourceAccountId)) {
                                return "DEBIT";
                        }
                        if (selectedAccountId.equals(destinationAccountId)) {
                                return "CREDIT";
                        }
                }

                boolean sourceOwned = sourceAccountId != null && userAccountIds.contains(sourceAccountId);
                boolean destinationOwned = destinationAccountId != null && userAccountIds.contains(destinationAccountId);

                if (sourceOwned && !destinationOwned) {
                        return "DEBIT";
                }
                if (destinationOwned && !sourceOwned) {
                        return "CREDIT";
                }

                return switch (transaction.getTransactionType()) {
                        case DEPOSIT, INTEREST -> "CREDIT";
                        default -> "DEBIT";
                };
        }

        private Transaction findExistingTransaction(TransactionRequest request) {
                if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
                        return null;
                }
                return transactionRepository.findByReferenceNumber(request.getIdempotencyKey())
                                .orElse(null);
        }

        private String resolveReferenceNumber(TransactionRequest request) {
                if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
                        return request.getIdempotencyKey();
                }
                return generateReferenceNumber();
        }

    private boolean isHighValueTransfer(TransactionRequest request) {
        return request.getAmount() != null && request.getAmount().compareTo(otpHighValueThreshold) > 0;
    }

    private String generateOtpCode() {
        int value = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return String.valueOf(value);
    }

    private TransactionRequest copyTransferRequest(TransactionRequest request) {
        return TransactionRequest.builder()
                .transactionType(TransactionType.TRANSFER)
                .amount(request.getAmount())
                .description(request.getDescription())
                .sourceAccountId(request.getSourceAccountId())
                .destinationAccountId(request.getDestinationAccountId())
                .destinationAccountNumber(request.getDestinationAccountNumber())
                .idempotencyKey(request.getIdempotencyKey())
                .build();
    }

        private void enforceTransferLimits(String userEmail, TransactionRequest request) {
                if (request.getAmount() == null) {
                        return;
                }

                Account destination = resolveDestinationAccount(request);
                validateTransferLimitForDestination(userEmail, destination, request.getAmount());
        }

        private Account resolveDestinationAccount(TransactionRequest request) {
                if (request.getDestinationAccountId() != null) {
                        return accountRepository.findById(request.getDestinationAccountId())
                                        .orElseThrow(() -> new ResourceNotFoundException("Destination account not found"));
                }
                if (request.getDestinationAccountNumber() != null) {
                        return accountRepository.findByAccountNumber(request.getDestinationAccountNumber())
                                        .orElseThrow(() -> new ResourceNotFoundException("Destination account not found"));
                }
                throw new BadRequestException("Destination account must be specified");
        }

        private void validateTransferLimitForDestination(String userEmail, Account destinationAccount, BigDecimal amount) {
                User user = userRepository.findByEmail(userEmail)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                boolean ownAccountTransfer = destinationAccount.getUser() != null
                                && destinationAccount.getUser().getId().equals(user.getId());

                transferLimitService.validateTransfer(user, ownAccountTransfer, amount);
        }

    private record PendingTransferChallenge(
            String userEmail,
            TransactionRequest transferRequest,
            String otpCode,
            LocalDateTime expiresAt,
            int attemptCount) {
    }
}
