package com.bof.banking.repository;

import com.bof.banking.model.Investment;
import com.bof.banking.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * JPA repository for Investment entity.
 */
@Repository
public interface InvestmentRepository extends JpaRepository<Investment, Long> {

    Optional<Investment> findByInvestmentNumber(String investmentNumber);

    List<Investment> findByUserOrderByCreatedAtDesc(User user);

    Page<Investment> findByUser(User user, Pageable pageable);

    List<Investment> findByUserAndIsActiveTrue(User user);

    List<Investment> findByIsActiveTrueAndMaturityDateBefore(LocalDate date);

    List<Investment> findByInvestmentType(String investmentType);
}
