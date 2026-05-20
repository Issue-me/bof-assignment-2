package com.bof.banking.repository;

import com.bof.banking.model.User;
import com.bof.banking.model.UserInterestSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link UserInterestSummary}.
 */
@Repository
public interface InterestSummaryRepository extends JpaRepository<UserInterestSummary, Long> {

    // ── Single-user lookups ───────────────────────────────────────────────
    Optional<UserInterestSummary> findByUserAndTaxYear(User user, int taxYear);
    boolean existsByUserAndTaxYear(User user, int taxYear);
    List<UserInterestSummary> findByUserOrderByTaxYearDesc(User user);

    // ── Year-level bulk queries ───────────────────────────────────────────
    List<UserInterestSummary> findByTaxYear(int taxYear);
    List<UserInterestSummary> findByTaxYearAndSubmittedToFrcsFalse(int taxYear);

    // ── Withholding-type queries ──────────────────────────────────────────
    @Query("SELECT s FROM UserInterestSummary s WHERE s.taxYear = :year AND s.nrwhtWithheld > 0 ORDER BY s.user.id")
    List<UserInterestSummary> findNrwhtSummariesForYear(@Param("year") int year);

    @Query("SELECT s FROM UserInterestSummary s WHERE s.taxYear = :year AND s.riwtWithheld > 0 ORDER BY s.user.id")
    List<UserInterestSummary> findRiwtSummariesForYear(@Param("year") int year);

    @Query("SELECT s FROM UserInterestSummary s WHERE s.taxYear = :year AND s.exemptionReason IS NOT NULL ORDER BY s.user.id")
    List<UserInterestSummary> findExemptSummariesForYear(@Param("year") int year);

    // ── Aggregate totals projection ───────────────────────────────────────
    @Query("""
        SELECT
          COALESCE(SUM(s.grossInterestEarned), 0) AS totalGross,
          COALESCE(SUM(s.nrwhtWithheld),       0) AS totalNrwht,
          COALESCE(SUM(s.riwtWithheld),         0) AS totalRiwt
        FROM UserInterestSummary s WHERE s.taxYear = :year
        """)
    YearTotals aggregateTotalsForYear(@Param("year") int year);

    interface YearTotals {
        BigDecimal getTotalGross();
        BigDecimal getTotalNrwht();
        BigDecimal getTotalRiwt();
    }
}