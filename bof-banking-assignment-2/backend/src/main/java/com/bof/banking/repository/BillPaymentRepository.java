package com.bof.banking.repository;

import com.bof.banking.model.BillPayment;
import com.bof.banking.model.Biller;
import com.bof.banking.model.User;
import com.bof.banking.model.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JPA repository for BillPayment entity.
 */
@Repository
public interface BillPaymentRepository extends JpaRepository<BillPayment, Long>, JpaSpecificationExecutor<BillPayment> {

    Optional<BillPayment> findByPaymentReference(String paymentReference);

    Optional<BillPayment> findByUserAndIdempotencyKey(User user, String idempotencyKey);

    List<BillPayment> findByUserOrderByCreatedAtDesc(User user);

    Page<BillPayment> findByUser(User user, Pageable pageable);

    List<BillPayment> findByUserAndStatus(User user, PaymentStatus status);

    List<BillPayment> findByStatusAndScheduledDateBefore(PaymentStatus status, LocalDateTime dateTime);

    List<BillPayment> findByBiller_BillerCode(String billerCode);

    List<BillPayment> findByUserAndBillerAndAccountNumberOrderByProcessedDateDesc(
            User user,
            Biller biller,
            String accountNumber);

    @Query("""
            SELECT COALESCE(SUM(bp.amount), 0)
            FROM BillPayment bp
            WHERE bp.user.id = :userId
              AND bp.status = :status
              AND bp.processedDate >= :since
            """)
    BigDecimal sumCompletedBillPaymentsSince(
            @Param("userId") Long userId,
            @Param("status") PaymentStatus status,
            @Param("since") LocalDateTime since);
}
