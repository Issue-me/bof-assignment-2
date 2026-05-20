package com.bof.banking.service.impl;

import com.bof.banking.dto.transaction.TransferCategoryLimitUsageResponse;
import com.bof.banking.dto.transaction.TransferLimitSummaryResponse;
import com.bof.banking.exception.ResourceNotFoundException;
import com.bof.banking.exception.TransferLimitExceededException;
import com.bof.banking.model.TransferLimit;
import com.bof.banking.model.User;
import com.bof.banking.model.enums.PaymentStatus;
import com.bof.banking.model.enums.TransactionType;
import com.bof.banking.model.enums.TransferLimitCategory;
import com.bof.banking.repository.TransactionRepository;
import com.bof.banking.repository.TransferLimitRepository;
import com.bof.banking.repository.UserRepository;
import com.bof.banking.service.TransferLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Enforces transfer limits and produces usage summaries.
 */
@Service
@RequiredArgsConstructor
public class TransferLimitServiceImpl implements TransferLimitService {

    private static final ZoneId TRANSFER_ZONE = ZoneId.of("Pacific/Fiji");

    private final TransferLimitRepository transferLimitRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

        @Override
        @Transactional(readOnly = true)
    /**
     * Checks whether transfer is valid.
     * @param user the authenticated user context.
     * @param ownAccountTransfer the own Account Transfer.
     * @param amount the monetary value used by this operation.
     */
    public void validateTransfer(User user, boolean ownAccountTransfer, BigDecimal amount) {
        TransferLimit limit = getOrCreateLimit(ownAccountTransfer
                ? TransferLimitCategory.OWN_ACCOUNT
                : TransferLimitCategory.EXTERNAL);

        LocalDateTime now = LocalDateTime.now(TRANSFER_ZONE);

        assertLimit(limit.getDailyLimit(),
                getUsedAmount(user.getId(), ownAccountTransfer, now.minusDays(1)),
                amount,
                "daily");
        assertLimit(limit.getWeeklyLimit(),
                getUsedAmount(user.getId(), ownAccountTransfer, now.minusWeeks(1)),
                amount,
                "weekly");
        assertLimit(limit.getMonthlyLimit(),
                getUsedAmount(user.getId(), ownAccountTransfer, now.minusMonths(1)),
                amount,
                "monthly");
        assertLimit(limit.getYearlyLimit(),
                getUsedAmount(user.getId(), ownAccountTransfer, now.minusYears(1)),
                amount,
                "yearly");
    }

        @Override
        @Transactional(readOnly = true)
    /**
     * Returns summary data.
     * @param userEmail the email of the authenticated user.
     * @return the result of the operation.
     */
    public TransferLimitSummaryResponse getSummary(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ZonedDateTime now = ZonedDateTime.now(TRANSFER_ZONE);
        LocalDateTime dailyStart = now.minusDays(1).toLocalDateTime();
        LocalDateTime weeklyStart = now.minusWeeks(1).toLocalDateTime();
        LocalDateTime monthlyStart = now.minusMonths(1).toLocalDateTime();
        LocalDateTime yearlyStart = now.minusYears(1).toLocalDateTime();

        TransferCategoryLimitUsageResponse own = buildCategorySummary(
                user.getId(),
                true,
                getOrCreateLimit(TransferLimitCategory.OWN_ACCOUNT),
                dailyStart,
                weeklyStart,
                monthlyStart,
                yearlyStart);

        TransferCategoryLimitUsageResponse external = buildCategorySummary(
                user.getId(),
                false,
                getOrCreateLimit(TransferLimitCategory.EXTERNAL),
                dailyStart,
                weeklyStart,
                monthlyStart,
                yearlyStart);

        return TransferLimitSummaryResponse.builder()
                .timezone(TRANSFER_ZONE.getId())
                .generatedAt(now.toLocalDateTime())
                .categories(List.of(external, own))
                .build();
    }

