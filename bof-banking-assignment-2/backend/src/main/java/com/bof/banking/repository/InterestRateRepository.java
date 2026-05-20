package com.bof.banking.repository;

import com.bof.banking.model.InterestRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
/**
 * Prevents two rates being set for the same effective date.
 */
public interface InterestRateRepository extends JpaRepository<InterestRate, Long> {

    /**
     * All rates ordered newest-first — used for the history table.
     */
    List<InterestRate> findAllByOrderByEffectiveFromDesc();

    /**
     * The most recent rate whose effectiveFrom is on or before the given date.
     *
     * Use:
     *   findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(LocalDate.now())
     *   → current active rate
     *
     *   findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(lastDayOfMonth)
     *   → rate in effect for a specific billing period
     *
     * Spring Data translates findFirst + OrderBy into SQL LIMIT 1 automatically.
     * Do NOT use JPQL "LIMIT 1" — it is SQL syntax and not valid in JPQL.
     */
    Optional<InterestRate> findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            LocalDate date);

    /**
     * Prevents two rates being set for the same effective date.
     */
    boolean existsByEffectiveFrom(LocalDate effectiveFrom);
}
