package com.bof.banking.dto.account;

import com.bof.banking.model.enums.AccountHolderRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * DTO for adding an account holder to an account.
 */
@Data
public class AddAccountHolderRequest {
    
    @NotBlank(message = "User email is required")
    @Email(message = "Invalid email format")
    private String userEmail;
    
    @NotNull(message = "Account holder role is required")
    private AccountHolderRole role;
}
