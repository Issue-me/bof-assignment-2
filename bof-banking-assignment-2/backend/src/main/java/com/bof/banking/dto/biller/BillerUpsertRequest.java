package com.bof.banking.dto.biller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating and updating billers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillerUpsertRequest {

    @NotBlank(message = "Biller name is required")
    @Size(max = 100, message = "Biller name must be 100 characters or fewer")
    private String billerName;

    @NotBlank(message = "Biller code is required")
    @Size(max = 50, message = "Biller code must be 50 characters or fewer")
    private String billerCode;

    @NotBlank(message = "Category is required")
    @Size(max = 50, message = "Category must be 50 characters or fewer")
    private String category;

    @NotBlank(message = "Settlement account number is required")
    @Size(max = 20, message = "Settlement account number must be 20 characters or fewer")
    private String settlementAccountNumber;
}
