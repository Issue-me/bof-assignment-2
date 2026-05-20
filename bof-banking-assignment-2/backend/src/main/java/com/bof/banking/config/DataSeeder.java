package com.bof.banking.config;

import com.bof.banking.model.*;
import com.bof.banking.model.enums.*;
import com.bof.banking.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Seeds demo data when bof.seed-demo-data=true and the database is empty.
 *
 * Maintenance fee rule (mirrors AccountServiceImpl.applyMaintenanceFeesByAnniversaryDay):
 *   - Fee is NOT charged in the same calendar month the account was created.
 *   - First charge occurs in the month AFTER creation, on the anniversary day.
 *   - anniversaryDay = Math.min(creationDay, lastDayOfMonth)
 *
 * Since both simple access accounts are created at seed time (createdAt = now),
 * the seeded fee transactions start from the month AFTER the current month.
 * For past months this year, the first fee month is:
 *   firstFeeMonth = creationMonth + 1
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository                       userRepository;
    private final AccountRepository                    accountRepository;
    private final TransactionRepository                transactionRepository;
    private final SavingsInterestTransactionRepository savingsInterestTransactionRepository;
    private final LoanRepository                       loanRepository;
    private final LoanRateRepository                   loanRateRepository;
    private final InvestmentRepository                 investmentRepository;
    private final BillerRepository                     billerRepository;
    private final BillerInvoiceRepository              billerInvoiceRepository;
    private final BillPaymentRepository                billPaymentRepository;
    private final ScheduledBillPaymentRepository       scheduledBillPaymentRepository;
    private final PasswordEncoder                      passwordEncoder;
    private final BankingProperties                    bankingProperties;
    private final InterestRateRepository               interestRateRepository;
    private final InterestSummaryRepository            interestSummaryRepository;

    @Override
    /**
     * Handles run.
     * @param args application startup arguments.
     */
    public void run(String... args) {
        if (!bankingProperties.isSeedDemoData()) {
            log.info("Demo data seeding disabled (bof.seed-demo-data=false)");
            return;
        }
        if (userRepository.count() > 0) {
                        log.info("Database already has {} users — skipping full seed", userRepository.count());
                        seedCustomerMockInvoicesForExistingData();
            return;
        }
        log.info("Starting demo data seeding");
        seedData();
        log.info("Demo data seeding complete. Users: {}", userRepository.count());
    }

        private void seedCustomerMockInvoicesForExistingData() {
                int currentYear = LocalDate.now().getYear();

                User adrian = userRepository.findByEmail("adrianobadiah4@gmail.com").orElse(null);
                if (adrian == null) {
                        return;
                }

                Biller feaBiller = billerRepository.findByBillerCode("FEA001").orElse(null);
                if (feaBiller == null) {
                        return;
                }

                String adrianFeaReference = "FEA-ACC-123456";
                BigDecimal[] adrianFeaMonthlyAmounts = {
                                new BigDecimal("118.40"),
                                new BigDecimal("124.95"),
                                new BigDecimal("132.10"),
                                new BigDecimal("126.75"),
                                new BigDecimal("140.20"),
                                new BigDecimal("136.45"),
                                new BigDecimal("149.80"),
                                new BigDecimal("143.55"),
                                new BigDecimal("155.30"),
                                new BigDecimal("147.25"),
                                new BigDecimal("139.60"),
                                new BigDecimal("152.90")
                };

                int inserted = 0;
                for (int month = 1; month <= 12; month++) {
                        if (!billerInvoiceRepository.existsByBillerAndCustomerReferenceAndInvoiceMonthAndInvoiceYear(
                                        feaBiller, adrianFeaReference, month, currentYear)) {
                                billerInvoiceRepository.save(BillerInvoice.builder()
                                                .biller(feaBiller)
                                                .customerReference(adrianFeaReference)
                                                .invoiceMonth(month)
                                                .invoiceYear(currentYear)
                                                .invoiceAmount(adrianFeaMonthlyAmounts[month - 1])
                                                .dueDate(LocalDate.of(currentYear, month, 22))
                                                .status(InvoiceStatus.UNPAID)
                                                .build());
                                inserted++;
                        }
                }

                Account adrianSimpleAccess = accountRepository.findByAccountNumber("BOF55443322").orElse(null);
                if (adrianSimpleAccess != null) {
                        boolean alreadyExists = scheduledBillPaymentRepository
                                        .findByUserOrderByStartDateAsc(adrian)
                                        .stream()
                                        .anyMatch(s -> s.getBiller() != null
                                                        && s.getBiller().getId().equals(feaBiller.getId())
                                                        && adrianFeaReference.equals(s.getBillReference()));

                        if (!alreadyExists) {
                                scheduledBillPaymentRepository.save(ScheduledBillPayment.builder()
                                                .user(adrian)
                                                .account(adrianSimpleAccess)
                                                .biller(feaBiller)
                                                .billReference(adrianFeaReference)
                                                .amount(new BigDecimal("149.80"))
                                                .frequency(ScheduleFrequency.MONTHLY)
                                                .startDate(LocalDate.now().minusMonths(2))
                                                .nextExecutionDate(LocalDate.now().plusDays(30))
                                                .autoPayEnabled(true)
                                                .approvalGiven(true)
                                                .status(PaymentStatus.ACTIVE)
                                                .description("Monthly electricity — Adrian simple access")
                                                .build());
                        }
                }

                if (inserted > 0) {
                        log.info("Inserted {} customer FEA mock invoices for reference {}", inserted, adrianFeaReference);
                }
        }

    private void seedData() {
        String prefix      = bankingProperties.getCustomerIdPrefix();
        int    currentYear = LocalDate.now().getYear();
        int    nowMonth    = LocalDate.now().getMonthValue();

        final BigDecimal ANNUAL_RATE     = new BigDecimal("0.035000");
        final BigDecimal ANNUAL_RATE_PCT = new BigDecimal("3.5");
        final BigDecimal RIWT_RATE       = new BigDecimal("0.10");
        final BigDecimal PERSONAL_RATE   = new BigDecimal("0.085000");
        final BigDecimal HOME_RATE       = new BigDecimal("0.065000");

        final BigDecimal PERRY_LOAN_PRINCIPAL    = new BigDecimal("12000.00");
        final BigDecimal ADRIAN_LOAN_PRINCIPAL   = new BigDecimal("25000.00");
        final BigDecimal PERRY_LOAN_OUTSTANDING  = new BigDecimal("10920.00");
        final BigDecimal ADRIAN_LOAN_OUTSTANDING = new BigDecimal("25000.00");
        final BigDecimal totalLoanDisbursements  = PERRY_LOAN_PRINCIPAL.add(ADRIAN_LOAN_PRINCIPAL);

        // ── Pre-calculate savings interest totals ──────────────────────────────
        // Perry is non-resident (no TIN) → NRWHT (10%) withheld on all interest.
        // Adrian is resident with TIN    → RIWT  (10%) withheld on all interest.
        // The withholding rate is the same (10%); the description distinguishes them.
        BigDecimal perryGrossMonthly  = monthlyInterest(new BigDecimal("100000.00"), ANNUAL_RATE);
        BigDecimal adrianGrossMonthly = monthlyInterest(new BigDecimal("6000.00"),   ANNUAL_RATE);
        BigDecimal perryNrwhtMonthly  = perryGrossMonthly.multiply(RIWT_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal adrianRiwtMonthly  = adrianGrossMonthly.multiply(RIWT_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal perryNetMonthly    = perryGrossMonthly.subtract(perryNrwhtMonthly);
        BigDecimal adrianNetMonthly   = adrianGrossMonthly.subtract(adrianRiwtMonthly);
        BigDecimal perryTotalGross    = perryGrossMonthly.multiply(BigDecimal.valueOf(nowMonth));
        BigDecimal adrianTotalGross   = adrianGrossMonthly.multiply(BigDecimal.valueOf(nowMonth));
        BigDecimal perryTotalNet      = perryNetMonthly.multiply(BigDecimal.valueOf(nowMonth));
        BigDecimal adrianTotalNet     = adrianNetMonthly.multiply(BigDecimal.valueOf(nowMonth));

        BigDecimal totalInterestOut = perryTotalNet.add(adrianTotalNet);
        BigDecimal bankBalance = new BigDecimal("100000000.00")
                .subtract(totalInterestOut)
                .subtract(totalLoanDisbursements);

        // ── Users ──────────────────────────────────────────────────────────────

        User admin = userRepository.save(User.builder()
                .customerId(String.format("%s-%06d", prefix, 0))
                .firstName("Admin").lastName("BoF").email("admin@bof.com.fj")
                .password(passwordEncoder.encode("test@123")).phoneNumber("+679 999 0000")
                .role(Role.ADMIN).isActive(true).isResident(true).isSeniorCitizen(false)
                .nationalId("FJ-ADM-000").dateOfBirth(LocalDate.of(1985, 1, 1))
                .address("Suva, Fiji").build());

        User toniHarmNam = userRepository.save(User.builder()
                .customerId(String.format("%s-%06d", prefix, 6))
                .firstName("Toni").lastName("HarmNam").email("toniharmnam@gmail.com")
                .password(passwordEncoder.encode("test@123")).phoneNumber("+679 999 0006")
                .role(Role.ADMIN).isActive(true).isResident(true).isSeniorCitizen(false)
                .nationalId("FJ-ADM-006").dateOfBirth(LocalDate.of(1990, 5, 20))
                .address("Suva, Fiji").build());

        // Perry is seeded as non-resident with no TIN → NRWHT applies on all interest.
        // Admin can trigger the refund by updating Perry's profile (isResident=true + TIN).
        User perry = userRepository.save(User.builder()
                .customerId(String.format("%s-%06d", prefix, 1))
                .firstName("Perry").lastName("Siola").email("perrysiola2022@gmail.com")
                .password(passwordEncoder.encode("12341234")).phoneNumber("+679 999 0001")
                .role(Role.CUSTOMER).isActive(true).isResident(false).isSeniorCitizen(false)
                .nationalId("FJ-PER-001")
                .dateOfBirth(LocalDate.of(1992, 4, 11)).address("Suva, Fiji").build());

        User adrian = userRepository.save(User.builder()
                .customerId(String.format("%s-%06d", prefix, 2))
                .firstName("Adrian").lastName("Obadiah").email("adrianobadiah4@gmail.com")
                .password(passwordEncoder.encode("12341234")).phoneNumber("+679 999 0002")
                .role(Role.CUSTOMER).isActive(true).isResident(true).isSeniorCitizen(false)
                .tinNumber("TIN654321").nationalId("FJ-ADR-002")
                .dateOfBirth(LocalDate.of(1990, 10, 3)).address("Nadi, Fiji").build());

        User bankSystem = userRepository.save(User.builder()
                .customerId(prefix + "-900000")
                .firstName("Bank").lastName("System").email("system.bank@bof.com.fj")
                .password(passwordEncoder.encode("Admin@123")).phoneNumber("+679 999 0900")
                .role(Role.ADMIN).isActive(true).isResident(true).isSeniorCitizen(false)
                .nationalId("FJ-SYS-900").dateOfBirth(LocalDate.of(1980, 1, 1))
                .address("Suva, Fiji").build());

        userRepository.save(User.builder()
                .customerId(String.format("%s-%06d", prefix, 3))
                .firstName("Gwen").lastName("Mar")
                .email("gwen.mar.fj@gmail.com")
                .password(passwordEncoder.encode("test@123"))
                .phoneNumber("+679 999 0003")
                .role(Role.TELLER).isActive(true).isResident(true).isSeniorCitizen(false)
                .nationalId("FJ-TEL-003").dateOfBirth(LocalDate.of(1988, 8, 24))
                .address("Lautoka, Fiji")
                .build());

        userRepository.save(User.builder()
                .customerId(String.format("%s-%06d", prefix, 4))
                .firstName("Ishal").lastName("Chand")
                .email("ishalprishikachand@gmail.com")
                .password(passwordEncoder.encode("test@123"))
                .phoneNumber("+679 999 0004")
                .role(Role.TELLER).isActive(true).isResident(true).isSeniorCitizen(false)
                .nationalId("FJ-TEL-004").dateOfBirth(LocalDate.of(1995, 3, 10))
                .address("Suva, Fiji")
                .build());



        // ── Accounts ───────────────────────────────────────────────────────────

        Account perrySavings = accountRepository.save(Account.builder()
                .accountNumber("BOF12345678").accountName("Perry Primary Savings")
                .accountType(AccountType.SAVINGS)
                .balance(new BigDecimal("210000.00").add(perryTotalNet))
                .interestRate(ANNUAL_RATE).interestEarned(perryTotalGross)
                .isActive(true).user(perry).build());

        Account perrySimpleAccess = accountRepository.save(Account.builder()
                .accountNumber("BOF87654321").accountName("Perry Simple Access")
                .accountType(AccountType.SIMPLE_ACCESS)
                .balance(new BigDecimal("6140.00")
                        .add(PERRY_LOAN_PRINCIPAL)
                        .subtract(PERRY_LOAN_PRINCIPAL.subtract(PERRY_LOAN_OUTSTANDING)))
                .interestRate(BigDecimal.ZERO).interestEarned(BigDecimal.ZERO)
                .isActive(true).user(perry).build());

        Account adrianSavings = accountRepository.save(Account.builder()
                .accountNumber("BOF22334455").accountName("Adrian Primary Savings")
                .accountType(AccountType.SAVINGS)
                .balance(new BigDecimal("6000.00").add(adrianTotalNet))
                .interestRate(ANNUAL_RATE).interestEarned(adrianTotalGross)
                .isActive(true).user(adrian).build());

        Account adrianSimpleAccess = accountRepository.save(Account.builder()
                .accountNumber("BOF55443322").accountName("Adrian Simple Access")
                .accountType(AccountType.SIMPLE_ACCESS)
                .balance(new BigDecimal("1800.00").add(ADRIAN_LOAN_PRINCIPAL))
                .interestRate(BigDecimal.ZERO).interestEarned(BigDecimal.ZERO)
                .isActive(true).user(adrian).build());

        Account bankOperations = accountRepository.save(Account.builder()
                .accountNumber("BOF90000001").accountName("BANK_INTERNAL_OPERATIONS")
                .accountType(AccountType.SAVINGS).balance(bankBalance)
                .interestRate(BigDecimal.ZERO).interestEarned(BigDecimal.ZERO)
                .isActive(true).user(bankSystem).build());

        Account feaSettlement = accountRepository.save(Account.builder()
                .accountNumber("BOF90001001").accountName("BILLER_SETTLEMENT_FEA001")
                .accountType(AccountType.SIMPLE_ACCESS).balance(BigDecimal.ZERO)
                .interestRate(BigDecimal.ZERO).interestEarned(BigDecimal.ZERO)
                .isActive(true).user(bankSystem).build());

        Account vodaSettlement = accountRepository.save(Account.builder()
                .accountNumber("BOF90001002").accountName("BILLER_SETTLEMENT_VODA01")
                .accountType(AccountType.SIMPLE_ACCESS).balance(BigDecimal.ZERO)
                .interestRate(BigDecimal.ZERO).interestEarned(BigDecimal.ZERO)
                .isActive(true).user(bankSystem).build());

        Account wafSettlement = accountRepository.save(Account.builder()
                .accountNumber("BOF90001003").accountName("BILLER_SETTLEMENT_WAF001")
                .accountType(AccountType.SIMPLE_ACCESS).balance(BigDecimal.ZERO)
                .interestRate(BigDecimal.ZERO).interestEarned(BigDecimal.ZERO)
                .isActive(true).user(bankSystem).build());

        // ── Billers ────────────────────────────────────────────────────────────

        Biller feaBiller = billerRepository.save(Biller.builder()
                .billerName("Fiji Electricity Authority").billerCode("FEA001")
                .category("UTILITIES").settlementAccountNumber(feaSettlement.getAccountNumber())
                .isActive(true).build());

        // ── FEA monthly invoice seed data (12 static amounts) ─────────────────

        String feaReference = "FEA-PERRY-MONTHLY";
        BigDecimal[] feaMonthlyAmounts = {
                new BigDecimal("134.15"),
                new BigDecimal("142.40"),
                new BigDecimal("128.95"),
                new BigDecimal("150.10"),
                new BigDecimal("137.85"),
                new BigDecimal("166.30"),
                new BigDecimal("159.75"),
                new BigDecimal("171.20"),
                new BigDecimal("145.65"),
                new BigDecimal("138.20"),
                new BigDecimal("147.50"),
                new BigDecimal("162.90")
        };

        String adrianFeaReference = "FEA-ACC-123456";
        BigDecimal[] adrianFeaMonthlyAmounts = {
                new BigDecimal("118.40"),
                new BigDecimal("124.95"),
                new BigDecimal("132.10"),
                new BigDecimal("126.75"),
                new BigDecimal("140.20"),
                new BigDecimal("136.45"),
                new BigDecimal("149.80"),
                new BigDecimal("143.55"),
                new BigDecimal("155.30"),
                new BigDecimal("147.25"),
                new BigDecimal("139.60"),
                new BigDecimal("152.90")
        };

        for (int month = 1; month <= 12; month++) {
            if (!billerInvoiceRepository.existsByBillerAndCustomerReferenceAndInvoiceMonthAndInvoiceYear(
                    feaBiller, feaReference, month, currentYear)) {
                billerInvoiceRepository.save(BillerInvoice.builder()
                        .biller(feaBiller)
                        .customerReference(feaReference)
                        .invoiceMonth(month)
                        .invoiceYear(currentYear)
                        .invoiceAmount(feaMonthlyAmounts[month - 1])
                        .dueDate(LocalDate.of(currentYear, month, 20))
                        .status(InvoiceStatus.UNPAID)
                        .build());
            }

                        if (!billerInvoiceRepository.existsByBillerAndCustomerReferenceAndInvoiceMonthAndInvoiceYear(
                                        feaBiller, adrianFeaReference, month, currentYear)) {
                                billerInvoiceRepository.save(BillerInvoice.builder()
                                                .biller(feaBiller)
                                                .customerReference(adrianFeaReference)
                                                .invoiceMonth(month)
                                                .invoiceYear(currentYear)
                                                .invoiceAmount(adrianFeaMonthlyAmounts[month - 1])
                                                .dueDate(LocalDate.of(currentYear, month, 22))
                                                .status(InvoiceStatus.UNPAID)
                                                .build());
                        }
        }

        Biller vodafoneBiller = billerRepository.save(Biller.builder()
                .billerName("Vodafone Fiji").billerCode("VODA01")
                .category("TELECOM").settlementAccountNumber(vodaSettlement.getAccountNumber())
                .isActive(true).build());
        Biller wafBiller = billerRepository.save(Biller.builder()
                .billerName("Water Authority of Fiji").billerCode("WAF001")
                .category("UTILITIES").settlementAccountNumber(wafSettlement.getAccountNumber())
                .isActive(true).build());

        // ── Initial deposits ───────────────────────────────────────────────────

        transactionRepository.save(txDeposit("TXN-DEPOSIT-PERRY-SAV-001", new BigDecimal("100000.00"), perrySavings, currentYear, 1, 3, 9, 0));
        transactionRepository.save(txDeposit("TXN-DEPOSIT-PERRY-SA-001",  new BigDecimal("2500.00"),   perrySimpleAccess, currentYear, 1, 3, 9, 1));
        transactionRepository.save(txDeposit("TXN-DEPOSIT-ADRIAN-SAV-001", new BigDecimal("6000.00"),   adrianSavings, currentYear, 1, 3, 9, 2));
        transactionRepository.save(txDeposit("TXN-DEPOSIT-ADRIAN-SA-001",  new BigDecimal("1800.00"),   adrianSimpleAccess, currentYear, 1, 3, 9, 3));

        // ── Monthly salary (Perry) ─────────────────────────────────────────────

        for (int month = 1; month <= nowMonth; month++) {
            transactionRepository.save(Transaction.builder()
                    .referenceNumber("TXN-SALARY-PERRY-" + currentYear + "-" + fmt(month))
                    .transactionType(TransactionType.DEPOSIT).amount(new BigDecimal("3500.00"))
                    .description("Monthly salary credit").destinationAccount(perrySimpleAccess)
                    .status(PaymentStatus.COMPLETED)
                    .transactionDate(LocalDateTime.of(currentYear, month, 26, 8, 0)).build());
        }

        // ── Monthly savings interest: Perry & Adrian ─────────────────────────-

        for (int month = 1; month <= nowMonth; month++) {
            String mm       = fmt(month);
            int    lastDay  = LocalDate.of(currentYear, month, 1).lengthOfMonth();
            LocalDateTime creditedAt = LocalDateTime.of(currentYear, month, lastDay, 23, 59);
            String perryRef  = "TXN-INT-BOF12345678-" + currentYear + "-" + mm;
            String adrianRef = "TXN-INT-BOF22334455-" + currentYear + "-" + mm;

            BigDecimal perryBalAfter  = new BigDecimal("100000.00").add(perryNetMonthly.multiply(BigDecimal.valueOf(month)));
            BigDecimal adrianBalAfter = new BigDecimal("6000.00").add(adrianNetMonthly.multiply(BigDecimal.valueOf(month)));

            // Perry is non-resident (no TIN) → NRWHT deducted instead of RIWT
            transactionRepository.save(Transaction.builder().referenceNumber(perryRef)
                    .transactionType(TransactionType.INTEREST).amount(perryGrossMonthly)
                    .description("Monthly savings interest at " + ANNUAL_RATE_PCT + "% p.a. (NRWHT deducted: FJD " + perryNrwhtMonthly + " — non-resident)")
                    .sourceAccount(bankOperations).destinationAccount(perrySavings)
                    .status(PaymentStatus.COMPLETED).balanceAfter(perryBalAfter).transactionDate(creditedAt).build());

            savingsInterestTransactionRepository.save(SavingsInterestTransaction.builder()
                    .account(perrySavings).balanceSnapshot(new BigDecimal("100000.00")).annualRate(ANNUAL_RATE)
                    .grossInterest(perryGrossMonthly).riwtDeducted(perryNrwhtMonthly).netInterest(perryNetMonthly)
                    .interestMonth(month).interestYear(currentYear).riwtExempt(false).referenceNumber(perryRef).build());

            transactionRepository.save(Transaction.builder().referenceNumber(adrianRef)
                    .transactionType(TransactionType.INTEREST).amount(adrianGrossMonthly)
                    .description("Monthly savings interest at " + ANNUAL_RATE_PCT + "% p.a. (RIWT deducted: FJD " + adrianRiwtMonthly + ")")
                    .sourceAccount(bankOperations).destinationAccount(adrianSavings)
                    .status(PaymentStatus.COMPLETED).balanceAfter(adrianBalAfter).transactionDate(creditedAt).build());

            savingsInterestTransactionRepository.save(SavingsInterestTransaction.builder()
                    .account(adrianSavings).balanceSnapshot(new BigDecimal("6000.00")).annualRate(ANNUAL_RATE)
                    .grossInterest(adrianGrossMonthly).riwtDeducted(adrianRiwtMonthly).netInterest(adrianNetMonthly)
                    .interestMonth(month).interestYear(currentYear).riwtExempt(false).referenceNumber(adrianRef).build());
        }

        // ── UserInterestSummary: Perry (NRWHT — non-resident) & Adrian (RIWT — resident) ──

        // Perry: non-resident, no TIN → all withheld as NRWHT. Refund triggers when admin
        // sets isResident=true and provides TIN via the customer management screen.
        BigDecimal perryTotalNrwht = perryNrwhtMonthly.multiply(BigDecimal.valueOf(nowMonth));
        interestSummaryRepository.save(UserInterestSummary.builder()
                .user(perry).taxYear(currentYear)
                .grossInterestEarned(perryTotalGross)
                .nrwhtWithheld(perryTotalNrwht)
                .riwtWithheld(BigDecimal.ZERO)
                .netInterestPaid(perryTotalNet)
                .exemptionReason(null)
                .interestTransactionCount(nowMonth)
                .submittedToFrcs(false).build());

        // Adrian: resident with TIN → all withheld as RIWT.
        BigDecimal adrianTotalRiwt = adrianRiwtMonthly.multiply(BigDecimal.valueOf(nowMonth));
        interestSummaryRepository.save(UserInterestSummary.builder()
                .user(adrian).taxYear(currentYear)
                .grossInterestEarned(adrianTotalGross)
                .nrwhtWithheld(BigDecimal.ZERO)
                .riwtWithheld(adrianTotalRiwt)
                .netInterestPaid(adrianTotalNet)
                .exemptionReason(null)
                .interestTransactionCount(nowMonth)
                .submittedToFrcs(false).build());

        // ── Monthly maintenance fees ───────────────────────────────────────────
        //
        // KEY RULE: fee is NOT charged in the month the account was created.
        // First fee = month AFTER creation month. This mirrors the skip logic
        // in AccountServiceImpl.applyMaintenanceFeesByAnniversaryDay().
        //
        // Since both accounts are created at seed time (createdAt = LocalDateTime.now()),
        // the creation month = the current calendar month (nowMonth).
        //
        // Therefore: fee loop runs only for months STRICTLY BEFORE nowMonth,
        // starting from month 1 (January). No fee is seeded for the current month.
        //
        // Example: seeded in March (nowMonth = 3)
        //   Fee months: 1 (Jan), 2 (Feb)  — NOT March (creation month)
        //   Next fee will be charged by the live scheduler in April.

        int perryCreationDay = perrySimpleAccess.getCreatedAt() != null
                ? perrySimpleAccess.getCreatedAt().getDayOfMonth()
                : LocalDate.now().getDayOfMonth();
        int adrianCreationDay = adrianSimpleAccess.getCreatedAt() != null
                ? adrianSimpleAccess.getCreatedAt().getDayOfMonth()
                : LocalDate.now().getDayOfMonth();

        // FIX: use nowMonth - 1 as the upper bound so the creation month is excluded.
        // If nowMonth == 1 (January), no fees at all — first fee will come in February.
        int feeEndMonth = nowMonth - 1;

        if (feeEndMonth >= 1) {
            // Running balance base for Perry (after loan disbursement)
            BigDecimal perrySimpleBase = new BigDecimal("2500.00").add(PERRY_LOAN_PRINCIPAL); // 14500.00
            int adrianLoanMonth = LocalDate.now().minusDays(7).getMonthValue();

            for (int month = 1; month <= feeEndMonth; month++) {
                String mm = fmt(month);
                int lastDayOfMonth = LocalDate.of(currentYear, month, 1).lengthOfMonth();
                int perryFeeDay = Math.min(perryCreationDay, lastDayOfMonth);
                int adrianFeeDay = Math.min(adrianCreationDay, lastDayOfMonth);

                BigDecimal perryFeeBalance = perrySimpleBase
                        .subtract(new BigDecimal("2.50").multiply(BigDecimal.valueOf(month)));
                transactionRepository.save(Transaction.builder()
                        .referenceNumber("TXN-FEE-BOF87654321-" + currentYear + "-" + mm)
                        .transactionType(TransactionType.FEE).amount(new BigDecimal("2.50"))
                        .description("Monthly Simple Access maintenance fee")
                        .sourceAccount(perrySimpleAccess).status(PaymentStatus.COMPLETED)
                        .balanceAfter(perryFeeBalance)
                        .transactionDate(LocalDateTime.of(currentYear, month, perryFeeDay, 12, 0)).build());

                BigDecimal adrianSimpleBase = month >= adrianLoanMonth
                        ? new BigDecimal("1800.00").add(ADRIAN_LOAN_PRINCIPAL)
                        : new BigDecimal("1800.00");
                BigDecimal adrianFeeBalance = adrianSimpleBase
                        .subtract(new BigDecimal("2.50").multiply(BigDecimal.valueOf(month)));
                transactionRepository.save(Transaction.builder()
                        .referenceNumber("TXN-FEE-BOF55443322-" + currentYear + "-" + mm)
                        .transactionType(TransactionType.FEE).amount(new BigDecimal("2.50"))
                        .description("Monthly Simple Access maintenance fee")
                        .sourceAccount(adrianSimpleAccess).status(PaymentStatus.COMPLETED)
                        .balanceAfter(adrianFeeBalance)
                        .transactionDate(LocalDateTime.of(currentYear, month, adrianFeeDay, 12, 0)).build());
            }
            log.info("Seeded maintenance fees for months 1–{} (creation month {} excluded)",
                    feeEndMonth, nowMonth);
        } else {
            log.info("No maintenance fees seeded — accounts created in January (first fee due February)");
        }

        // ── One-off transactions ───────────────────────────────────────────────

        transactionRepository.save(Transaction.builder()
                .referenceNumber("TXN-TRANSFER-001").transactionType(TransactionType.TRANSFER)
                .amount(new BigDecimal("300.00")).description("Transfer — Perry simple access → Perry savings")
                .sourceAccount(perrySimpleAccess).destinationAccount(perrySavings)
                .status(PaymentStatus.COMPLETED).transactionDate(LocalDateTime.now().minusDays(10)).build());

        transactionRepository.save(Transaction.builder()
                .referenceNumber("TXN-BILLPAY-001").transactionType(TransactionType.BILL_PAYMENT)
                .amount(new BigDecimal("120.50")).description("Electricity bill payment")
                .sourceAccount(perrySimpleAccess).destinationAccount(feaSettlement)
                .status(PaymentStatus.COMPLETED).transactionDate(LocalDateTime.now().minusDays(5)).build());

        // ── Bill payments ──────────────────────────────────────────────────────

        billPaymentRepository.save(BillPayment.builder()
                .paymentReference("PAY-DEMO-001").biller(feaBiller)
                .accountNumber("FEA-ACC-123456").amount(new BigDecimal("120.50"))
                .description("Electricity bill").sourceAccount(perrySimpleAccess).user(perry)
                .status(PaymentStatus.COMPLETED)
                .scheduledDate(LocalDateTime.now().minusDays(6))
                .processedDate(LocalDateTime.now().minusDays(5)).build());

        billPaymentRepository.save(BillPayment.builder()
                .paymentReference("PAY-DEMO-002").biller(vodafoneBiller)
                .accountNumber("VODA-ACC-556677").amount(new BigDecimal("55.00"))
                .description("Mobile postpaid bill").sourceAccount(adrianSimpleAccess).user(adrian)
                .status(PaymentStatus.PENDING).scheduledDate(LocalDateTime.now().plusDays(2)).build());

        // ── Scheduled payments ─────────────────────────────────────────────────

        scheduledBillPaymentRepository.save(ScheduledBillPayment.builder()
                .user(perry).account(perrySimpleAccess).biller(feaBiller)
                .billReference("FEA-PERRY-MONTHLY").amount(new BigDecimal("135.75"))
                .frequency(ScheduleFrequency.MONTHLY).startDate(LocalDate.now().minusMonths(2))
                .nextExecutionDate(LocalDate.now().plusDays(5))
                .autoPayEnabled(true)
                .approvalGiven(true)
                .status(PaymentStatus.ACTIVE)
                .description("Monthly electricity — Perry simple access").build());

        scheduledBillPaymentRepository.save(ScheduledBillPayment.builder()
                .user(adrian).account(adrianSimpleAccess).biller(feaBiller)
                .billReference(adrianFeaReference).amount(new BigDecimal("149.80"))
                .frequency(ScheduleFrequency.MONTHLY)
                .startDate(LocalDate.now().minusMonths(2))
                .nextExecutionDate(LocalDate.now().plusDays(30))
                .autoPayEnabled(true)
                .approvalGiven(true)
                .status(PaymentStatus.ACTIVE)
                .description("Monthly electricity — Adrian simple access").build());

        scheduledBillPaymentRepository.save(ScheduledBillPayment.builder()
                .user(adrian).account(adrianSimpleAccess).biller(wafBiller)
                .billReference("WAF-ADRIAN-QUARTERLY").amount(new BigDecimal("90.00"))
                .frequency(ScheduleFrequency.QUARTERLY).startDate(LocalDate.now().minusMonths(1))
                .endDate(LocalDate.now().plusMonths(11)).nextExecutionDate(LocalDate.now().plusDays(20))
                .status(PaymentStatus.ACTIVE).description("Quarterly water — Adrian simple access").build());

        // ── Loans ──────────────────────────────────────────────────────────────

        BigDecimal perryMonthlyPayment = calcMonthlyPayment(PERRY_LOAN_PRINCIPAL, PERSONAL_RATE, 36);
        BigDecimal adrianMonthlyPayment = calcMonthlyPayment(ADRIAN_LOAN_PRINCIPAL, HOME_RATE, 60);

        loanRepository.save(Loan.builder().loanNumber("LN-2026-0001").loanType("Personal Loan")
                .purpose("Debt Consolidation").principalAmount(PERRY_LOAN_PRINCIPAL)
                .interestRate(PERSONAL_RATE).termMonths(36).monthlyPayment(perryMonthlyPayment)
                .outstandingBalance(PERRY_LOAN_OUTSTANDING).status(LoanStatus.ACTIVE).user(perry)
                .disbursementAccount(perrySimpleAccess).employmentType("Permanent Employee")
                .monthlyIncome(new BigDecimal("4200.00"))
                .applicationDate(LocalDate.now().minusDays(90)).approvalDate(LocalDate.now().minusDays(80))
                .startDate(LocalDate.now().minusDays(75)).endDate(LocalDate.now().plusYears(3)).build());

        loanRepository.save(Loan.builder().loanNumber("LN-2026-0002").loanType("Home Loan")
                .purpose("Purchase").principalAmount(ADRIAN_LOAN_PRINCIPAL)
                .interestRate(HOME_RATE).termMonths(60).monthlyPayment(adrianMonthlyPayment)
                .outstandingBalance(ADRIAN_LOAN_OUTSTANDING).status(LoanStatus.ACTIVE).user(adrian)
                .disbursementAccount(adrianSimpleAccess).employmentType("Civil Servant")
                .monthlyIncome(new BigDecimal("3800.00"))
                .applicationDate(LocalDate.now().minusDays(20)).approvalDate(LocalDate.now().minusDays(7))
                .startDate(LocalDate.now().minusDays(5)).endDate(LocalDate.now().plusYears(5)).build());

        transactionRepository.save(Transaction.builder()
                .referenceNumber("LOAN-DISB-LN-2026-0001").transactionType(TransactionType.LOAN_DISBURSEMENT)
                .amount(PERRY_LOAN_PRINCIPAL)
                .description("Loan disbursement — Personal Loan [LN-2026-0001] to " + perrySimpleAccess.getAccountNumber())
                .sourceAccount(bankOperations).destinationAccount(perrySimpleAccess)
                .status(PaymentStatus.COMPLETED)
                .balanceAfter(new BigDecimal("2500.00").add(PERRY_LOAN_PRINCIPAL))
                .transactionDate(LocalDateTime.now().minusDays(80)).build());

        transactionRepository.save(Transaction.builder()
                .referenceNumber("LOAN-DISB-LN-2026-0002").transactionType(TransactionType.LOAN_DISBURSEMENT)
                .amount(ADRIAN_LOAN_PRINCIPAL)
                .description("Loan disbursement — Home Loan [LN-2026-0002] to " + adrianSimpleAccess.getAccountNumber())
                .sourceAccount(bankOperations).destinationAccount(adrianSimpleAccess)
                .status(PaymentStatus.COMPLETED)
                .balanceAfter(new BigDecimal("1800.00").add(ADRIAN_LOAN_PRINCIPAL))
                .transactionDate(LocalDateTime.now().minusDays(7)).build());

        // ── Investments ────────────────────────────────────────────────────────

        investmentRepository.save(Investment.builder().investmentNumber("INV-2026-0001")
                .investmentType("TERM_DEPOSIT").principalAmount(new BigDecimal("8000.00"))
                .interestRate(new BigDecimal("0.0450")).currentValue(new BigDecimal("8230.00"))
                .termMonths(12).isActive(true).user(perry).linkedAccount(perrySavings)
                .startDate(LocalDate.now().minusDays(120)).maturityDate(LocalDate.now().plusDays(245)).build());

        investmentRepository.save(Investment.builder().investmentNumber("INV-2026-0002")
                .investmentType("UNIT_TRUST").principalAmount(new BigDecimal("5000.00"))
                .interestRate(new BigDecimal("0.0350")).currentValue(new BigDecimal("5125.00"))
                .termMonths(24).isActive(true).user(adrian).linkedAccount(adrianSavings)
                .startDate(LocalDate.now().minusDays(180)).maturityDate(LocalDate.now().plusDays(550)).build());

        // ── Default rates ──────────────────────────────────────────────────────

        if (interestRateRepository.count() == 0) {
            interestRateRepository.save(InterestRate.builder().annualRate(ANNUAL_RATE)
                    .effectiveFrom(LocalDate.of(currentYear, 1, 1))
                    .changeReason("Default rate — 3.5% p.a. per RBF minimum variable rate directive")
                    .setBy("system").build());
        }

        if (loanRateRepository.count() == 0) {
            loanRateRepository.save(LoanRate.builder().loanType("Personal Loan").annualRate(PERSONAL_RATE).setBy("system").changeReason("Default rate — 8.5% p.a.").build());
            loanRateRepository.save(LoanRate.builder().loanType("Home Loan").annualRate(HOME_RATE).setBy("system").changeReason("Default rate — 6.5% p.a.").build());
            loanRateRepository.save(LoanRate.builder().loanType("Vehicle Loan").annualRate(new BigDecimal("0.075000")).setBy("system").changeReason("Default rate — 7.5% p.a.").build());
            loanRateRepository.save(LoanRate.builder().loanType("Business Loan").annualRate(new BigDecimal("0.090000")).setBy("system").changeReason("Default rate — 9.0% p.a.").build());
        }

        log.info("Seeded: accounts={} transactions={} interestTxns={} loans=2 investments=2",
                accountRepository.count(), transactionRepository.count(),
                savingsInterestTransactionRepository.count());
        log.info("Bank balance: FJD {}", bankBalance);
        log.info("Maintenance fees seeded for months 1–{} (month {} = creation month, first live fee = month {})",
                feeEndMonth, nowMonth, nowMonth + 1);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Transaction txDeposit(String ref, BigDecimal amount, Account dest,
                                   int year, int month, int day, int hour, int min) {
        return Transaction.builder()
                .referenceNumber(ref).transactionType(TransactionType.DEPOSIT)
                .amount(amount).description("Initial deposit")
                .destinationAccount(dest).status(PaymentStatus.COMPLETED)
                .balanceAfter(amount)
                .transactionDate(LocalDateTime.of(year, month, day, hour, min)).build();
    }

    private String fmt(int month) {
        return String.format("%02d", month);
    }

    private BigDecimal monthlyInterest(BigDecimal balance, BigDecimal annualRate) {
        return balance.multiply(annualRate).divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calcMonthlyPayment(BigDecimal principal, BigDecimal annualRate, int termMonths) {
        BigDecimal r        = annualRate.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
        BigDecimal onePlusR = BigDecimal.ONE.add(r);
        BigDecimal power    = onePlusR.pow(termMonths, MathContext.DECIMAL64);
        BigDecimal num      = principal.multiply(r).multiply(power);
        BigDecimal den      = power.subtract(BigDecimal.ONE);
        return num.divide(den, 2, RoundingMode.HALF_UP);
    }
}
