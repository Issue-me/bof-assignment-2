package com.bof.banking.repository;

import com.bof.banking.model.Biller;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for Biller entity.
 */
@Repository
public interface BillerRepository extends JpaRepository<Biller, Long> {

    Optional<Biller> findByBillerCode(String billerCode);

    Optional<Biller> findByBillerCodeIgnoreCase(String billerCode);

    Optional<Biller> findBySettlementAccountNumber(String settlementAccountNumber);

    Optional<Biller> findByBillerCodeAndIsActiveTrue(String billerCode);

    List<Biller> findByIsActiveTrue();

    List<Biller> findAllByOrderByBillerNameAsc();
}
