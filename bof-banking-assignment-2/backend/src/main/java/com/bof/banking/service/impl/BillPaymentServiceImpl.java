package com.bof.banking.service.impl;

import com.bof.banking.dto.billpayment.BillPaymentRequest;
import com.bof.banking.dto.billpayment.BillPaymentResponse;
import com.bof.banking.exception.BadRequestException;
import com.bof.banking.exception.InsufficientFundsException;
import com.bof.banking.exception.ResourceNotFoundException;
import com.bof.banking.mapper.BillPaymentMapper;
import com.bof.banking.model.Account;
import com.bof.banking.model.Biller;
import com.bof.banking.model.BillPayment;
import com.bof.banking.model.Transaction;
import com.bof.banking.model.User;
import com.bof.banking.model.enums.PaymentStatus;
import com.bof.banking.model.enums.TransactionType;
import com.bof.banking.repository.AccountRepository;
import com.bof.banking.repository.BillerRepository;
import com.bof.banking.repository.BillPaymentRepository;
import com.bof.banking.repository.TransactionRepository;
import com.bof.banking.repository.UserRepository;
import com.bof.banking.service.BillPaymentService;
import com.bof.banking.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of BillPaymentService for bill payment operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillPaymentServiceImpl implements BillPaymentService {

    private final BillPaymentRepository billPaymentRepository;
    private final AccountRepository accountRepository;
    private final BillerRepository billerRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final BillPaymentMapper billPaymentMapper;
    private final NotificationService notificationService;

    @Override
    @Transactional
    /**
     * Creates bill payment data.
     * @param userEmail the email of the authenticated user.
     * @param request the request payload.
     * @return the result of the operation.
     */
    public BillPaymentResponse createBillPayment(String userEmail, BillPaymentRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Account sourceAccount = accountRepository.findById(request.getSourceAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Source account not found"));

        Biller biller = billerRepository.findById(request.getBillerId())
            .orElseThrow(() -> new ResourceNotFoundException("Biller not found for ID: " + request.getBillerId()));

        if (!biller.isActive()) {
            throw new BadRequestException("Selected biller is inactive");
        }

        if (sourceAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException("Insufficient funds in account");
        }

        Account billerSettlementAccount = resolveSettlementAccount(biller);

        boolean isScheduled = request.getScheduledDate() != null &&
                request.getScheduledDate().isAfter(LocalDateTime.now());

        BillPayment billPayment = BillPayment.builder()
                .paymentReference(generatePaymentReference())
            .biller(biller)
                .accountNumber(request.getAccountNumber())
                .amount(request.getAmount())
                .description(request.getDescription())
                .sourceAccount(sourceAccount)
                .user(user)
                .status(isScheduled ? PaymentStatus.PENDING : PaymentStatus.COMPLETED)
                .scheduledDate(request.getScheduledDate())
                .build();

        if (!isScheduled) {
            sourceAccount.setBalance(sourceAccount.getBalance().subtract(request.getAmount()));
            billerSettlementAccount.setBalance(billerSettlementAccount.getBalance().add(request.getAmount()));
            accountRepository.save(sourceAccount);
            accountRepository.save(billerSettlementAccount);
            billPayment.setProcessedDate(LocalDateTime.now());
            createBillPaymentTransaction(savedReferenceForTransaction(billPayment), request.getAmount(), sourceAccount,
                    billerSettlementAccount, billPayment.getDescription(), billPayment.getProcessedDate());
        }

        BillPayment savedPayment = billPaymentRepository.save(billPayment);
        if (savedPayment.getStatus() == PaymentStatus.COMPLETED) {
            notificationService.notifyBillPaymentProcessed(savedPayment);
        }
        log.info("Bill payment {} created for user {}", savedPayment.getPaymentReference(), userEmail);
        return billPaymentMapper.toResponse(savedPayment);
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns bill payment by id data.
     * @param id the unique identifier of the target record.
     * @return the result of the operation.
     */
    public BillPaymentResponse getBillPaymentById(Long id) {
        BillPayment payment = billPaymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bill payment not found"));
        return billPaymentMapper.toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns bill payment by reference data.
     * @param paymentReference the payment Reference.
     * @return the result of the operation.
     */
    public BillPaymentResponse getBillPaymentByReference(String paymentReference) {
        BillPayment payment = billPaymentRepository.findByPaymentReference(paymentReference)
                .orElseThrow(() -> new ResourceNotFoundException("Bill payment not found"));
        return billPaymentMapper.toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns bill payments by user data.
     * @param userEmail the email of the authenticated user.
     * @return the matching results.
     */
    public List<BillPaymentResponse> getBillPaymentsByUser(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return billPaymentRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(billPaymentMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns bill payments by user data.
     * @param userEmail the email of the authenticated user.
     * @param pageable pagination and sorting settings.
     * @return a paged set of matching results.
     */
    public Page<BillPaymentResponse> getBillPaymentsByUser(String userEmail, Pageable pageable) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return billPaymentRepository.findByUser(user, pageable)
                .map(billPaymentMapper::toResponse);
    }

        @Override
        @Transactional(readOnly = true)
        public Page<BillPaymentResponse> getBillPaymentsForMonitoring(
            String search,
            Long billerId,
            PaymentStatus status,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Pageable pageable) {
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw new BadRequestException("minAmount must be less than or equal to maxAmount");
        }

        Specification<BillPayment> specification = Specification.where(null);

        if (search != null && !search.isBlank()) {
            String normalizedSearch = "%" + search.trim().toLowerCase() + "%";
            specification = specification.and((root, query, cb) -> {
                var billerJoin = root.join("biller", jakarta.persistence.criteria.JoinType.LEFT);
                var sourceAccountJoin = root.join("sourceAccount", jakarta.persistence.criteria.JoinType.LEFT);

                return cb.or(
                        cb.like(cb.lower(root.get("paymentReference")), normalizedSearch),
                        cb.like(cb.lower(root.get("accountNumber")), normalizedSearch),
                        cb.like(cb.lower(root.get("description")), normalizedSearch),
                        cb.like(cb.lower(billerJoin.get("billerName")), normalizedSearch),
                        cb.like(cb.lower(billerJoin.get("billerCode")), normalizedSearch),
                        cb.like(cb.lower(sourceAccountJoin.get("accountNumber")), normalizedSearch));
            });
        }

        if (billerId != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("biller").get("id"), billerId));
        }

        if (status != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }

        if (fromDate != null) {
            specification = specification.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
        }

        if (toDate != null) {
            specification = specification.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), toDate));
        }

        if (minAmount != null) {
            specification = specification.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("amount"), minAmount));
        }

        if (maxAmount != null) {
            specification = specification.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("amount"), maxAmount));
        }

        return billPaymentRepository.findAll(specification, pageable)
                .map(billPaymentMapper::toResponse);
        }

    @Override
    @Transactional
    /**
     * Removes bill payment data.
     * @param id the unique identifier of the target record.
     * @return the result of the operation.
     */
    public BillPaymentResponse cancelBillPayment(Long id) {
        BillPayment payment = billPaymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bill payment not found"));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BadRequestException("Only pending payments can be cancelled");
        }

        payment.setStatus(PaymentStatus.CANCELLED);
        BillPayment savedPayment = billPaymentRepository.save(payment);
        log.info("Bill payment {} cancelled", savedPayment.getPaymentReference());
        return billPaymentMapper.toResponse(savedPayment);
    }

    @Override
    @Scheduled(fixedRate = 60000) // Run every minute
    @Transactional
    /**
     * Handles process scheduled payments.
     */
    public void processScheduledPayments() {
        List<BillPayment> pendingPayments = billPaymentRepository
                .findByStatusAndScheduledDateBefore(PaymentStatus.PENDING, LocalDateTime.now());

        for (BillPayment payment : pendingPayments) {
            try {
                Account sourceAccount = payment.getSourceAccount();
                Account billerSettlementAccount = resolveSettlementAccount(payment.getBiller());
                if (sourceAccount.getBalance().compareTo(payment.getAmount()) >= 0) {
                    sourceAccount.setBalance(sourceAccount.getBalance().subtract(payment.getAmount()));
                    billerSettlementAccount.setBalance(billerSettlementAccount.getBalance().add(payment.getAmount()));
                    accountRepository.save(sourceAccount);
                    accountRepository.save(billerSettlementAccount);
                    payment.setStatus(PaymentStatus.COMPLETED);
                    payment.setProcessedDate(LocalDateTime.now());
                    createBillPaymentTransaction(savedReferenceForTransaction(payment), payment.getAmount(), sourceAccount,
                            billerSettlementAccount, payment.getDescription(), payment.getProcessedDate());
                } else {
                    payment.setStatus(PaymentStatus.FAILED);
                }
                BillPayment savedPayment = billPaymentRepository.save(payment);
                if (savedPayment.getStatus() == PaymentStatus.COMPLETED) {
                    notificationService.notifyBillPaymentProcessed(savedPayment);
                }
                log.info("Processed scheduled payment {}: {}", payment.getPaymentReference(), payment.getStatus());
            } catch (Exception e) {
                log.error("Error processing payment {}: {}", payment.getPaymentReference(), e.getMessage());
                payment.setStatus(PaymentStatus.FAILED);
                billPaymentRepository.save(payment);
            }
        }
    }

    private String generatePaymentReference() {
        return "PAY" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
    }

    private Account resolveSettlementAccount(Biller biller) {
        if (biller.getSettlementAccountNumber() == null || biller.getSettlementAccountNumber().isBlank()) {
            throw new BadRequestException("Biller does not have a settlement account configured");
        }

        return accountRepository.findByAccountNumber(biller.getSettlementAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Settlement account not found for biller code: " + biller.getBillerCode()));
    }

    private String savedReferenceForTransaction(BillPayment billPayment) {
        return "TXN-" + billPayment.getPaymentReference();
    }

    private void createBillPaymentTransaction(String referenceNumber,
                                              BigDecimal amount,
                                              Account sourceAccount,
                                              Account destinationAccount,
                                              String description,
                                              LocalDateTime processedDate) {
        if (transactionRepository.findByReferenceNumber(referenceNumber).isPresent()) {
            return;
        }

        transactionRepository.save(Transaction.builder()
                .referenceNumber(referenceNumber)
                .transactionType(TransactionType.BILL_PAYMENT)
                .amount(amount)
                .description(description != null ? description : "Bill payment")
                .sourceAccount(sourceAccount)
                .destinationAccount(destinationAccount)
                .status(PaymentStatus.COMPLETED)
                .balanceAfter(sourceAccount.getBalance())
                .transactionDate(processedDate != null ? processedDate : LocalDateTime.now())
                .build());
    }
}
