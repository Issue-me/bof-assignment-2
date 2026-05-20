package com.bof.banking.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a biller that can receive customer bill payments.
 */
@Entity
@Table(name = "billers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Biller {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "biller_name", nullable = false)
    private String billerName;

    @Column(name = "biller_code", unique = true, nullable = false)
    private String billerCode;

    @Column(name = "category")
    private String category;

    @Column(name = "settlement_account_number", unique = true)
    private String settlementAccountNumber;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    /**
     * Hooks into lifecycle processing for Biller to keep entity state consistent.
     */
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    /**
     * Hooks into lifecycle processing for Biller to keep entity state consistent.
     */
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
