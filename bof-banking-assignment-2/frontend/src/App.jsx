import React from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider } from "./context/AuthContext";
import ProtectedRoute from "./components/ProtectedRoute";

import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import DashboardPage from "./pages/DashboardPage";
import AccountsPage from "./pages/AccountsPage";
import RoleBasedRedirect from "./components/RoleBasedRedirect";
import ManageAccountHoldersPage from "./pages/ManageAccountHoldersPage";
import ManageAccountHoldersHubPage from "./pages/ManageAccountHoldersHubPage";
import ManageAccountsPage from "./pages/ManageAccountsPage";
import UserManagementPage from "./pages/UserManagementPage";
import AccessDeniedPage from "./pages/AccessDeniedPage";
import TransferPage from "./pages/TransferPage";
import TransferLimitsPage from "./pages/TransferLimitsPage";
import TransactionHistoryPage from "./pages/TransactionHistoryPage";
import TransactionMonitoringPage from "./pages/TransactionMonitoringPage";
import BillerManagementPage from './pages/BillerManagementPage';
import BillPaymentMonitoringPage from './pages/BillPaymentMonitoringPage';
import PayBillPage from './pages/PayBillPage';
import ScheduleBillPaymentPage from './pages/ScheduleBillPaymentPage';
import ScheduledBillsPage from './pages/ScheduledBillsPage';
import EditScheduledBillPaymentPage from './pages/EditScheduledBillPaymentPage';
import BillPaymentHistoryPage from './pages/BillPaymentHistoryPage';
import TaxReportPage from './pages/TaxReportPage';
import FrcsReportPage from "./pages/FrcsReportPage";
import StatementsPage from './pages/StatementsPage';
import AdminRiwtExemptionPage from "./pages/AdminRiwtExemptionPage";
import ManageInterestRatePage from "./pages/ManageInterestRate";
import LoanApplicationPage from './pages/LoanApplicationPage';
import LoansPage from './pages/LoansPage';
import AdminLoanPage from './pages/LoanManagement';
import ManageLoanRate from './pages/ManageLoanRates';
import LoanAdvertPage from "./pages/LoanAdvertPage";
import FrcsReportSubmission from "./pages/FrcsReportSubmission";

