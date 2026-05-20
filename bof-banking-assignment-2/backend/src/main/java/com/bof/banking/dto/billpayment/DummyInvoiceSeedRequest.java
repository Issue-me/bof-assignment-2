package com.bof.banking.dto.billpayment;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Core type for D um my In vo ic eS ee dR eq ue st.
 */
public class DummyInvoiceSeedRequest {

    @NotBlank(message = "Customer reference is required")
    private String customerReference;

    private Integer invoiceYear;
}
