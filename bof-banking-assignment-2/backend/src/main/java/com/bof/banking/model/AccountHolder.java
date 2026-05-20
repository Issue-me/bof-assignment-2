package com.bof.banking.model;

import com.bof.banking.model.enums.AccountHolderRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents the relationship between an account and its holders.
 * Supports joint accounts and multiple account holders.
 */
@Entity
@Table(name = "account_holders", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"account_id", "user_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Represents the relationship between an account and its holders. Supports joint accounts and multiple account holders.
 */
public class AccountHolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private AccountHolderRole role;

    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;

    @Column(name = "added_by_user_id")
    private Long addedByUserId;

    @PrePersist
    /**
     * Hooks into lifecycle processing for AccountHolder to keep entity state consistent.
     */
    protected void onCreate() {
        addedAt = LocalDateTime.now();
    }
}
