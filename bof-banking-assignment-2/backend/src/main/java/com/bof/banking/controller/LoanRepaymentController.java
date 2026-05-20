// package com.bof.banking.controller;

// import com.bof.banking.dto.loan.LoanRepaymentRequest;
// import com.bof.banking.dto.loan.LoanRepaymentResponse;
// import com.bof.banking.service.LoanRepaymentService;
// import jakarta.validation.Valid;
// import lombok.RequiredArgsConstructor;
// import org.springframework.http.ResponseEntity;
// import org.springframework.security.core.annotation.AuthenticationPrincipal;
// import org.springframework.security.core.userdetails.UserDetails;
// import org.springframework.web.bind.annotation.*;

// import java.util.List;

// /**
//  * REST controller for loan repayment operations.
//  *
//  * <h3>Endpoints</h3>
//  * <ul>
//  *   <li>POST /api/loans/repay              — submit a repayment</li>
//  *   <li>GET  /api/loans/{loanId}/repayments — get repayment history for a loan</li>
//  * </ul>
//  */
// @RestController
// @RequestMapping("/api/loans")
// @RequiredArgsConstructor
// public class LoanRepaymentController {

//     private final LoanRepaymentService repaymentService;

//     /**
//      * Submit a repayment for an active loan.
//      * The amount is debited from the customer's chosen account.
//      */
//     @PostMapping("/repay")
//     public ResponseEntity<LoanRepaymentResponse> repay(
//             @AuthenticationPrincipal UserDetails userDetails,
//             @Valid @RequestBody LoanRepaymentRequest request) {

//         return ResponseEntity.ok(
//             repaymentService.makeRepayment(userDetails.getUsername(), request)
//         );
//     }

//     /**
//      * Get the full repayment history for one of the customer's loans.
//      */
//     @GetMapping("/{loanId}/repayments")
//     public ResponseEntity<List<LoanRepaymentResponse>> getHistory(
//             @AuthenticationPrincipal UserDetails userDetails,
//             @PathVariable Long loanId) {

//         return ResponseEntity.ok(
//             repaymentService.getRepaymentHistory(userDetails.getUsername(), loanId)
//         );
//     }
// }