import Layout from "./layout/Layout";
import "./App.css";

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          {/* Public */}
          <Route path="/login"         element={<LoginPage />} />
          <Route path="/register"      element={<RegisterPage />} />
          <Route path="/access-denied" element={<AccessDeniedPage />} />

          {/* Protected routes with layout */}
          <Route
            element={
              <ProtectedRoute>
                <Layout />
              </ProtectedRoute>
            }
          >
            {/* Dashboard - CUSTOMER and TELLER only (ADMIN redirects to manage-accounts) */}
            <Route
              path="/dashboard"
              element={
                <ProtectedRoute allowedRoles={["CUSTOMER", "TELLER", "ADMIN"]}>
                  <DashboardPage />
                </ProtectedRoute>
              }
            />
            
            {/* CUSTOMER-only routes: Personal accounts and financial transactions */}
            <Route
              path="/accounts"
              element={
                <ProtectedRoute allowedRoles={["CUSTOMER"]}>
                  <AccountsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/transfer"
              element={
                <ProtectedRoute allowedRoles={["CUSTOMER"]}>
                  <TransferPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/transfer-limits"
              element={
                <ProtectedRoute allowedRoles={["CUSTOMER"]}>
                  <TransferLimitsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/loan-adverstisements"
              element={
                <ProtectedRoute allowedRoles={["CUSTOMER"]}>
                  <LoanAdvertPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/transfer-limit"
              element={<Navigate to="/transfer-limits" replace />}
            />
            <Route
              path="/transaction_history"
              element={
                <ProtectedRoute allowedRoles={["CUSTOMER"]}>
                  <TransactionHistoryPage />
                </ProtectedRoute>
              }
            />
            
            {/* Bill Payment routes - CUSTOMER only */}
            <Route
              path="/bill-payments/pay-now"
              element={
                <ProtectedRoute allowedRoles={["CUSTOMER"]}>
                  <PayBillPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/bill-payments/schedule"
              element={
                <ProtectedRoute allowedRoles={["CUSTOMER"]}>
                  <ScheduleBillPaymentPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/bill-payments/scheduled"
              element={
                <ProtectedRoute allowedRoles={["CUSTOMER"]}>
                  <ScheduledBillsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/bill-payments/scheduled/:id/edit"
              element={
                <ProtectedRoute allowedRoles={["CUSTOMER"]}>
                  <EditScheduledBillPaymentPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/bill-payments/history"
              element={
                <ProtectedRoute allowedRoles={["CUSTOMER"]}>
                  <BillPaymentHistoryPage />
                </ProtectedRoute>
              }
            />

            {/* Legacy bill payment aliases */}
            <Route path="/pay_bills"              element={<Navigate to="/bill-payments/pay-now" replace />} />
            <Route path="/schedule_bill_payment"  element={<Navigate to="/bill-payments/schedule" replace />} />
            <Route path="/scheduled_bills"        element={<Navigate to="/bill-payments/scheduled" replace />} />
            <Route path="/scheduled_bills/:id/edit" element={
              <ProtectedRoute allowedRoles={["CUSTOMER"]}><EditScheduledBillPaymentPage /></ProtectedRoute>
            } />
            <Route path="/bill_payment_history" element={<Navigate to="/bill-payments/history" replace />} />

            {/* Bank Statements - CUSTOMER only */}
            <Route
              path="/statements"
              element={
                <ProtectedRoute allowedRoles={["CUSTOMER"]}>
                  <StatementsPage />
                </ProtectedRoute>
              }
            />

            <Route
              path="/loans"
              element={
                <ProtectedRoute allowedRoles={["CUSTOMER"]}>
                  <LoansPage />
                </ProtectedRoute>
              }
            />

            <Route 
            path="/loan-application"
            element={
              <ProtectedRoute allowedRoles={["CUSTOMER"]}>
                <LoanApplicationPage />
              </ProtectedRoute>
            }
            />

            <Route
              path="/loan-advertisements"
              element={
                <ProtectedRoute allowedRoles={["CUSTOMER"]}>
                  <LoanAdvertPage />
                </ProtectedRoute>
              }
            />

            {/* TELLER/ADMIN routes: Account administration */}
            <Route
              path="/manage-accounts"
              element={
                <ProtectedRoute allowedRoles={["TELLER", "ADMIN"]}>
                  <ManageAccountsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/manage-account-holders"
              element={
                <ProtectedRoute allowedRoles={["TELLER", "ADMIN"]}>
                  <ManageAccountHoldersHubPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/accounts/:accountId/holders"
              element={
                <ProtectedRoute allowedRoles={["TELLER", "ADMIN"]}>
                  <ManageAccountHoldersPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/teller/transaction-monitoring"
              element={
                <ProtectedRoute allowedRoles={["TELLER"]}>
                  <TransactionMonitoringPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/teller/billers"
              element={
                <ProtectedRoute allowedRoles={["TELLER"]}>
                  <BillerManagementPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/teller/bill-payments/monitoring"
              element={
                <ProtectedRoute allowedRoles={["TELLER"]}>
                  <BillPaymentMonitoringPage />
                </ProtectedRoute>
              }
            />
            
            {/* Tax report - CUSTOMER only (own data) */}
            <Route
              path="/tax_report"
              element={
                <ProtectedRoute allowedRoles={["CUSTOMER"]}>
                  <TaxReportPage />
                </ProtectedRoute>
              }
            />
            <Route path="/tax-report" element={<Navigate to="/tax_report" replace />} />

            {/* FRCS report — ADMIN and TELLER */}
            <Route path="/frcs_report" element={
              <ProtectedRoute allowedRoles={["ADMIN", "TELLER"]}><FrcsReportPage /></ProtectedRoute>
            } />
            <Route path="/teller/tax-report" element={
              <ProtectedRoute allowedRoles={["ADMIN", "TELLER"]}><FrcsReportPage /></ProtectedRoute>
            } />
            {/* FIX: both URL variants point to frcs_report */}
            <Route path="/admin/frcs-report" element={<Navigate to="/frcs_report" replace />} />

            {/* Manage Interest Rates — TELLER and ADMIN */}
            <Route path="/manage-interest-rates" element={
              <ProtectedRoute allowedRoles={["TELLER", "ADMIN"]}>
                <ManageInterestRatePage />
              </ProtectedRoute>
            } />

            {/* TELLER / ADMIN — account operations */}
            <Route path="/manage-accounts" element={
              <ProtectedRoute allowedRoles={["TELLER", "ADMIN"]}><ManageAccountsPage /></ProtectedRoute>
            } />
            <Route path="/manage-account-holders" element={
              <ProtectedRoute allowedRoles={["TELLER", "ADMIN"]}><ManageAccountHoldersHubPage /></ProtectedRoute>
            } />
            <Route path="/accounts/:accountId/holders" element={
              <ProtectedRoute allowedRoles={["TELLER", "ADMIN"]}><ManageAccountHoldersPage /></ProtectedRoute>
            } />
            <Route path="/teller/transaction-monitoring" element={
              <ProtectedRoute allowedRoles={["TELLER"]}><TransactionMonitoringPage /></ProtectedRoute>
            } />
            <Route path="/teller/billers" element={
              <ProtectedRoute allowedRoles={["TELLER"]}><BillerManagementPage /></ProtectedRoute>
            } />
            <Route path="/teller/bill-payments/monitoring" element={
              <ProtectedRoute allowedRoles={["TELLER"]}><BillPaymentMonitoringPage /></ProtectedRoute>
            } />

            {/* RIWT exemptions — ADMIN only */}
            <Route path="/manage-RIWT-exemptions" element={
              <ProtectedRoute allowedRoles={["ADMIN"]}><AdminRiwtExemptionPage /></ProtectedRoute>
            } />

            {/* FRCS report submission - ADMIN only */}
            <Route path="/admin/frcs-report-submission" element={
              <ProtectedRoute allowedRoles={["ADMIN"]}>
                <FrcsReportSubmission />
              </ProtectedRoute>
            } />

            {/* User management — ADMIN only */}
            <Route path="/user-management" element={
              <ProtectedRoute allowedRoles={["ADMIN"]}><UserManagementPage /></ProtectedRoute>
            } />

            {/* Manage RIWT Exemptions - ADMIN only */}
            <Route path="/manage-riwt-exemptions" element={
              <ProtectedRoute allowedRoles={["ADMIN"]}>
                <AdminRiwtExemptionPage />
              </ProtectedRoute>
            } />

            <Route path="/teller/loan-approvals" element={
              <ProtectedRoute allowedRoles={["TELLER"]}>
                <AdminLoanPage />
              </ProtectedRoute>
            } />

            {/* Legacy alias: keep old route working but move to teller panel */}
            <Route path="/admin/loans" element={<Navigate to="/teller/loan-approvals" replace />} />

            <Route
              path="/manage-loan-rates" element={
                <ProtectedRoute allowedRoles={["TELLER"]}>
                  <ManageLoanRate />
                </ProtectedRoute>
              } />

          </Route>

          {/* Default */}
          <Route path="/"  element={<RoleBasedRedirect />} />
          <Route path="*"  element={<RoleBasedRedirect />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;