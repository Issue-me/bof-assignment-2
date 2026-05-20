package com.bof.banking.repository;

import com.bof.banking.model.RiwtExemption;
import com.bof.banking.model.User;
import com.bof.banking.model.enums.RiwtExemptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
/**
 * Check whether an approved exemption already exists
 */
public interface RiwtExemptionRepository extends JpaRepository<RiwtExemption, Long> {

    /** All submissions, newest first — used by admin list */
    List<RiwtExemption> findAllByOrderBySubmittedAtDesc();

    /** Filter by status */
    List<RiwtExemption> findByStatusOrderBySubmittedAtDesc(RiwtExemptionStatus status);

    /** Find an existing submission for a specific user + year */
    Optional<RiwtExemption> findByUserAndTaxYear(User user, int taxYear);

    /** Check whether an approved exemption already exists */
    boolean existsByUserAndTaxYearAndStatus(User user, int taxYear, RiwtExemptionStatus status);
}
