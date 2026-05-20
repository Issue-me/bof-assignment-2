import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import billPaymentService from '../services/billPaymentService';
import Toast from '../components/Toast';
import ConfirmDialog from '../components/ConfirmDialog';
import './BillPaymentForms.css';

/**
 * ScheduledBillsPage — displays user's scheduled bill payments.
 */
const ScheduledBillsPage = () => {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [payments, setPayments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');
  const [actionSuccess, setActionSuccess] = useState('');
  const [processingId, setProcessingId] = useState(null);
  const [toast, setToast] = useState(null);
  const [confirmDialog, setConfirmDialog] = useState(null); // custom confirm dialog state
  const [expandedId, setExpandedId] = useState(null);
  const [detailsLoading, setDetailsLoading] = useState(false);
  const [invoiceMap, setInvoiceMap] = useState({});
  const [historyMap, setHistoryMap] = useState({});
  const [payRequestKeyMap, setPayRequestKeyMap] = useState({});
  const [autoEnablePrompt, setAutoEnablePrompt] = useState(null);

  useEffect(() => {
    let isMounted = true;

    billPaymentService
      .getScheduledBillPayments()
      .then((response) => {
        if (isMounted) {
          setPayments(response.data || []);
        }
      })
      .catch((err) => {
        if (isMounted) {
          setError(err.response?.data?.message || 'Failed to load scheduled payments.');
        }
      })
      .finally(() => {
        if (isMounted) setLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, []);

  const handleCancel = (id) => {
    setConfirmDialog({
      id,
      message: 'Are you sure you want to cancel this scheduled payment? This action cannot be undone.',
      title: 'Cancel Scheduled Payment',
    });
  };

  const handleCancelConfirmed = async (id) => {
    setConfirmDialog(null);
    setProcessingId(id);
    setActionError('');
    setActionSuccess('');
    setToast(null);

    try {
      await billPaymentService.cancelScheduledBillPayment(id);
      setToast({
        type: 'success',
        message: 'Scheduled payment cancelled successfully.'
      });
      setActionSuccess('Scheduled payment cancelled successfully!');
      setPayments((prev) =>
        prev.map((p) => (p.id === id ? { ...p, status: 'CANCELLED' } : p))
      );
    } catch (err) {
      const errorMsg = err.response?.data?.message || 'Failed to cancel scheduled payment.';
      setToast({
        type: 'error',
        message: errorMsg
      });
      setActionError(errorMsg);
    } finally {
      setProcessingId(null);
    }
  };

  const handlePause = async (id) => {
    setProcessingId(id);
    setActionError('');
    setActionSuccess('');
    setToast(null);

    try {
      await billPaymentService.pauseScheduledBillPayment(id);
      setToast({
        type: 'success',
        message: 'Scheduled payment paused.'
      });
      setActionSuccess('Scheduled payment paused!');
      setPayments((prev) =>
        prev.map((p) => (p.id === id ? { ...p, status: 'PAUSED' } : p))
      );
    } catch (err) {
      const errorMsg = err.response?.data?.message || 'Failed to pause scheduled payment.';
      setToast({
        type: 'error',
        message: errorMsg
      });
      setActionError(errorMsg);
    } finally {
      setProcessingId(null);
    }
  };

  const handleResume = async (id) => {
    setProcessingId(id);
    setActionError('');
    setActionSuccess('');
    setToast(null);

    try {
      await billPaymentService.resumeScheduledBillPayment(id);
      setToast({
        type: 'success',
        message: 'Scheduled payment resumed.'
      });
      setActionSuccess('Scheduled payment resumed!');
      setPayments((prev) =>
        prev.map((p) => (p.id === id ? { ...p, status: 'ACTIVE' } : p))
      );
    } catch (err) {
      const errorMsg = err.response?.data?.message || 'Failed to resume scheduled payment.';
      setToast({
        type: 'error',
        message: errorMsg
      });
      setActionError(errorMsg);
    } finally {
      setProcessingId(null);
    }
  };

  const handleToggleAutoPay = async (id, enabled, payPendingBills = false) => {
    setProcessingId(id);
    setActionError('');
    setActionSuccess('');
    setToast(null);

    try {
      await billPaymentService.setScheduledAutoPay(id, enabled, payPendingBills);
      setToast({
        type: 'success',
        message: `Auto-pay ${enabled ? 'enabled' : 'disabled'} successfully.`
      });
      setPayments((prev) =>
        prev.map((p) => (p.id === id ? { ...p, autoPayEnabled: enabled } : p))
      );
    } catch (err) {
      const errorMsg = err.response?.data?.message || 'Failed to update auto-pay status.';
      setToast({ type: 'error', message: errorMsg });
      setActionError(errorMsg);
    } finally {
      setProcessingId(null);
    }
  };

  const handleEnableAutoClick = (paymentId) => {
    setAutoEnablePrompt({ paymentId });
  };

  const handleEnableAutoWithPending = async () => {
    if (!autoEnablePrompt?.paymentId) return;
    const paymentId = autoEnablePrompt.paymentId;
    setAutoEnablePrompt(null);
    await handleToggleAutoPay(paymentId, true, true);
  };

  const handleEnableAutoWithoutPending = async () => {
    if (!autoEnablePrompt?.paymentId) return;
    const paymentId = autoEnablePrompt.paymentId;
    setAutoEnablePrompt(null);
    await handleToggleAutoPay(paymentId, true, false);
    navigate('/bill-payments/scheduled');
  };

  const handleToggleDetails = async (id) => {
    if (expandedId === id) {
      setExpandedId(null);
      return;
    }

    setExpandedId(id);

    if (invoiceMap[id] || historyMap[id]) {
      return;
    }

    setDetailsLoading(true);
    try {
      const [invoiceRes, historyRes] = await Promise.all([
        billPaymentService.getScheduledInvoices(id),
        billPaymentService.getScheduledHistory(id),
      ]);
      setInvoiceMap((prev) => ({ ...prev, [id]: invoiceRes.data || [] }));
      setHistoryMap((prev) => ({ ...prev, [id]: historyRes.data || [] }));
    } catch (err) {
      const errorMsg = err.response?.data?.message || 'Failed to load setup details.';
      setToast({ type: 'error', message: errorMsg });
      setActionError(errorMsg);
    } finally {
      setDetailsLoading(false);
    }
  };

  const refreshSetupDetails = async (id) => {
    const [invoiceRes, historyRes] = await Promise.all([
      billPaymentService.getScheduledInvoices(id),
      billPaymentService.getScheduledHistory(id),
    ]);
    setInvoiceMap((prev) => ({ ...prev, [id]: invoiceRes.data || [] }));
    setHistoryMap((prev) => ({ ...prev, [id]: historyRes.data || [] }));
  };

  const handlePayInvoiceNow = async (paymentId, invoiceId) => {
    setProcessingId(paymentId);
    setActionError('');
    setActionSuccess('');
    setToast(null);

    try {
      const mapKey = `${paymentId}-${invoiceId}`;
      const existingKey = payRequestKeyMap[mapKey];
      const generatedKey =
        existingKey ||
        (typeof crypto !== 'undefined' && crypto.randomUUID
          ? crypto.randomUUID()
          : `${Date.now()}-${Math.random().toString(16).slice(2)}`);

      if (!existingKey) {
        setPayRequestKeyMap((prev) => ({ ...prev, [mapKey]: generatedKey }));
      }

      await billPaymentService.payScheduledInvoice(paymentId, invoiceId, generatedKey);
      await refreshSetupDetails(paymentId);
      setToast({ type: 'success', message: 'Invoice paid successfully.' });
      setActionSuccess('Invoice paid successfully.');

      setPayRequestKeyMap((prev) => {
        const next = { ...prev };
        delete next[mapKey];
        return next;
      });
    } catch (err) {
      const errorMsg = err.response?.data?.message || 'Failed to pay invoice.';
      setToast({ type: 'error', message: errorMsg });
      setActionError(errorMsg);
    } finally {
      setProcessingId(null);
    }
  };

  const getStatusBadge = (status) => {
    const statusClasses = {
      ACTIVE: 'badge badge-success',
      PAUSED: 'badge badge-warning',
      CANCELLED: 'badge badge-danger',
    };
    return <span className={statusClasses[status] || 'badge'}>{status}</span>;
  };

  const getPastThreeMonthsInvoices = (paymentId) => {
    const invoices = invoiceMap[paymentId] || [];
    return [...invoices]
      .sort((a, b) => {
        const yearDiff = (b.invoiceYear || 0) - (a.invoiceYear || 0);
        if (yearDiff !== 0) return yearDiff;
        return (b.invoiceMonth || 0) - (a.invoiceMonth || 0);
      })
      .slice(0, 3);
  };

  if (loading) {
    return (
      <div className="dashboard-wrapper">
        <nav className="dashboard-nav">
          <div className="nav-brand">
            <svg className="logo-icon-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m3 10 9-7 9 7"/><path d="M5 10v10h14V10"/><path d="M9 20v-5h6v5"/></svg>
            <span>Scheduled Payments</span>
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
          <svg className="logo-icon-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m3 10 9-7 9 7"/><path d="M5 10v10h14V10"/><path d="M9 20v-5h6v5"/></svg>
          <span>Scheduled Payments</span>
        </div>
        <div style={{ display: 'flex', gap: '10px' }}>
          <button
            onClick={() => navigate('/bill-payments/schedule')}
            className="btn btn-primary btn-sm"
          >
            New Schedule
          </button>
          <button onClick={() => navigate('/dashboard')} className="btn btn-back-outline btn-sm">
            Back
          </button>
        </div>
      </nav>

      <main className="dashboard-main">
        {error && <div className="error-message">{error}</div>}
        {actionError && <div className="error-message">{actionError}</div>}
        {actionSuccess && <div className="success-message">{actionSuccess}</div>}

        {payments.length === 0 ? (
          <div className="card">
            <p>No scheduled bill payments found.</p>
            <button
              onClick={() => navigate('/bill-payments/schedule')}
              className="btn btn-primary"
            >
              Create Your First Scheduled Payment
            </button>
          </div>
        ) : (
          <div className="card">
            <h2>Your Scheduled Bill Payments</h2>
            <div className="table-responsive">
              <table className="table">
                <thead>
                  <tr>
                    <th>Provider</th>
                    <th>Reference</th>
                    <th>Amount</th>
                    <th>Frequency</th>
                    <th>Next Execution</th>
                    <th>Auto Pay</th>
                    <th>Status</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {payments.map((payment) => (
                    <React.Fragment key={payment.id}>
                      <tr>
                        <td><strong>{payment.billerName}</strong></td>
                        <td>{payment.billReference}</td>
                        <td className="amount">${payment.amount?.toFixed(2)}</td>
                        <td>{payment.frequency}</td>
                        <td>{new Date(payment.nextExecutionDate).toLocaleDateString()}</td>
                        <td>
                          {payment.autoPayEnabled ? (
                            <span className="badge badge-success">ENABLED</span>
                          ) : (
                            <span className="badge badge-warning">DISABLED</span>
                          )}
                        </td>
                        <td>{getStatusBadge(payment.status)}</td>
                        <td className="actions">
                          <button
                            onClick={() => navigate(`/bill-payments/scheduled/${payment.id}/edit`)}
                            className="btn btn-sm btn-secondary"
                            disabled={payment.status === 'CANCELLED'}
                          >
                            Edit
                          </button>
                          {payment.status === 'ACTIVE' && (
                            <button
                              onClick={() => handlePause(payment.id)}
                              className="btn btn-sm btn-warning"
                              disabled={processingId === payment.id}
                            >
                              Pause
                            </button>
                          )}
                          {payment.status === 'PAUSED' && (
                            <button
                              onClick={() => handleResume(payment.id)}
                              className="btn btn-sm btn-success"
                              disabled={processingId === payment.id}
                            >
                              Resume
                            </button>
                          )}
                          <button
                            onClick={() => (
                              payment.autoPayEnabled
                                ? handleToggleAutoPay(payment.id, false)
                                : handleEnableAutoClick(payment.id)
                            )}
                            className="btn btn-sm btn-primary"
                            disabled={processingId === payment.id || payment.status === 'CANCELLED'}
                          >
                            {payment.autoPayEnabled ? 'Disable Auto' : 'Enable Auto'}
                          </button>
                          <button
                            onClick={() => handleToggleDetails(payment.id)}
                            className="btn btn-sm btn-secondary"
                          >
                            {expandedId === payment.id ? 'Hide Details' : 'View Details'}
                          </button>
                          {payment.status !== 'CANCELLED' && (
                            <button
                              onClick={() => handleCancel(payment.id)}
                              className="btn btn-sm btn-danger"
                              disabled={processingId === payment.id}
                            >
                              Cancel
                            </button>
                          )}
                        </td>
                      </tr>
                      {expandedId === payment.id && (
                        <tr>
                          <td colSpan="8" style={{ background: '#f8fafc' }}>
                            {detailsLoading ? (
                              <p>Loading invoices and history...</p>
                            ) : (
                              <div style={{ display: 'grid', gap: '12px' }}>
                                <div>
                                  <h4 style={{ marginBlockEnd: '8px' }}>Monthly Invoices</h4>
                                  {(invoiceMap[payment.id] || []).length === 0 ? (
                                    <p>No invoices available for this setup yet.</p>
                                  ) : (
                                    <table className="table">
                                      <thead>
                                        <tr>
                                          <th>Month</th>
                                          <th>Amount</th>
                                          <th>Due Date</th>
                                          <th>Status</th>
                                          <th>Action</th>
                                        </tr>
                                      </thead>
                                      <tbody>
                                        {(invoiceMap[payment.id] || []).map((inv) => (
                                          <tr key={inv.id}>
                                            <td>{`${inv.invoiceMonth}/${inv.invoiceYear}`}</td>
                                            <td className="amount">${inv.invoiceAmount?.toFixed(2)}</td>
                                            <td>{new Date(inv.dueDate).toLocaleDateString()}</td>
                                            <td>{inv.status}</td>
                                            <td>
                                              {inv.status === 'UNPAID' ? (
                                                <button
                                                  className="btn btn-sm btn-primary"
                                                  onClick={() => handlePayInvoiceNow(payment.id, inv.id)}
                                                  disabled={processingId === payment.id}
                                                >
                                                  Pay Now
                                                </button>
                                              ) : (
                                                '-'
                                              )}
                                            </td>
                                          </tr>
                                        ))}
                                      </tbody>
                                    </table>
                                  )}
                                </div>
                                <div>
                                  <h4 style={{ marginBlockEnd: '8px' }}>Auto-Pay History</h4>
                                  <h5 style={{ marginBlockEnd: '8px', color: '#334155' }}>
                                    Past 3 Months Invoice History
                                  </h5>
                                  {getPastThreeMonthsInvoices(payment.id).length === 0 ? (
                                    <p>No invoice records found for the last 3 months.</p>
                                  ) : (
                                    <table className="table">
                                      <thead>
                                        <tr>
                                          <th>Invoice Month</th>
                                          <th>Amount</th>
                                          <th>Status</th>
                                          <th>Payment Ref</th>
                                        </tr>
                                      </thead>
                                      <tbody>
                                        {getPastThreeMonthsInvoices(payment.id).map((inv) => (
                                          <tr key={`past-3-${inv.id}`}>
                                            <td>{`${inv.invoiceMonth}/${inv.invoiceYear}`}</td>
                                            <td className="amount">${inv.invoiceAmount?.toFixed(2)}</td>
                                            <td>{inv.status}</td>
                                            <td>{inv.paymentReference || '-'}</td>
                                          </tr>
                                        ))}
                                      </tbody>
                                    </table>
                                  )}

                                  {(historyMap[payment.id] || []).filter((h) => h.paymentReference).length === 0 ? (
                                    <p>No successful auto-payments yet.</p>
                                  ) : (
                                    <table className="table">
                                      <thead>
                                        <tr>
                                          <th>Invoice</th>
                                          <th>Amount</th>
                                          <th>Payment Ref</th>
                                          <th>Processed At</th>
                                        </tr>
                                      </thead>
                                      <tbody>
                                        {(historyMap[payment.id] || [])
                                          .filter((h) => h.paymentReference)
                                          .map((h, idx) => (
                                            <tr key={`${h.paymentReference}-${idx}`}>
                                              <td>{`${h.invoiceMonth}/${h.invoiceYear}`}</td>
                                              <td className="amount">${h.amount?.toFixed(2)}</td>
                                              <td>{h.paymentReference}</td>
                                              <td>{h.processedAt ? new Date(h.processedAt).toLocaleString() : '-'}</td>
                                            </tr>
                                          ))}
                                      </tbody>
                                    </table>
                                  )}
                                </div>
                                {payment.lastFailureReason && (
                                  <div className="error-message" style={{ margin: 0 }}>
                                    Last Auto-Pay Failure: {payment.lastFailureReason}
                                  </div>
                                )}
                              </div>
                            )}
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </main>

      {toast && (
        <Toast
          message={toast.message}
          type={toast.type}
          duration={toast.duration || 5000}
          onClose={() => setToast(null)}
        />
      )}

      {confirmDialog && (
        <ConfirmDialog
          title={confirmDialog.title}
          message={confirmDialog.message}
          confirmText="Yes, Cancel Payment"
          cancelText="Keep It"
          variant="danger"
          onConfirm={() => handleCancelConfirmed(confirmDialog.id)}
          onCancel={() => setConfirmDialog(null)}
        />
      )}

      {autoEnablePrompt && (
        <ConfirmDialog
          title="Enable Auto-Pay"
          message="Would you like to pay for any pending bills?"
          confirmText="Yes"
          cancelText="No"
          variant="info"
          confirmVariant="success"
          onConfirm={handleEnableAutoWithPending}
          onCancel={handleEnableAutoWithoutPending}
        />
      )}
    </div>
  );
};

export default ScheduledBillsPage;
