package com.bof.banking.model;

import com.bof.banking.model.enums.AccountType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a bank account.
 *
 * CRITICAL — WHY @Data IS REPLACED:
 *
 * Lombok @Data generates equals() and hashCode() from ALL fields including
 * the mutable `balance` field. Hibernate stores managed entities in an
 * IdentityMap keyed by hashCode. When setBalance() is called, the object
 * moves to a different bucket and Hibernate can no longer find it as "dirty"
 * — so it silently skips the UPDATE even after saveAndFlush().
 *
 * This was the root cause of the NRWHT refund not being written to the DB.
 *
 * FIX: Replace @Data with @Getter @Setter @ToString and hand-write
 * equals/hashCode based only on the immutable `id` field.
 */
@Entity
@Table(name = "accounts")
@Getter
@Setter
@ToString(exclude = {"user", "accountHolders"})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_number", unique = true, nullable = false)
    private String accountNumber;

    @Column(name = "account_name")
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    @NotNull
    @DecimalMin(value = "0.00", message = "Balance cannot be negative")
    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal interestRate = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.00", message = "Interest earned cannot be negative")
    @Column(name = "interest_earned", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal interestEarned = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AccountHolder> accountHolders = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    /**
     * Hooks into lifecycle processing for Account to keep entity state consistent.
     */
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    /**
     * Hooks into lifecycle processing for Account to keep entity state consistent.
     */
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ── JPA-safe equals/hashCode — based ONLY on the immutable id ────────────
    //
    // Two Account instances with the same DB id are the same account.
    // Changing balance/interestRate/etc. does NOT change the object's identity.
    // This keeps Hibernate's IdentityMap consistent after any setBalance() call.

    @Override
    /**
     * Handles equals.
     * @param o the o.
     * @return true if the condition is met; otherwise false.
     */
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Account other)) return false;
        if (id == null || other.id == null) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    /**
     * Checks whether h code is valid.
     * @return the result of the operation.
     */
    public int hashCode() {
        return id != null ? Objects.hash(id) : System.identityHashCode(this);
    }
}
