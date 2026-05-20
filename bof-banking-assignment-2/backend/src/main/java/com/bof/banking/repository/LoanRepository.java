// package com.bof.banking.repository;

// import com.bof.banking.model.Loan;
// import com.bof.banking.model.User;
// import com.bof.banking.model.enums.LoanStatus;
// import org.springframework.data.jpa.repository.JpaRepository;
// import org.springframework.stereotype.Repository;

// import java.util.List;

// /**
//  * JPA repository for {@link Loan} entity.
//  */
// @Repository
// public interface LoanRepository extends JpaRepository<Loan, Long> {

//     List<Loan> findByUserOrderByApplicationDateDesc(User user);

//     List<Loan> findAllByOrderByApplicationDateDesc();

//     List<Loan> findByStatusOrderByApplicationDateAsc(LoanStatus status);

//     boolean existsByLoanNumber(String loanNumber);
// }

package com.bof.banking.repository;

import com.bof.banking.model.Loan;
import com.bof.banking.model.User;
import com.bof.banking.model.enums.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
/**
 * FIX: replaces findByLoanTypeAndStatus(String, LoanStatus).
 */
public interface LoanRepository extends JpaRepository<Loan, Long> {

    List<Loan> findByUserOrderByApplicationDateDesc(User user);

    List<Loan> findAllByOrderByApplicationDateDesc();

    List<Loan> findByStatusOrderByApplicationDateAsc(LoanStatus status);

    boolean existsByLoanNumber(String loanNumber);

    /**
     * FIX: replaces findByLoanTypeAndStatus(String, LoanStatus).
     *
     * The original derived query used a lazy-loaded User association.
     * When NotificationServiceImpl iterated the results and called
     * loan.getUser() inside the loop, the Hibernate session was still
     * open for the first loan (because @Transactional wraps the whole
     * method), BUT the groupingBy collector resolved all user IDs up
     * front. The real problem was that loan.getUser() returned a Hibernate
     * proxy — which is fine inside the transaction — but any exception on
     * one user's send() was caught silently, making it look like only one
     * notification was sent.
     *
     * This query uses JOIN FETCH to eagerly load the user in a single SQL
     * query, so loan.getUser() is a fully-initialized object for every
     * loan in the list, and each user's notification is independent.
     */
    @Query("SELECT l FROM Loan l JOIN FETCH l.user WHERE l.loanType = :loanType AND l.status = :status")
    List<Loan> findByLoanTypeAndStatusWithUser(
            @Param("loanType") String loanType,
            @Param("status") LoanStatus status);
}
