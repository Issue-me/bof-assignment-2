package com.bof.banking.dto.user;

import com.bof.banking.dto.account.AccountResponseDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for detailed customer profile view including linked accounts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDetailResponse {

    private Long id;
    private String customerId;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String role;
    private boolean active;
    private String tinNumber;
    private String nationalId;
    private LocalDate dateOfBirth;
    private String address;
    private boolean resident;
    private LocalDateTime createdAt;
    private LocalDateTime lastLogin;
    
    // Linked bank accounts
    private List<AccountResponseDto> accounts;
    private int totalAccounts;
}
