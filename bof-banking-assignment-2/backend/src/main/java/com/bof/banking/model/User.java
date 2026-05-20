package com.bof.banking.model;

import com.bof.banking.model.Role;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a bank customer / system user.
 *
 * <p><b>Tax fields added for FRCS reporting:</b>
 * <ul>
 *   <li>{@code tinNumber}       – Tax Identification Number. Null = not provided → NRWHT applies.</li>
 *   <li>{@code isResident}      – Fiji tax resident. Non-residents attract NRWHT on interest.</li>
 *   <li>{@code isSeniorCitizen} – Overrides all withholding; exempt from NRWHT and RIWT.</li>
 *   <li>{@code dateOfBirth}     – Optional; used to auto-derive senior-citizen status (age >= 60).</li>
 * </ul>
 *
 * <p>{@code @ToString} and {@code @EqualsAndHashCode} exclude {@code accounts} and
 * {@code interestSummaries} to prevent Hibernate lazy-load StackOverflowErrors.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"accounts", "interestSummaries"})
@EqualsAndHashCode(of = "id")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", unique = true, nullable = false)
    private String customerId; // e.g. BOF-000001

    // ── Identity ──────────────────────────────────────────────────────────
    @NotBlank
    @Column(name = "first_name", nullable = false)
    private String firstName;

    @NotBlank
    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Email
    @Column(unique = true, nullable = false)
    private String email;

    @NotBlank
    @Column(nullable = false)
    private String password; // BCrypt hashed

    @Column(name = "phone_number")
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    // ── Tax / residency ───────────────────────────────────────────────────
    /**
     * Tax Identification Number assigned by FRCS.
     * Null or blank → NRWHT (10%) withheld on interest, unless senior citizen.
     */
    @Column(name = "tin_number")
    private String tinNumber; // Tax Identification Number

    @Column(name = "national_id")
    private String nationalId; // National ID or Passport Number

    @Column(name = "address")
    private String address;

    /**
     * Fiji tax resident flag.
     * Non-residents attract NRWHT (10%) on gross interest regardless of TIN,
     * unless they are a senior citizen.
     */
    @Column(name = "is_resident", nullable = false)
    @Builder.Default
    private boolean isResident = true;

    /**
     * Senior citizens (age >= 60) are fully exempt from all interest
     * withholding tax (NRWHT and RIWT) under the Fiji Income Tax Act.
     */
    @Column(name = "is_senior_citizen", nullable = false, columnDefinition = "boolean DEFAULT false")
    @Builder.Default
    private boolean isSeniorCitizen = false;

    /**
     * Optional date of birth. Used in {@link #onCreate()} to auto-derive
     * {@code seniorCitizen} status when not explicitly set.
     */
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    // ── Relationships ─────────────────────────────────────────────────────
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Account> accounts = new ArrayList<>();

    /**
     * Persisted interest summaries for this user, one per tax year.
     * Populated by {@link com.bof.banking.service.impl.InterestSummaryCalculator}.
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<UserInterestSummary> interestSummaries = new ArrayList<>();

    // ── Audit ─────────────────────────────────────────────────────────────
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @PrePersist
    /**
     * Hooks into lifecycle processing for User to keep entity state consistent.
     */
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (dateOfBirth != null && !isSeniorCitizen) {
            isSeniorCitizen = LocalDate.now().getYear() - dateOfBirth.getYear() >= 60;
        }
    }

    // ── Convenience helpers ───────────────────────────────────────────────
    /**
     * Returns full name data.
     * @return the resulting text value.
     */
    public String getFullName() {
        return firstName + " " + lastName;
    }

    /** True when NRWHT should be withheld (non-resident or no TIN, not senior citizen). */
    /**
     * Checks whether subject to nrwht is valid.
     * @return true if the condition is met; otherwise false.
     */
    public boolean isSubjectToNrwht() {
        return !isSeniorCitizen && (!isResident || !hasTin());
    }

    /** True when RIWT should be withheld (resident with TIN, not senior citizen). */
    /**
     * Checks whether subject to riwt is valid.
     * @return true if the condition is met; otherwise false.
     */
    public boolean isSubjectToRiwt() {
        return !isSeniorCitizen && isResident && hasTin();
    }

    /** True when the user has a non-blank TIN registered. */
    /**
     * Checks whether tin is valid.
     * @return true if the condition is met; otherwise false.
     */
    public boolean hasTin() {
        return tinNumber != null && !tinNumber.isBlank();
    }
}
