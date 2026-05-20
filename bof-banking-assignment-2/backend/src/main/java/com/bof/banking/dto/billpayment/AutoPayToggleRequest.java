package com.bof.banking.dto.billpayment;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Core type for auto pay toggle req.
 */
public class AutoPayToggleRequest {

    @NotNull(message = "enabled is required")
    private Boolean enabled;

    private Boolean payPendingBills;
}
