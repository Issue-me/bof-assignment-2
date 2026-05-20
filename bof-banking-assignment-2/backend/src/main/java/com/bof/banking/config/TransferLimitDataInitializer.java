package com.bof.banking.config;

import com.bof.banking.model.TransferLimit;
import com.bof.banking.model.enums.TransferLimitCategory;
import com.bof.banking.repository.TransferLimitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Ensures default transfer limits exist in the database at startup.
 */
@Component
@RequiredArgsConstructor
public class TransferLimitDataInitializer implements ApplicationRunner {

    private final TransferLimitRepository transferLimitRepository;

    @Override
    @Transactional
    /**
     * Handles run.
     * @param args application startup arguments.
     */
    public void run(ApplicationArguments args) {
        upsertDefault(TransferLimitCategory.EXTERNAL,
                new BigDecimal("5000"),
                new BigDecimal("35000"),
                new BigDecimal("140000"),
                new BigDecimal("1680000"));

        upsertDefault(TransferLimitCategory.OWN_ACCOUNT,
                new BigDecimal("99999999.90"),
                new BigDecimal("99999999.90"),
                new BigDecimal("999999999"),
                new BigDecimal("9999999999"));
    }

    private void upsertDefault(
            TransferLimitCategory category,
            BigDecimal daily,
            BigDecimal weekly,
            BigDecimal monthly,
            BigDecimal yearly) {

        transferLimitRepository.findByCategory(category)
                .orElseGet(() -> transferLimitRepository.save(
                        TransferLimit.builder()
                                .category(category)
                                .dailyLimit(daily)
                                .weeklyLimit(weekly)
                                .monthlyLimit(monthly)
                                .yearlyLimit(yearly)
                                .build()));
    }
}
