package com.bof.banking.repository;

import com.bof.banking.model.Role;
import com.bof.banking.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.List;
import java.util.Optional;

/**
 * JPA repository for the {@link User} entity.
 *
 * <p><b>Important:</b> All boolean queries use {@code @Query} with explicit JPQL
 * rather than derived method names. This avoids Spring Data misreading the {@code is}
 * prefix on boolean fields (e.g. {@code isActive}, {@code isResident}) as part of
 * a property path vs. a Java bean accessor prefix, which causes startup failures.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // ── Identity lookup ───────────────────────────────────────────────────

    Optional<User> findByEmail(String email);

    Optional<User> findByCustomerId(String customerId);

    boolean existsByEmail(String email);

    List<User> findByRole(Role role);

    // ── General active-user query ─────────────────────────────────────────

    @Query("SELECT u FROM User u WHERE u.isActive = true")
    List<User> findAllActive();

    // ── Tax / withholding queries ─────────────────────────────────────────

    /**
     * All active users subject to NRWHT:
     * non-resident OR no TIN, AND NOT a senior citizen.
     */
    @Query("""
        SELECT u FROM User u
        WHERE u.isActive = true
          AND u.isSeniorCitizen = false
          AND (u.isResident = false
               OR u.tinNumber IS NULL
               OR u.tinNumber = '')
        """)
    List<User> findUsersSubjectToNrwht();

    /**
     * All active resident users with a TIN — subject to RIWT.
     */
    @Query("""
        SELECT u FROM User u
        WHERE u.isActive = true
          AND u.isSeniorCitizen = false
          AND u.isResident = true
          AND u.tinNumber IS NOT NULL
          AND u.tinNumber <> ''
        """)
    List<User> findUsersSubjectToRiwt();

    /**
     * All active senior citizen users — exempt from interest withholding.
     */
    @Query("""
        SELECT u FROM User u
        WHERE u.isActive = true
          AND u.isSeniorCitizen = true
        """)
    List<User> findActiveSeniorCitizens();

    /**
     * All active users without a TIN registered — used for compliance outreach.
     */
    @Query("""
        SELECT u FROM User u
        WHERE u.isActive = true
          AND (u.tinNumber IS NULL OR u.tinNumber = '')
        """)
    List<User> findUsersWithoutTin();
}