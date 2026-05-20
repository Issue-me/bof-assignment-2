package com.bof.banking.dto.transaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response returned when transfer is initiated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferInitiationResponse {

    private boolean otpRequired;
    private String challengeId;
    private String message;
    private LocalDateTime expiresAt;
    private TransactionResponse transaction;
}