    private TransferCategoryLimitUsageResponse buildCategorySummary(
            Long userId,
            boolean ownAccount,
            TransferLimit limit,
            LocalDateTime dailyStart,
            LocalDateTime weeklyStart,
            LocalDateTime monthlyStart,
            LocalDateTime yearlyStart) {

        BigDecimal dailyUsed = getUsedAmount(userId, ownAccount, dailyStart);
        BigDecimal weeklyUsed = getUsedAmount(userId, ownAccount, weeklyStart);
        BigDecimal monthlyUsed = getUsedAmount(userId, ownAccount, monthlyStart);
        BigDecimal yearlyUsed = getUsedAmount(userId, ownAccount, yearlyStart);

        return TransferCategoryLimitUsageResponse.builder()
                .category(limit.getCategory())
                .dailyLimit(limit.getDailyLimit())
                .dailyUsed(dailyUsed)
                .dailyRemaining(remaining(limit.getDailyLimit(), dailyUsed))
                .weeklyLimit(limit.getWeeklyLimit())
                .weeklyUsed(weeklyUsed)
                .weeklyRemaining(remaining(limit.getWeeklyLimit(), weeklyUsed))
                .monthlyLimit(limit.getMonthlyLimit())
                .monthlyUsed(monthlyUsed)
                .monthlyRemaining(remaining(limit.getMonthlyLimit(), monthlyUsed))
                .yearlyLimit(limit.getYearlyLimit())
                .yearlyUsed(yearlyUsed)
                .yearlyRemaining(remaining(limit.getYearlyLimit(), yearlyUsed))
                .build();
    }

    private void assertLimit(BigDecimal limit, BigDecimal used, BigDecimal requestedAmount, String periodLabel) {
        BigDecimal projected = used.add(requestedAmount);
        if (projected.compareTo(limit) > 0) {
            throw new TransferLimitExceededException(String.format(
                    "Transfer %s limit reached. Limit: %s, Used: %s, Requested: %s",
                    periodLabel,
                    limit,
                    used,
                    requestedAmount));
        }
    }

    private BigDecimal remaining(BigDecimal limit, BigDecimal used) {
        BigDecimal value = limit.subtract(used);
        return value.signum() < 0 ? BigDecimal.ZERO : value;
    }

    private BigDecimal getUsedAmount(Long userId, boolean ownAccount, LocalDateTime startDate) {
                BigDecimal result;
        if (ownAccount) {
                        result = transactionRepository.sumCompletedOwnTransfersSince(
                    userId,
                    startDate,
                    TransactionType.TRANSFER,
                    PaymentStatus.COMPLETED);
                        return result != null ? result : BigDecimal.ZERO;
        }

                result = transactionRepository.sumCompletedExternalTransfersSince(
                userId,
                startDate,
                TransactionType.TRANSFER,
                PaymentStatus.COMPLETED);
                return result != null ? result : BigDecimal.ZERO;
    }

        private TransferLimit getOrCreateLimit(TransferLimitCategory category) {
                return transferLimitRepository.findByCategory(category)
                                .orElseGet(() -> defaultLimit(category));
    }

    private TransferLimit defaultLimit(TransferLimitCategory category) {
        if (category == TransferLimitCategory.OWN_ACCOUNT) {
            return TransferLimit.builder()
                    .category(TransferLimitCategory.OWN_ACCOUNT)
                    .dailyLimit(new BigDecimal("99999999.90"))
                    .weeklyLimit(new BigDecimal("99999999.90"))
                    .monthlyLimit(new BigDecimal("999999999"))
                    .yearlyLimit(new BigDecimal("9999999999"))
                    .build();
        }

        return TransferLimit.builder()
                .category(TransferLimitCategory.EXTERNAL)
                .dailyLimit(new BigDecimal("5000"))
                .weeklyLimit(new BigDecimal("35000"))
                .monthlyLimit(new BigDecimal("140000"))
                .yearlyLimit(new BigDecimal("1680000"))
                .build();
    }
}
