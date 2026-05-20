import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import billPaymentService from '../services/billPaymentService';
import Toast from '../components/Toast';
import './BillPaymentForms.css';

/**
 * BillPaymentHistoryPage — displays all past bill payments for the user
 */
const BillPaymentHistoryPage = () => {
  useAuth();
  const navigate = useNavigate();

  const [payments, setPayments] = useState([]);
  const [recentInvoices, setRecentInvoices] = useState([]);
  const [invoiceLoading, setInvoiceLoading] = useState(true);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [toast, setToast] = useState(null);
  const [filter, setFilter] = useState('ALL'); // ALL, COMPLETED, PENDING, FAILED, CANCELLED

  useEffect(() => {
    let isMounted = true;

    const currentMonthIndex = (() => {
      const now = new Date();
      return now.getFullYear() * 12 + now.getMonth();
    })();

    const isWithinPastThreeMonths = (invoiceMonth, invoiceYear) => {
      if (!invoiceMonth || !invoiceYear) return false;
      const invoiceMonthIndex = (invoiceYear * 12) + (invoiceMonth - 1);
      const diff = currentMonthIndex - invoiceMonthIndex;
      return diff >= 0 && diff <= 2;
    };

    const toNumber = (value, fallback = 0) => {
      const numeric = Number(value);
      return Number.isFinite(numeric) ? numeric : fallback;
    };

    const loadPayments = async () => {
      setInvoiceLoading(true);
      try {
        const [paymentsResponse, schedulesResponse] = await Promise.all([
          billPaymentService.getMyBillPayments(),
          billPaymentService.getScheduledBillPayments(),
        ]);

        const paymentsData = paymentsResponse.data || paymentsResponse || [];
        const schedules = schedulesResponse.data || [];

        const invoiceResults = await Promise.allSettled(
          schedules.map(async (setup) => {
            const invoiceResponse = await billPaymentService.getScheduledInvoices(setup.id);
            const invoices = invoiceResponse?.data || [];
            return invoices.map((invoice) => ({
              ...invoice,
              billerName: setup.billerName,
              billReference: setup.billReference,
            }));
          })
        );

        const mergedInvoices = invoiceResults
          .filter((result) => result.status === 'fulfilled')
          .flatMap((result) => result.value)
          .filter((invoice) => isWithinPastThreeMonths(invoice.invoiceMonth, invoice.invoiceYear))
          .sort((a, b) => {
            if (toNumber(a.invoiceYear) !== toNumber(b.invoiceYear)) {
              return toNumber(b.invoiceYear) - toNumber(a.invoiceYear);
            }
            return toNumber(b.invoiceMonth) - toNumber(a.invoiceMonth);
          });

        if (isMounted) {
          // Ensure it's an array
          setPayments(Array.isArray(paymentsData) ? paymentsData : []);
          setRecentInvoices(mergedInvoices);
        }
      } catch (err) {
        if (isMounted) {
          setError(err.response?.data?.message || 'Failed to load payment history.');
          setToast({
            type: 'error',
            message: 'Failed to load payment history.'
          });
          setRecentInvoices([]);
        }
      } finally {
        if (isMounted) {
          setLoading(false);
          setInvoiceLoading(false);
        }
      }
    };

    loadPayments();
    return () => {
      isMounted = false;
    };
  }, []);

  const getFilteredPayments = () => {
    const safePayments = Array.isArray(payments) ? payments : [];
    if (filter === 'ALL') return safePayments;
    return safePayments.filter((p) => p.status === filter);
  };

  const getStatusBadge = (status) => {
    const statusClasses = {
      COMPLETED: 'badge badge-success',
      PENDING: 'badge badge-warning',
      FAILED: 'badge badge-danger',
      CANCELLED: 'badge badge-secondary',
    };
    return <span className={statusClasses[status] || 'badge'}>{status}</span>;
  };

  const formatDate = (date) => {
    if (!date) return '-';
    return new Date(date).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  const filteredPayments = getFilteredPayments();
  const totalAmount = filteredPayments.reduce((sum, p) => sum + (p.amount || 0), 0);

  if (loading) {
    return (
      <div className="dashboard-wrapper">
        <nav className="dashboard-nav">
          <div className="nav-brand">
            <svg className="logo-icon-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m3 10 9-7 9 7"/><path d="M5 10v10h14V10"/><path d="M9 20v-5h6v5"/></svg>
            <span>Bill Payment History</span>
          </div>
          <button onClick={() => navigate('/dashboard')} className="btn btn-back-outline btn-sm">
            Back
          </button>
        </nav>
        <main className="dashboard-main">
          <p>Loading...</p>
        </main>
      </div>
    );
  }

  return (
    <div className="dashboard-wrapper">
      <nav className="dashboard-nav">
        <div className="nav-brand">
          <svg className="logo-icon-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><rect x="2" y="5" width="20" height="14" rx="2"/><path d="M2 10h20"/></svg>
          <span>Bill Payment History</span>
        </div>
        <button onClick={() => navigate('/dashboard')} className="btn btn-back-outline btn-sm">
          Back to Dashboard
        </button>
      </nav>

      <main className="dashboard-main">
        <div className="card">
          <h2>Payment History</h2>
          <p className="subtitle">View all your past bill payments</p>

          {error && <div className="error-message">{error}</div>}

          {payments.length === 0 ? (
            <div className="empty-state">
              <div className="empty-state-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="5" width="18" height="14" rx="2"/><path d="m3 8 9 6 9-6"/></svg>
              </div>
              <p>No bill payments yet.</p>
              <button
                onClick={() => navigate('/bill-payments/schedule')}
                className="btn btn-primary"
              >
                Pay a Bill Now
              </button>
            </div>
          ) : (
            <>
              <div className="summary-box history-summary-box" style={{ marginBlockEnd: '12px' }}>
                <div>
                  <p className="summary-label">Past 3 Months Invoices</p>
                  <p className="summary-value">{recentInvoices.length}</p>
                </div>
                <div>
                  <p className="summary-label">Invoice Amount Total</p>
                  <p className="summary-value">
                    FJD ${recentInvoices.reduce((sum, inv) => sum + (inv.invoiceAmount || 0), 0).toFixed(2)}
                  </p>
                </div>
              </div>

              {invoiceLoading ? (
                <p>Loading past 3 months invoices...</p>
              ) : recentInvoices.length === 0 ? (
                <div className="empty-state" style={{ marginBlockEnd: '14px' }}>
                  <p>No invoices found for the past 3 months.</p>
                </div>
              ) : (
                <div className="history-table-wrap" style={{ marginBlockEnd: '14px' }}>
                  <table className="history-table">
                    <thead>
                      <tr>
                        <th>Invoice Month</th>
                        <th>Provider</th>
                        <th>Reference</th>
                        <th className="ta-right">Invoice Amount</th>
                        <th className="ta-center">Status</th>
                        <th>Payment Ref</th>
                      </tr>
                    </thead>
                    <tbody>
                      {recentInvoices.map((invoice) => (
                        <tr key={`invoice-${invoice.id}`}>
                          <td>{`${invoice.invoiceMonth}/${invoice.invoiceYear}`}</td>
                          <td>{invoice.billerName || '-'}</td>
                          <td>{invoice.billReference || '-'}</td>
                          <td className="ta-right fw-bold">FJD ${invoice.invoiceAmount?.toFixed(2) || '0.00'}</td>
                          <td className="ta-center">{getStatusBadge(invoice.status)}</td>
                          <td>{invoice.paymentReference || '-'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}

              {/* Filter Buttons */}
              <div className="filter-buttons history-filter-buttons">
                <button
                  className={`btn ${filter === 'ALL' ? 'btn-primary' : 'btn-back-outline'}`}
                  onClick={() => setFilter('ALL')}
                >
                  All ({payments.length})
                </button>
                <button
                  className={`btn ${filter === 'COMPLETED' ? 'btn-primary' : 'btn-back-outline'}`}
                  onClick={() => setFilter('COMPLETED')}
                >
                  Completed ({payments.filter((p) => p.status === 'COMPLETED').length})
                </button>
                <button
                  className={`btn ${filter === 'PENDING' ? 'btn-primary' : 'btn-back-outline'}`}
                  onClick={() => setFilter('PENDING')}
                >
                  Pending ({payments.filter((p) => p.status === 'PENDING').length})
                </button>
                <button
                  className={`btn ${filter === 'FAILED' ? 'btn-primary' : 'btn-back-outline'}`}
                  onClick={() => setFilter('FAILED')}
                >
                  Failed ({payments.filter((p) => p.status === 'FAILED').length})
                </button>
                <button
                  className={`btn ${filter === 'CANCELLED' ? 'btn-primary' : 'btn-back-outline'}`}
                  onClick={() => setFilter('CANCELLED')}
                >
                  Cancelled ({payments.filter((p) => p.status === 'CANCELLED').length})
                </button>
              </div>

              {/* Summary */}
              <div className="summary-box history-summary-box">
                <div>
                  <p className="summary-label">Total {filter === 'ALL' ? 'Payments' : filter} Amount</p>
                  <p className="summary-value">
                    FJD ${totalAmount.toFixed(2)}
                  </p>
                </div>
                <div>
                  <p className="summary-label">Transactions</p>
                  <p className="summary-value">
                    {filteredPayments.length}
                  </p>
                </div>
              </div>

              {filteredPayments.length === 0 ? (
                <div className="empty-state">
                  <p>No {filter.toLowerCase()} payments found.</p>
                </div>
              ) : (
                /* Payments Table */
                <div className="history-table-wrap">
                  <table className="history-table">
                    <thead>
                      <tr>
                        <th>Reference #</th>
                        <th>Bill Provider</th>
                        <th>Account #</th>
                        <th className="ta-right">Amount</th>
                        <th className="ta-center">Status</th>
                        <th>Processed Date</th>
                        <th>Description</th>
                      </tr>
                    </thead>
                    <tbody>
                      {filteredPayments.map((payment) => (
                        <tr key={payment.id} className={payment.status === 'COMPLETED' ? 'is-completed' : ''}>
                          <td className="fw-bold">
                            {payment.paymentReference}
                          </td>
                          <td>
                            {payment.biller?.billerName || '-'}
                          </td>
                          <td className="text-small">
                            {payment.accountNumber}
                          </td>
                          <td className="ta-right fw-bold">
                            FJD ${payment.amount?.toFixed(2) || '0.00'}
                          </td>
                          <td className="ta-center">
                            {getStatusBadge(payment.status)}
                          </td>
                          <td className="text-small">
                            {formatDate(payment.processedDate || payment.createdAt)}
                          </td>
                          <td className="text-small muted">
                            {payment.description || '-'}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </>
          )}
        </div>
      </main>

      {toast && (
        <Toast
          message={toast.message}
          type={toast.type}
          duration={toast.duration || 5000}
          onClose={() => setToast(null)}
        />
      )}
    </div>
  );
};

export default BillPaymentHistoryPage;
