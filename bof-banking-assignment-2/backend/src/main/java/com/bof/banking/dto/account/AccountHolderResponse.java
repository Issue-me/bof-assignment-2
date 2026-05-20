package com.bof.banking.dto.account;

import com.bof.banking.model.enums.AccountHolderRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for account holder response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountHolderResponse {
    
    private Long id;
    private Long accountId;
    private Long userId;
    private String userEmail;
    private String userFullName;
    private AccountHolderRole role;
    private LocalDateTime addedAt;
    private Long addedByUserId;
}
