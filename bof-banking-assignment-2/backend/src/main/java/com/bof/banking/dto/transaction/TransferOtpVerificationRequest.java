package com.bof.banking.dto.transaction;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for completing a high-value transfer with OTP.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferOtpVerificationRequest {

    @NotBlank(message = "Challenge ID is required")
    private String challengeId;

    @NotBlank(message = "OTP code is required")
    private String otpCode;
}
