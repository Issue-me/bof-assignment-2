package com.bof.banking.mapper;

import com.bof.banking.dto.account.AccountResponse;
import com.bof.banking.dto.account.AccountResponseDto;
import com.bof.banking.model.Account;
import org.springframework.stereotype.Component;

/**
 * Mapper for Account entity to DTO conversions.
 */
@Component
public class AccountMapper {

    /**
     * Handles to response.
     * @param account the account.
     * @return the result of the operation.
     */
    public AccountResponse toResponse(Account account) {
        if (account == null) {
            return null;
        }
        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .accountName(account.getAccountName())
                .ownerName(resolveOwnerName(account))
                .accountType(account.getAccountType())
                .balance(account.getBalance())
                .interestRate(account.getInterestRate())
                .interestEarned(account.getInterestEarned())
                .active(account.isActive())
                .createdAt(account.getCreatedAt())
                .build();
    }

    /**
     * Handles to response dto.
     * @param account the account.
     * @return the result of the operation.
     */
    public AccountResponseDto toResponseDto(Account account) {
        if (account == null) {
            return null;
        }
        return AccountResponseDto.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .accountName(account.getAccountName())
                .accountType(account.getAccountType())
                .balance(account.getBalance())
                .interestRate(account.getInterestRate())
                .interestEarned(account.getInterestEarned())
                .active(account.isActive())
                .createdAt(account.getCreatedAt())
                .build();
    }

    private String resolveOwnerName(Account account) {
        if (account.getUser() == null) {
            return null;
        }

        String firstName = account.getUser().getFirstName() == null ? "" : account.getUser().getFirstName().trim();
        String lastName = account.getUser().getLastName() == null ? "" : account.getUser().getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();

        return fullName.isEmpty() ? null : fullName;
    }
}
