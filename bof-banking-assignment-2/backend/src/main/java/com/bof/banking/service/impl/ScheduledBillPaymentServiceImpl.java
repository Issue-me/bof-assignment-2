package com.bof.banking.service.impl;

import com.bof.banking.dto.billpayment.AutoPayHistoryItemResponse;
import com.bof.banking.dto.billpayment.BillerInvoiceResponse;
import com.bof.banking.dto.billpayment.ScheduledBillPaymentRequest;
import com.bof.banking.dto.billpayment.ScheduledBillPaymentResponse;
import com.bof.banking.exception.BadRequestException;
import com.bof.banking.exception.ResourceNotFoundException;
import com.bof.banking.mapper.ScheduledBillPaymentMapper;
import com.bof.banking.model.Account;
import com.bof.banking.model.Biller;
import com.bof.banking.model.BillerInvoice;
import com.bof.banking.model.BillPayment;
import com.bof.banking.model.Notification;
import com.bof.banking.model.ScheduledBillPayment;
import com.bof.banking.model.Transaction;
import com.bof.banking.model.User;
import com.bof.banking.model.enums.InvoiceStatus;
import com.bof.banking.model.enums.NotificationType;
import com.bof.banking.model.enums.PaymentStatus;
import com.bof.banking.model.enums.ScheduleFrequency;
import com.bof.banking.model.enums.TransactionType;
import com.bof.banking.repository.AccountRepository;
import com.bof.banking.repository.BillerInvoiceRepository;
import com.bof.banking.repository.BillerRepository;
import com.bof.banking.repository.BillPaymentRepository;
import com.bof.banking.repository.NotificationRepository;
import com.bof.banking.repository.ScheduledBillPaymentRepository;
import com.bof.banking.repository.TransactionRepository;
import com.bof.banking.repository.UserRepository;
import com.bof.banking.service.ScheduledBillPaymentService;
import com.bof.banking.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Invoice-driven implementation for recurring monthly bill payments.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledBillPaymentServiceImpl implements ScheduledBillPaymentService {

    private static final ZoneId FIJI_ZONE = ZoneId.of("Pacific/Fiji");

    private final ScheduledBillPaymentRepository scheduledPaymentRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final BillerRepository billerRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final TransactionRepository transactionRepository;
    private final BillerInvoiceRepository billerInvoiceRepository;
    private final NotificationRepository notificationRepository;
    private final ScheduledBillPaymentMapper mapper;
    private final NotificationService notificationService;

    @Value("${app.autopay.test-mode:false}")
    private boolean autoPayTestMode;

    @Value("${app.billpay.manual.min-balance-buffer:20.00}")
    private BigDecimal manualPaymentMinBalanceBuffer;

    @Value("${app.billpay.manual.daily-limit:10000.00}")
    private BigDecimal manualPaymentDailyLimit;

    @Value("${app.billpay.manual.cutoff-hour-fiji:22}")
    private int manualPaymentCutoffHourFiji;

    @Value("${app.billpay.manual.max-days-after-due:45}")
    private long manualPaymentMaxDaysAfterDue;

    @Override
    @Transactional
    public ScheduledBillPaymentResponse createScheduledBillPayment(
            String userEmail,
            ScheduledBillPaymentRequest request) {

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (!account.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("Account does not belong to the authenticated user");
        }

        Biller biller = billerRepository.findById(request.getBillerId())
                .orElseThrow(() -> new ResourceNotFoundException("Biller not found"));

        if (!biller.isActive()) {
            throw new BadRequestException("Selected biller is inactive");
        }

        if (request.getStartDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("Start date cannot be in the past");
        }

        if (request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("End date cannot be before start date");
        }

        if (!Boolean.TRUE.equals(request.getApprovalGiven())) {
            throw new BadRequestException("Recurring approval must be given for automated bill payment setup");
        }

        LocalDate nextExecution = calculateNextExecutionDate(request.getStartDate(), request.getFrequency());

        ScheduledBillPayment payment = ScheduledBillPayment.builder()
                .user(user)
                .account(account)
                .biller(biller)
                .billReference(request.getBillReference())
                .amount(request.getAmount())
                .frequency(request.getFrequency())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .nextExecutionDate(nextExecution)
                .autoPayEnabled(request.getAutoPayEnabled() == null || request.getAutoPayEnabled())
                .approvalGiven(true)
                .status(PaymentStatus.ACTIVE)
                .description(request.getDescription())
                .build();

        ScheduledBillPayment saved = scheduledPaymentRepository.save(payment);
        log.info("Scheduled bill payment created: id={}, user={}, biller={}",
                saved.getId(), userEmail, biller.getBillerName());

        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns scheduled bill payment by id data.
     * @param id the unique identifier of the target record.
     * @param userEmail the email of the authenticated user.
     * @return the result of the operation.
     */
    public ScheduledBillPaymentResponse getScheduledBillPaymentById(Long id, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ScheduledBillPayment payment = scheduledPaymentRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled payment not found"));

        return mapper.toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns scheduled bill payments by user data.
     * @param userEmail the email of the authenticated user.
     * @return the matching results.
     */
    public List<ScheduledBillPaymentResponse> getScheduledBillPaymentsByUser(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return scheduledPaymentRepository.findByUserOrderByStartDateAsc(user).stream()
                .map(mapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns scheduled bill payments by user data.
     * @param userEmail the email of the authenticated user.
     * @param pageable pagination and sorting settings.
     * @return a paged set of matching results.
     */
    public Page<ScheduledBillPaymentResponse> getScheduledBillPaymentsByUser(String userEmail, Pageable pageable) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return scheduledPaymentRepository.findByUser(user, pageable)
                .map(mapper::toResponse);
    }

    @Override
    @Transactional
    public ScheduledBillPaymentResponse updateScheduledBillPayment(
            Long id,
            String userEmail,
            ScheduledBillPaymentRequest request) {

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ScheduledBillPayment payment = scheduledPaymentRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled payment not found"));

        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            throw new BadRequestException("Cannot update a cancelled scheduled payment");
        }

        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (!account.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("Account does not belong to the authenticated user");
        }

        Biller biller = billerRepository.findById(request.getBillerId())
                .orElseThrow(() -> new ResourceNotFoundException("Biller not found"));

        if (!biller.isActive()) {
            throw new BadRequestException("Selected biller is inactive");
        }

        if (request.getStartDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("Start date cannot be in the past");
        }

        if (request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("End date cannot be before start date");
        }

        if (!Boolean.TRUE.equals(request.getApprovalGiven())) {
            throw new BadRequestException("Recurring approval must remain true for automated bill payment");
        }

        payment.setAccount(account);
        payment.setBiller(biller);
        payment.setBillReference(request.getBillReference());
        payment.setAmount(request.getAmount());
        payment.setFrequency(request.getFrequency());
        payment.setStartDate(request.getStartDate());
        payment.setEndDate(request.getEndDate());
        payment.setAutoPayEnabled(request.getAutoPayEnabled() == null || request.getAutoPayEnabled());
        payment.setApprovalGiven(true);
        payment.setDescription(request.getDescription());
        payment.setNextExecutionDate(calculateNextExecutionDate(request.getStartDate(), request.getFrequency()));

        ScheduledBillPayment updated = scheduledPaymentRepository.save(payment);
        log.info("Scheduled bill payment updated: id={}, user={}", id, userEmail);

        return mapper.toResponse(updated);
    }

    @Override
    @Transactional
    /**
     * Removes scheduled bill payment data.
     * @param id the unique identifier of the target record.
     * @param userEmail the email of the authenticated user.
     * @return the result of the operation.
     */
    public ScheduledBillPaymentResponse cancelScheduledBillPayment(Long id, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ScheduledBillPayment payment = scheduledPaymentRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled payment not found"));

        payment.setStatus(PaymentStatus.CANCELLED);
        ScheduledBillPayment updated = scheduledPaymentRepository.save(payment);

        log.info("Scheduled bill payment cancelled: id={}, user={}", id, userEmail);
        return mapper.toResponse(updated);
    }

    @Override
    @Transactional
    /**
     * Handles pause scheduled bill payment.
     * @param id the unique identifier of the target record.
     * @param userEmail the email of the authenticated user.
     * @return the result of the operation.
     */
    public ScheduledBillPaymentResponse pauseScheduledBillPayment(Long id, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ScheduledBillPayment payment = scheduledPaymentRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled payment not found"));

        if (payment.getStatus() != PaymentStatus.ACTIVE) {
            throw new BadRequestException("Only ACTIVE payments can be paused");
        }

        payment.setStatus(PaymentStatus.PAUSED);
        ScheduledBillPayment updated = scheduledPaymentRepository.save(payment);

        log.info("Scheduled bill payment paused: id={}, user={}", id, userEmail);
        return mapper.toResponse(updated);
    }

    @Override
    @Transactional
    /**
     * Handles resume scheduled bill payment.
     * @param id the unique identifier of the target record.
     * @param userEmail the email of the authenticated user.
     * @return the result of the operation.
     */
    public ScheduledBillPaymentResponse resumeScheduledBillPayment(Long id, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ScheduledBillPayment payment = scheduledPaymentRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled payment not found"));

        if (payment.getStatus() != PaymentStatus.PAUSED) {
            throw new BadRequestException("Only PAUSED payments can be resumed");
        }

        payment.setStatus(PaymentStatus.ACTIVE);
        ScheduledBillPayment updated = scheduledPaymentRepository.save(payment);

        log.info("Scheduled bill payment resumed: id={}, user={}", id, userEmail);
        return mapper.toResponse(updated);
    }

    @Override
    @Transactional
        public ScheduledBillPaymentResponse setAutoPayEnabled(
            Long id,
            String userEmail,
            boolean enabled,
            boolean payPendingBills) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ScheduledBillPayment payment = scheduledPaymentRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled payment not found"));

        if (!payment.isApprovalGiven()) {
            throw new BadRequestException("Recurring approval has not been given for this setup");
        }

        payment.setAutoPayEnabled(enabled);

        if (enabled && payPendingBills) {
            List<BillerInvoice> invoices = billerInvoiceRepository
                    .findByBillerAndCustomerReferenceOrderByInvoiceYearAscInvoiceMonthAsc(
                            payment.getBiller(),
                            payment.getBillReference());

            int currentMonthIndex = monthIndex(LocalDate.now(FIJI_ZONE).getYear(), LocalDate.now(FIJI_ZONE).getMonthValue());

            for (BillerInvoice invoice : invoices) {
                if (invoice.getStatus() != InvoiceStatus.UNPAID) {
                    continue;
                }
                int invoiceMonthIndex = monthIndex(invoice.getInvoiceYear(), invoice.getInvoiceMonth());
                if (invoiceMonthIndex >= currentMonthIndex) {
                    continue;
                }
                payInvoiceInternal(payment, invoice, user, null, false);
            }
        }

        ScheduledBillPayment updated = scheduledPaymentRepository.save(payment);
        return mapper.toResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns invoices for scheduled payment data.
     * @param id the unique identifier of the target record.
     * @param userEmail the email of the authenticated user.
     * @return the matching results.
     */
    public List<BillerInvoiceResponse> getInvoicesForScheduledPayment(Long id, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ScheduledBillPayment setup = scheduledPaymentRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled payment not found"));

        return billerInvoiceRepository
                .findByBillerAndCustomerReferenceOrderByInvoiceYearAscInvoiceMonthAsc(
                        setup.getBiller(),
                        setup.getBillReference())
                .stream()
                .map(this::toInvoiceResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public BillerInvoiceResponse payInvoiceNow(
            Long scheduledPaymentId,
            Long invoiceId,
            String userEmail,
            String idempotencyKey) {
            User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            ScheduledBillPayment setup = scheduledPaymentRepository.findByIdAndUser(scheduledPaymentId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled payment not found"));

            if (setup.getStatus() == PaymentStatus.CANCELLED) {
                throw new BadRequestException("Cannot pay invoice from a cancelled scheduled setup");
            }

            if (!setup.isApprovalGiven()) {
                throw new BadRequestException("Manual payment not allowed because recurring approval is missing");
            }

            BillerInvoice invoice = billerInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

            return payInvoiceInternal(setup, invoice, user, idempotencyKey, true);
    }

    private BillerInvoiceResponse payInvoiceInternal(
            ScheduledBillPayment setup,
            BillerInvoice invoice,
            User user,
            String idempotencyKey,
            boolean enforceTimingRules) {

            boolean invoiceBelongsToSetup = invoice.getBiller().getId().equals(setup.getBiller().getId())
                && setup.getBillReference().equals(invoice.getCustomerReference());
            if (!invoiceBelongsToSetup) {
                throw new BadRequestException("Invoice does not belong to this scheduled payment setup");
            }

            if (invoice.getStatus() == InvoiceStatus.PAID) {
                throw new BadRequestException("Invoice is already paid");
            }

            if (invoice.getInvoiceAmount() == null || invoice.getInvoiceAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Invoice amount is invalid");
            }

            LocalDate todayFiji = LocalDate.now(FIJI_ZONE);
            LocalDateTime nowFiji = LocalDateTime.now(FIJI_ZONE);
            if (enforceTimingRules && invoice.getDueDate() != null) {
                long overdueDays = ChronoUnit.DAYS.between(invoice.getDueDate(), todayFiji);
                if (overdueDays > manualPaymentMaxDaysAfterDue) {
                    throw new BadRequestException(
                            "Invoice is too old for online payment. Please contact support.");
                }
                if (invoice.getDueDate().isEqual(todayFiji) && nowFiji.getHour() >= manualPaymentCutoffHourFiji) {
                    throw new BadRequestException(
                            "Payment cutoff for today has passed. Please try again tomorrow.");
                }
            }

            String cleanedIdempotencyKey = idempotencyKey == null ? null : idempotencyKey.trim();
            if (cleanedIdempotencyKey != null && !cleanedIdempotencyKey.isEmpty()) {
                BillPayment existingPayment = billPaymentRepository
                        .findByUserAndIdempotencyKey(user, cleanedIdempotencyKey)
                        .orElse(null);
                if (existingPayment != null) {
                    if (invoice.getBillPayment() != null
                            && existingPayment.getId().equals(invoice.getBillPayment().getId())) {
                        return toInvoiceResponse(invoice);
                    }
                    throw new BadRequestException("This request was already submitted with a different invoice");
                }
            }

            Account sourceAccount = accountRepository.findByIdForUpdate(setup.getAccount().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Source account not found"));

            if (!sourceAccount.getUser().getId().equals(user.getId())) {
                throw new BadRequestException("Source account does not belong to the authenticated user");
            }

            Account destinationAccount = resolveSettlementAccount(setup.getBiller());
            destinationAccount = accountRepository.findByIdForUpdate(destinationAccount.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Settlement account not found"));

            if (sourceAccount.getBalance().compareTo(invoice.getInvoiceAmount()) < 0) {
                throw new BadRequestException("Insufficient balance for invoice amount " + invoice.getInvoiceAmount());
            }

            BigDecimal todayUsed = billPaymentRepository.sumCompletedBillPaymentsSince(
                    user.getId(),
                    PaymentStatus.COMPLETED,
                    todayFiji.atStartOfDay());
            BigDecimal projectedDailyTotal = todayUsed.add(invoice.getInvoiceAmount());
            if (projectedDailyTotal.compareTo(manualPaymentDailyLimit) > 0) {
                throw new BadRequestException(String.format(
                        "Daily bill payment limit exceeded. Daily limit: %s, used: %s, requested: %s",
                        manualPaymentDailyLimit,
                        todayUsed,
                        invoice.getInvoiceAmount()));
            }

            BigDecimal remainingBalance = sourceAccount.getBalance().subtract(invoice.getInvoiceAmount());
            if (remainingBalance.compareTo(manualPaymentMinBalanceBuffer) < 0) {
                throw new BadRequestException(String.format(
                        "Payment would breach minimum balance buffer. Minimum required after payment: %s",
                        manualPaymentMinBalanceBuffer));
            }

            sourceAccount.setBalance(sourceAccount.getBalance().subtract(invoice.getInvoiceAmount()));
            destinationAccount.setBalance(destinationAccount.getBalance().add(invoice.getInvoiceAmount()));
            accountRepository.save(sourceAccount);
            accountRepository.save(destinationAccount);

            LocalDateTime processedAt = LocalDateTime.now();

            BillPayment billPayment = billPaymentRepository.save(BillPayment.builder()
                .paymentReference(generateScheduledPaymentReference())
                .idempotencyKey(cleanedIdempotencyKey)
                .biller(setup.getBiller())
                .accountNumber(setup.getBillReference())
                .amount(invoice.getInvoiceAmount())
                .description("Manual invoice payment")
                .sourceAccount(sourceAccount)
                .user(setup.getUser())
                .status(PaymentStatus.COMPLETED)
                .scheduledDate(processedAt)
                .processedDate(processedAt)
                .build());

            String transactionReference = "TXN-" + billPayment.getPaymentReference();
            if (transactionRepository.findByReferenceNumber(transactionReference).isEmpty()) {
                transactionRepository.save(Transaction.builder()
                    .referenceNumber(transactionReference)
                    .transactionType(TransactionType.BILL_PAYMENT)
                    .amount(invoice.getInvoiceAmount())
                    .description("Manual invoice payment")
                    .sourceAccount(sourceAccount)
                    .destinationAccount(destinationAccount)
                    .status(PaymentStatus.COMPLETED)
                    .balanceAfter(sourceAccount.getBalance())
                    .transactionDate(processedAt)
                    .build());
            }

            invoice.setStatus(InvoiceStatus.PAID);
            invoice.setPaidAt(processedAt);
            invoice.setBillPayment(billPayment);
            billerInvoiceRepository.save(invoice);

            setup.setLastAttemptAt(processedAt);
            setup.setLastProcessedMonth(invoice.getInvoiceMonth());
            setup.setLastProcessedYear(invoice.getInvoiceYear());
            setup.setLastFailureReason(null);
            scheduledPaymentRepository.save(setup);

            notificationService.notifyBillPaymentProcessed(billPayment);
            return toInvoiceResponse(invoice);
    }

    private int monthIndex(Integer year, Integer month) {
        if (year == null || month == null) {
            return Integer.MAX_VALUE;
        }
        return (year * 12) + (month - 1);
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns auto pay history data.
     * @param id the unique identifier of the target record.
     * @param userEmail the email of the authenticated user.
     * @return the matching results.
     */
    public List<AutoPayHistoryItemResponse> getAutoPayHistory(Long id, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ScheduledBillPayment setup = scheduledPaymentRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled payment not found"));

        List<BillerInvoice> invoices = billerInvoiceRepository
                .findByBillerAndCustomerReferenceOrderByInvoiceYearAscInvoiceMonthAsc(
                        setup.getBiller(),
                        setup.getBillReference());

        List<AutoPayHistoryItemResponse> history = new ArrayList<>();
        for (BillerInvoice invoice : invoices) {
            BillPayment billPayment = invoice.getBillPayment();
            history.add(AutoPayHistoryItemResponse.builder()
                    .invoiceMonth(invoice.getInvoiceMonth())
                    .invoiceYear(invoice.getInvoiceYear())
                    .amount(invoice.getInvoiceAmount())
                    .dueDate(invoice.getDueDate())
                    .invoiceStatus(invoice.getStatus())
                    .paymentReference(billPayment != null ? billPayment.getPaymentReference() : null)
                    .processedAt(invoice.getPaidAt())
                    .transactionReference(
                            billPayment != null ? "TXN-" + billPayment.getPaymentReference() : null)
                    .build());
        }
        return history;
    }

    @Override
    @Scheduled(cron = "${app.autopay.monthly-cron:0 0 1 1 * *}", zone = "Pacific/Fiji")
    @Transactional
    /**
     * Handles execute scheduled payments.
     */
    public void executeScheduledPayments() {
        if (autoPayTestMode) {
            return;
        }
        runScheduledPaymentCycle();
    }

    @Scheduled(fixedDelayString = "${app.autopay.test-fixed-delay-ms:60000}")
    @Transactional
    /**
     * Handles execute scheduled payments for test mode.
     */
    public void executeScheduledPaymentsForTestMode() {
        if (!autoPayTestMode) {
            return;
        }
        runScheduledPaymentCycle();
    }

    @Override
    @Transactional
    /**
     * Handles execute scheduled payments manually.
     */
    public void executeScheduledPaymentsManually() {
        runScheduledPaymentCycle();
    }

    private void runScheduledPaymentCycle() {
        LocalDate today = LocalDate.now();
        Integer month = today.getMonthValue();
        Integer year = today.getYear();

        List<ScheduledBillPayment> autoDuePayments = scheduledPaymentRepository
                .findDueAutoPayForMonth(today, month, year);

        log.info("Auto-pay cycle started. Due setups={}", autoDuePayments.size());

        for (ScheduledBillPayment scheduled : autoDuePayments) {
            try {
                executeSingleAutoPayInvoice(scheduled, today, month, year);
            } catch (Exception e) {
                log.error("Error executing invoice-based auto-payment id={}: {}",
                        scheduled.getId(), e.getMessage());
            }
        }

        List<ScheduledBillPayment> legacyDuePayments = scheduledPaymentRepository.findDueForExecution();
        for (ScheduledBillPayment scheduled : legacyDuePayments) {
            if (scheduled.isAutoPayEnabled()) {
                continue;
            }
            try {
                executeSingleLegacyScheduledPayment(scheduled);
            } catch (Exception e) {
                log.error("Error executing legacy scheduled payment id={}: {}",
                        scheduled.getId(), e.getMessage());
            }
        }

        log.info("Auto-pay cycle completed");
    }

    /**
     * Pays only the matching monthly invoice and uses the exact invoice amount.
     */
    private void executeSingleAutoPayInvoice(
            ScheduledBillPayment scheduled,
            LocalDate today,
            Integer month,
            Integer year) {

        scheduled.setLastAttemptAt(LocalDateTime.now());

        if (!scheduled.getAccount().getUser().getId().equals(scheduled.getUser().getId())) {
            markAutoPayFailure(scheduled, "Linked account does not belong to setup user");
            return;
        }

        BillerInvoice invoice = billerInvoiceRepository
                .findByBillerAndCustomerReferenceAndInvoiceMonthAndInvoiceYearAndStatus(
                        scheduled.getBiller(),
                        scheduled.getBillReference(),
                        month,
                        year,
                        InvoiceStatus.UNPAID)
                .orElse(null);

        if (invoice == null) {
            markAutoPayFailure(scheduled, "No unpaid invoice found for this month");
            return;
        }

        Account sourceAccount = accountRepository.findByIdForUpdate(scheduled.getAccount().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Source account not found"));

        Account destinationAccount = resolveSettlementAccount(scheduled.getBiller());
        destinationAccount = accountRepository.findByIdForUpdate(destinationAccount.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Settlement account not found"));

        if (sourceAccount.getBalance().compareTo(invoice.getInvoiceAmount()) < 0) {
            markAutoPayFailure(scheduled, "Insufficient balance for invoice amount " + invoice.getInvoiceAmount());
            createFailureNotification(scheduled, invoice);
            return;
        }

        sourceAccount.setBalance(sourceAccount.getBalance().subtract(invoice.getInvoiceAmount()));
        destinationAccount.setBalance(destinationAccount.getBalance().add(invoice.getInvoiceAmount()));
        accountRepository.save(sourceAccount);
        accountRepository.save(destinationAccount);

        LocalDateTime processedAt = LocalDateTime.now();

        BillPayment billPayment = billPaymentRepository.save(BillPayment.builder()
                .paymentReference(generateScheduledPaymentReference())
                .biller(scheduled.getBiller())
                .accountNumber(scheduled.getBillReference())
                .amount(invoice.getInvoiceAmount())
                .description("Auto monthly invoice payment")
                .sourceAccount(sourceAccount)
                .user(scheduled.getUser())
                .status(PaymentStatus.COMPLETED)
                .scheduledDate(processedAt)
                .processedDate(processedAt)
                .build());

        String transactionReference = "TXN-" + billPayment.getPaymentReference();
        if (transactionRepository.findByReferenceNumber(transactionReference).isEmpty()) {
            transactionRepository.save(Transaction.builder()
                    .referenceNumber(transactionReference)
                    .transactionType(TransactionType.BILL_PAYMENT)
                    .amount(invoice.getInvoiceAmount())
                    .description("Auto monthly invoice payment")
                    .sourceAccount(sourceAccount)
                    .destinationAccount(destinationAccount)
                    .status(PaymentStatus.COMPLETED)
                    .balanceAfter(sourceAccount.getBalance())
                    .transactionDate(processedAt)
                    .build());
        }

        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(processedAt);
        invoice.setBillPayment(billPayment);
        billerInvoiceRepository.save(invoice);

        scheduled.setLastProcessedMonth(month);
        scheduled.setLastProcessedYear(year);
        scheduled.setLastFailureReason(null);
        LocalDate nextExecution = calculateNextExecutionDate(
                scheduled.getNextExecutionDate() != null ? scheduled.getNextExecutionDate() : today,
                ScheduleFrequency.MONTHLY);
        scheduled.setNextExecutionDate(nextExecution);

        if (scheduled.getEndDate() != null && nextExecution.isAfter(scheduled.getEndDate())) {
            scheduled.setStatus(PaymentStatus.CANCELLED);
        }

        scheduledPaymentRepository.save(scheduled);
        notificationService.notifyBillPaymentProcessed(billPayment);

        log.info("Auto-pay invoice paid: setupId={}, invoiceId={}, amount={}",
                scheduled.getId(), invoice.getId(), invoice.getInvoiceAmount());
    }

    /**
     * Keeps existing non-invoice recurring schedules working as-is.
     */
    private void executeSingleLegacyScheduledPayment(ScheduledBillPayment scheduled) {
        Account sourceAccount = scheduled.getAccount();
        Account destinationAccount = resolveSettlementAccount(scheduled.getBiller());

        if (sourceAccount.getBalance().compareTo(scheduled.getAmount()) < 0) {
            log.warn("Insufficient funds for legacy scheduled payment id={}", scheduled.getId());
            return;
        }

        sourceAccount.setBalance(sourceAccount.getBalance().subtract(scheduled.getAmount()));
        destinationAccount.setBalance(destinationAccount.getBalance().add(scheduled.getAmount()));
        accountRepository.save(sourceAccount);
        accountRepository.save(destinationAccount);

        LocalDateTime processedAt = LocalDateTime.now();

        BillPayment billPayment = billPaymentRepository.save(BillPayment.builder()
                .paymentReference(generateScheduledPaymentReference())
                .biller(scheduled.getBiller())
                .accountNumber(scheduled.getBillReference())
                .amount(scheduled.getAmount())
                .description(scheduled.getDescription())
                .sourceAccount(sourceAccount)
                .user(scheduled.getUser())
                .status(PaymentStatus.COMPLETED)
                .scheduledDate(processedAt)
                .processedDate(processedAt)
                .build());

        transactionRepository.save(Transaction.builder()
                .referenceNumber("TXN-" + billPayment.getPaymentReference())
                .transactionType(TransactionType.BILL_PAYMENT)
                .amount(scheduled.getAmount())
                .description(scheduled.getDescription())
                .sourceAccount(sourceAccount)
                .destinationAccount(destinationAccount)
                .status(PaymentStatus.COMPLETED)
                .balanceAfter(sourceAccount.getBalance())
                .transactionDate(processedAt)
                .build());

        LocalDate nextExecution = calculateNextExecutionDate(scheduled.getNextExecutionDate(), scheduled.getFrequency());
        scheduled.setNextExecutionDate(nextExecution);

        if (scheduled.getEndDate() != null && nextExecution.isAfter(scheduled.getEndDate())) {
            scheduled.setStatus(PaymentStatus.CANCELLED);
        }

        scheduledPaymentRepository.save(scheduled);
    }

    private void markAutoPayFailure(ScheduledBillPayment scheduled, String reason) {
        scheduled.setLastFailureReason(reason);
        scheduledPaymentRepository.save(scheduled);
    }

    private void createFailureNotification(ScheduledBillPayment scheduled, BillerInvoice invoice) {
        notificationRepository.save(Notification.builder()
                .user(scheduled.getUser())
                .title("Auto bill payment failed")
                .message(String.format(
                        "Auto-payment for %s invoice %d/%d failed due to insufficient balance.",
                        scheduled.getBiller().getBillerName(),
                        invoice.getInvoiceMonth(),
                        invoice.getInvoiceYear()))
                .type(NotificationType.SYSTEM)
                .build());
    }

    private BillerInvoiceResponse toInvoiceResponse(BillerInvoice invoice) {
        return BillerInvoiceResponse.builder()
                .id(invoice.getId())
                .billerId(invoice.getBiller().getId())
                .billerName(invoice.getBiller().getBillerName())
                .customerReference(invoice.getCustomerReference())
                .invoiceMonth(invoice.getInvoiceMonth())
                .invoiceYear(invoice.getInvoiceYear())
                .invoiceAmount(invoice.getInvoiceAmount())
                .dueDate(invoice.getDueDate())
                .status(invoice.getStatus())
                .paymentReference(invoice.getBillPayment() != null ? invoice.getBillPayment().getPaymentReference() : null)
                .paidAt(invoice.getPaidAt())
                .build();
    }

    private Account resolveSettlementAccount(Biller biller) {
        if (biller.getSettlementAccountNumber() == null || biller.getSettlementAccountNumber().isBlank()) {
            throw new BadRequestException("Biller does not have a settlement account configured");
        }

        return accountRepository.findByAccountNumber(biller.getSettlementAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Settlement account not found for biller code: " + biller.getBillerCode()));
    }

    private String generateScheduledPaymentReference() {
        return "PAY" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
    }

    private LocalDate calculateNextExecutionDate(LocalDate currentDate, ScheduleFrequency frequency) {
        return switch (frequency) {
            case ONCE -> currentDate;
            case WEEKLY -> currentDate.plusWeeks(1);
            case BIWEEKLY -> currentDate.plusWeeks(2);
            case MONTHLY -> currentDate.plusMonths(1);
            case QUARTERLY -> currentDate.plusMonths(3);
            case ANNUALLY -> currentDate.plusYears(1);
        };
    }
}
