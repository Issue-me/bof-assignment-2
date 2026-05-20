package com.bof.banking.repository;

import com.bof.banking.model.ScheduledBillPayment;
import com.bof.banking.model.User;
import com.bof.banking.model.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * JPA repository for ScheduledBillPayment entity.
 */
@Repository
public interface ScheduledBillPaymentRepository extends JpaRepository<ScheduledBillPayment, Long> {

    List<ScheduledBillPayment> findByUserOrderByStartDateAsc(User user);

    Page<ScheduledBillPayment> findByUser(User user, Pageable pageable);

    List<ScheduledBillPayment> findByUserAndStatus(User user, PaymentStatus status);

    @Query("SELECT s FROM ScheduledBillPayment s WHERE s.status = 'ACTIVE' " +
           "AND (s.endDate IS NULL OR s.endDate >= CURRENT_DATE) " +
           "AND s.nextExecutionDate <= CURRENT_DATE")
    List<ScheduledBillPayment> findDueForExecution();

    @Query("SELECT s FROM ScheduledBillPayment s WHERE s.status = 'ACTIVE' " +
           "AND s.autoPayEnabled = true AND s.approvalGiven = true " +
           "AND s.frequency = com.bof.banking.model.enums.ScheduleFrequency.MONTHLY " +
           "AND s.startDate <= :currentDate " +
           "AND (s.endDate IS NULL OR s.endDate >= :currentDate) " +
           "AND s.nextExecutionDate <= :currentDate " +
           "AND (s.lastProcessedYear IS NULL OR s.lastProcessedYear <> :year " +
           "OR s.lastProcessedMonth IS NULL OR s.lastProcessedMonth <> :month)")
    List<ScheduledBillPayment> findDueAutoPayForMonth(
            @Param("currentDate") LocalDate currentDate,
            @Param("month") Integer month,
            @Param("year") Integer year);

    @Query("SELECT s FROM ScheduledBillPayment s WHERE s.user = :user " +
           "AND s.status IN ('ACTIVE', 'PAUSED') ORDER BY s.startDate ASC")
    List<ScheduledBillPayment> findActiveAndPausedByUser(@Param("user") User user);

    Optional<ScheduledBillPayment> findByIdAndUser(Long id, User user);
}
