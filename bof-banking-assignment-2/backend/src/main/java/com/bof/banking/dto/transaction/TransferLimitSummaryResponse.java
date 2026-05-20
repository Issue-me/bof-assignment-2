package com.bof.banking.dto.transaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Transfer limit summary response for the authenticated user.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferLimitSummaryResponse {

    private String timezone;
    private LocalDateTime generatedAt;
    private List<TransferCategoryLimitUsageResponse> categories;
}
