import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import './TaxReport.css';
import './LoanApplication.css';

export default function LoansPage() {
  const navigate  = useNavigate();

  const [loans, setLoans]       = useState([]);
  const [accounts, setAccounts] = useState([]);
  const [loading, setLoading]   = useState(true);
  const [error, setError]       = useState('');

  const [repayModal, setRepayModal]         = useState(null);
  const [repayAccountId, setRepayAccountId] = useState('');
  const [repayAmount, setRepayAmount]       = useState('');
  const [repayLoading, setRepayLoading]     = useState(false);
  const [repaySuccess, setRepaySuccess]     = useState(null);
  const [repayError, setRepayError]         = useState('');

  const [historyOpen, setHistoryOpen]       = useState({});
  const [historyData, setHistoryData]       = useState({});
  const [historyLoading, setHistoryLoading] = useState({});

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await api.get('/loans');
      setLoans(res.data ?? []);
    } catch (err) {
      const status = err?.response?.status;
      if (status === 401 || status === 403) {
        setError('Session expired or access denied. Please sign in again.');
      } else {
        setError(err?.response?.data?.message || 'Failed to load loans.');
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);
  useEffect(() => {
    api.get('/accounts').then(r => setAccounts(r.data ?? [])).catch(() => {});
  }, []);

  const calcPreview = () => {
    if (!repayModal || !repayAmount) return null;
    const outstanding = parseFloat(repayModal.outstandingBalance ?? 0);
    const amount      = Math.min(parseFloat(repayAmount) || 0, outstanding);
    if (amount <= 0) return null;
    const monthlyRate   = parseFloat(repayModal.interestRate) / 12;
    const interestDue   = outstanding * monthlyRate;
    const interestComp  = Math.min(amount, interestDue);
    const principalComp = Math.max(amount - interestComp, 0);
    const balanceAfter  = Math.max(outstanding - principalComp, 0);
    return {
      amount: amount.toFixed(2),
      interestComp: interestComp.toFixed(2),
      principalComp: principalComp.toFixed(2),
      balanceAfter: balanceAfter.toFixed(2),
      willPayOff: balanceAfter === 0,
    };
  };

  const preview = calcPreview();

  const openRepayModal = (loan) => {
    const monthly     = parseFloat(loan.monthlyPayment     ?? 0);
    const outstanding = parseFloat(loan.outstandingBalance ?? 0);
    setRepayModal(loan);
    setRepayAmount(Math.min(monthly, outstanding).toFixed(2));
    setRepayAccountId('');
    setRepaySuccess(null);
    setRepayError('');
  };

  const isPayDisabled = () => {
    if (!repayAmount || !repayAccountId || repayLoading) return true;
    const amount      = parseFloat(repayAmount);
    const outstanding = parseFloat(repayModal?.outstandingBalance ?? 0);
    const isResidual  = outstanding < 1.00 && amount >= outstanding;
    return !isResidual && amount < 1.00;
  };

  const handleRepaySubmit = async () => {
    if (!repayModal || !repayAmount || !repayAccountId) return;
    setRepayLoading(true);
    setRepayError('');
    try {
      const res = await api.post('/loans/repay', {
        loanId:          repayModal.id,
        sourceAccountId: parseInt(repayAccountId),
        amount:          parseFloat(repayAmount),
      });
      setRepaySuccess(res.data);
      if (res.data.loanPaidOff) {
        setLoans(prev => prev.map(l =>
          l.id === repayModal.id ? { ...l, status: 'CLOSED', outstandingBalance: '0.00' } : l
        ));
      } else {
        setLoans(prev => prev.map(l =>
          l.id === repayModal.id ? { ...l, outstandingBalance: res.data.balanceAfter } : l
        ));
      }
      const key = String(repayModal.id);
      setHistoryData(prev => ({ ...prev, [key]: null }));
      load();
    } catch (err) {
      setRepayError(err?.response?.data?.message ?? 'Repayment failed. Please try again.');
    } finally {
      setRepayLoading(false);
    }
  };

  const closeRepayModal = () => {
    setRepayModal(null);
    setRepayAmount('');
    setRepayAccountId('');
    setRepaySuccess(null);
    setRepayError('');
  };

  const toggleHistory = async (loanId) => {
    const key     = String(loanId);
    const nowOpen = !historyOpen[key];
    setHistoryOpen(prev => ({ ...prev, [key]: nowOpen }));
    if (nowOpen && !historyData[key]) {
      setHistoryLoading(prev => ({ ...prev, [key]: true }));
      try {
        const res = await api.get(`/loans/${loanId}/repayments`);
        setHistoryData(prev => ({ ...prev, [key]: res.data ?? [] }));
      } catch {
        setHistoryData(prev => ({ ...prev, [key]: [] }));
      } finally {
        setHistoryLoading(prev => ({ ...prev, [key]: false }));
      }
    }
  };

  const fmt = (v) =>
    `FJD ${parseFloat(v ?? 0).toLocaleString('en-FJ', {
      minimumFractionDigits: 2, maximumFractionDigits: 2,
    })}`;

  const statusConfig = {
    PENDING:  { cls: 'badge-yellow', label: 'Pending review', cardClass: 'pending-card'  },
    ACTIVE:   { cls: 'badge-green',  label: 'Active',         cardClass: 'active-card'   },
    REJECTED: { cls: 'badge-red',    label: 'Rejected',       cardClass: 'rejected-card' },
    CLOSED:   { cls: 'badge-blue',   label: '✓ Fully repaid', cardClass: 'closed-card'   },
    PAID_OFF: { cls: 'badge-blue',   label: '✓ Fully repaid', cardClass: 'closed-card'   },
  };

  const isRepaid = (loan) => loan.status === 'CLOSED' || loan.status === 'PAID_OFF';
  const isActive = (loan) => loan.status === 'ACTIVE';

  const activeLoans  = loans.filter(l => l.status === 'ACTIVE');
  const pendingLoans = loans.filter(l => l.status === 'PENDING');
  const totalBalance = activeLoans.reduce((s, l) => s + parseFloat(l.outstandingBalance ?? 0), 0);
  const totalMonthly = activeLoans.reduce((s, l) => s + parseFloat(l.monthlyPayment ?? 0), 0);

  const renderRepaymentAction = (loan) => {
    const key = String(loan.id);
    if (isActive(loan)) {
      return (
        <div className="admin-action-bar">
          <button
            className="btn-primary btn-sm"
            style={{ minWidth: 160 }}
            onClick={() => openRepayModal(loan)}
          >
            Make a Repayment
          </button>
          <button
            className="btn-ghost btn-sm"
            onClick={() => toggleHistory(loan.id)}
          >
            {historyOpen[key] ? 'Hide history' : 'View history'}
          </button>
        </div>
      );
    }
    if (isRepaid(loan)) {
      return (
        <div className="admin-action-bar">
          <div style={{
            display: 'inline-flex', alignItems: 'center', gap: 8,
            padding: '7px 16px', borderRadius: 8,
            background: '#ecfdf5', border: '1px solid #a7f3d0',
            color: '#065f46', fontSize: 13, fontWeight: 600,
          }}>
            ✓ Loan fully repaid — no further payments required
          </div>
          <button
            className="btn-ghost btn-sm"
            onClick={() => toggleHistory(loan.id)}
          >
            {historyOpen[key] ? 'Hide history' : 'View history'}
          </button>
        </div>
      );
    }
    return null;
  };

  return (
    <div className="tax-page">
      {/* Header */}
      <div className="tax-header">
        <div className="tax-header-left">
          <button className="btn-back no-print" onClick={() => navigate('/dashboard')}>← Back</button>
          <div className="tax-badge">BoF</div>
          <div>
            <h1 className="tax-title">My Loans</h1>
            <p className="tax-subtitle">Bank of Fiji — Loan overview &amp; repayments</p>
          </div>
        </div>
        <div className="tax-header-right no-print">
          <button className="btn-primary" onClick={() => navigate('/loan-application')}>
            + Apply for a loan
          </button>
        </div>
      </div>

      {error && <div className="alert alert-error">! {error}</div>}

      {/* ── Repayment modal ── */}
      {repayModal && (
        <div className="loan-modal-overlay" onClick={e => e.target === e.currentTarget && closeRepayModal()}>
          <div className="loan-modal" style={{ width: 520 }}>
            {repaySuccess ? (
              /* Success receipt */
              <>
                <div style={{ textAlign: 'center', padding: '8px 0 24px' }}>
                  <div style={{ fontSize: 48, marginBottom: 12 }}>
                    {repaySuccess.loanPaidOff ? '✓' : '✓'}
                  </div>
                  <div className="loan-modal-title">
                    {repaySuccess.loanPaidOff ? 'Loan fully repaid!' : 'Repayment successful'}
                  </div>
                  <div style={{ fontSize: 13, color: '#6b7280', marginTop: 4 }}>
                    Ref: <code style={{
                      fontFamily: "'DM Mono', monospace",
                      background: '#dbeafe', color: '#1d4ed8',
                      padding: '2px 8px', borderRadius: 4,
                    }}>{repaySuccess.reference}</code>
                  </div>
                </div>

                <div style={{
                  background: '#f9fafb', border: '1px solid #e5e7eb',
                  borderRadius: 12, padding: '16px 18px', marginBottom: 20,
                }}>
                  {[
                    ['Amount paid',         repaySuccess.amountPaid,          'positive'],
                    ['Principal reduction', repaySuccess.principalComponent,   'positive'],
                    ['Interest charged',    repaySuccess.interestComponent,    'negative'],
                  ].map(([label, val, cls]) => (
                    <div className="income-row" key={label}>
                      <span style={{ color: '#4b5563' }}>{label}</span>
                      <span className={cls}>{fmt(val)}</span>
                    </div>
                  ))}
                  <div className="income-row income-row-highlight">
                    <span style={{ fontWeight: 600 }}>Remaining balance</span>
                    <span style={{
                      fontFamily: "'DM Mono', monospace",
                      color: repaySuccess.loanPaidOff ? '#059669' : '#dc2626',
                      fontWeight: 700,
                    }}>
                      {repaySuccess.loanPaidOff ? 'FJD 0.00 — Cleared ✓' : fmt(repaySuccess.balanceAfter)}
                    </span>
                  </div>
                </div>

                {repaySuccess.loanPaidOff && (
                  <div className="loan-affordability ok" style={{ marginBottom: 20 }}>
                    Congratulations! Your <strong>{repayModal.loanType}</strong> has been fully
                    repaid and is now <strong>closed</strong>. No further payments required.
                  </div>
                )}

                <button className="btn-primary" style={{ width: '100%', padding: '12px' }} onClick={closeRepayModal}>
                  Done
                </button>
              </>
            ) : (
              /* Repayment form */
              <>
                <div className="loan-modal-title">Make a Repayment</div>
                <div className="loan-modal-sub">{repayModal.loanType} — {repayModal.loanNumber}</div>

                <div className="repay-balance-block">
                  <div>
                    <div className="repay-balance-label">Outstanding balance</div>
                    <div className="repay-balance-value" style={{ color: '#dc2626' }}>
                      {fmt(repayModal.outstandingBalance)}
                    </div>
                  </div>
                  <div>
                    <div className="repay-balance-label">Scheduled monthly</div>
                    <div className="repay-balance-value" style={{ color: '#0f2044' }}>
                      {fmt(repayModal.monthlyPayment)}
                    </div>
                  </div>
                </div>

                <div style={{ marginBottom: 16 }}>
                  <label className="loan-label">Debit from account</label>
                  <select className="loan-input" value={repayAccountId}
                    onChange={e => setRepayAccountId(e.target.value)}>
                    <option value="">Select account</option>
                    {accounts.map(a => (
                      <option key={a.id} value={a.id}>
                        {a.accountName} — {a.accountNumber} ({fmt(a.balance)})
                      </option>
                    ))}
                  </select>
                </div>

                <div style={{ marginBottom: 16 }}>
                  <label className="loan-label">Payment amount (FJD)</label>
                  <input type="number" className="loan-input"
                    value={repayAmount}
                    min={parseFloat(repayModal.outstandingBalance) < 1 ? parseFloat(repayModal.outstandingBalance) : 1}
                    max={parseFloat(repayModal.outstandingBalance)}
                    step="0.01"
                    onChange={e => setRepayAmount(e.target.value)}
                  />
                  <span className="loan-hint">
                    Min FJD 1.00 · Max {fmt(repayModal.outstandingBalance)}
                  </span>
                </div>

                {preview && (
                  <div style={{
                    background: '#f0f4ff', border: '1px solid #c7d4f0',
                    borderRadius: 12, padding: '16px 18px', marginBottom: 18,
                  }}>
                    <div style={{
                      fontFamily: "'Playfair Display', serif",
                      fontSize: 14, color: '#0f2044', marginBottom: 12, fontWeight: 600,
                    }}>
                      Payment breakdown
                    </div>
                    {[
                      ['Principal reduction', fmt(preview.principalComp), '#059669'],
                      ['Interest',            fmt(preview.interestComp),  '#dc2626'],
                    ].map(([label, val, color]) => (
                      <div className="income-row" key={label}>
                        <span style={{ color: '#4b5563' }}>{label}</span>
                        <span style={{ fontFamily: "'DM Mono', monospace", color, fontWeight: 500 }}>{val}</span>
                      </div>
                    ))}
                    <div className="income-row income-row-highlight">
                      <span style={{ fontWeight: 600 }}>Balance after payment</span>
                      <span style={{
                        fontFamily: "'DM Mono', monospace",
                        color: preview.willPayOff ? '#059669' : '#dc2626',
                        fontWeight: 700,
                      }}>
                        {preview.willPayOff ? 'FJD 0.00 — Will close loan ✓' : fmt(preview.balanceAfter)}
                      </span>
                    </div>
                    {preview.willPayOff && (
                      <div className="loan-affordability ok" style={{ marginTop: 12 }}>
                        This payment will fully clear your loan and mark it as <strong>Closed</strong>.
                      </div>
                    )}
                  </div>
                )}

                {repayError && (
                  <div className="alert alert-error" style={{ marginBottom: 14 }}>! {repayError}</div>
                )}

                <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
                  <button className="btn-outline btn-sm" onClick={closeRepayModal} disabled={repayLoading}>
                    Cancel
                  </button>
                  <button
                    className="btn-primary"
                    style={{ minWidth: 160, padding: '10px 20px' }}
                    disabled={isPayDisabled()}
                    onClick={handleRepaySubmit}
                  >
                    {repayLoading ? 'Processing…' : `Pay ${preview ? fmt(preview.amount) : repayAmount ? fmt(repayAmount) : ''}`}
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}

      {/* ── Page body ── */}
      {loading ? (
        <div className="tax-loading"><div className="spinner" /><p>Loading your loans…</p></div>
      ) : loans.length === 0 ? (
        <div className="tax-card" style={{ textAlign: 'center', padding: '72px 24px' }}>
          <h2 style={{ fontFamily: "'Playfair Display', serif", fontSize: 24, fontWeight: 600, color: '#0f2044', margin: '0 0 10px' }}>
            No loans yet
          </h2>
          <p style={{ color: '#6b7280', marginBottom: 28, fontSize: 15 }}>
            You have not applied for any loans. Apply online in minutes.
          </p>
          <button className="btn-primary" style={{ padding: '12px 28px', fontSize: 15 }}
            onClick={() => navigate('/loan-application')}>
            Apply for a loan →
          </button>
        </div>
      ) : (
        <>
          {/* Summary cards */}
          <div className="summary-grid">
            <div className="summary-card red">
              <span className="summary-label">Total outstanding</span>
              <span className="summary-amount">{fmt(totalBalance)}</span>
              <span className="summary-sub">{activeLoans.length} active loan{activeLoans.length !== 1 ? 's' : ''}</span>
            </div>
            <div className="summary-card blue">
              <span className="summary-label">Monthly repayments</span>
              <span className="summary-amount">{fmt(totalMonthly)}</span>
              <span className="summary-sub">Total across active loans</span>
            </div>
            <div className="summary-card yellow">
              <span className="summary-label">Pending applications</span>
              <span className="summary-amount">{pendingLoans.length}</span>
              <span className="summary-sub">Awaiting assessment</span>
            </div>
            <div className="summary-card green">
              <span className="summary-label">Total loans</span>
              <span className="summary-amount">{loans.length}</span>
              <span className="summary-sub">All time</span>
            </div>
          </div>

          {/* Loan list */}
          <div className="tax-card">
            <h2 className="card-title">Loan accounts</h2>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              {loans.map(loan => {
                const key         = String(loan.id);
                const cfg         = statusConfig[loan.status] ?? { cls: 'badge-blue', label: loan.status, cardClass: '' };
                const outstanding = parseFloat(loan.outstandingBalance ?? loan.principalAmount ?? 0);
                const principal   = parseFloat(loan.principalAmount ?? 0);
                const paidPct     = principal > 0
                  ? Math.floor(Math.max(0, Math.min(99.9, ((principal - outstanding) / principal) * 100)))
                  : 0;

                return (
                  <div key={loan.id} className={`loan-card ${cfg.cardClass ?? ''}`}>
                    {/* Card header */}
                    <div className="loan-card-header">
                      <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
                        <span className="loan-type-label">{loan.loanType}</span>
                        <code style={{
                          fontFamily: "'DM Mono', monospace",
                          fontSize: 11.5, color: '#9ca3af',
                          background: '#f3f4f6', padding: '2px 8px', borderRadius: 4,
                        }}>
                          {loan.loanNumber}
                        </code>
                        <span className={`badge ${cfg.cls}`}>{cfg.label}</span>
                      </div>
                    </div>

                    {/* Stats grid */}
                    <div className="loan-card-body">
                      {[
                        ['Principal',       fmt(loan.principalAmount), null],
                        ['Outstanding',     isRepaid(loan) ? 'FJD 0.00' : fmt(loan.outstandingBalance ?? loan.principalAmount), isRepaid(loan) ? '#059669' : '#dc2626'],
                        ['Monthly payment', isRepaid(loan) ? '—' : fmt(loan.monthlyPayment), isRepaid(loan) ? '#9ca3af' : null],
                        ['Interest rate',   `${(parseFloat(loan.interestRate) * 100).toFixed(2)}% p.a.`, null],
                        ['Term',            `${loan.termMonths} months`, null],
                        ['End date',        loan.endDate || '—', null],
                      ].map(([label, val, color]) => (
                        <div className="loan-stat" key={label}>
                          <span className="loan-stat-label">{label}</span>
                          <span className="loan-stat-value" style={color ? { color } : {}}>
                            {val}
                          </span>
                        </div>
                      ))}
                    </div>

                    {/* Progress bar */}
                    {(isActive(loan) || isRepaid(loan)) && (
                      <div style={{ marginTop: 16 }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: '#9ca3af', marginBottom: 6 }}>
                          <span style={{ fontWeight: 600, textTransform: 'uppercase', letterSpacing: '.06em' }}>Repayment progress</span>
                          <span style={{ fontFamily: "'DM Mono', monospace", fontWeight: 600, color: isRepaid(loan) ? '#059669' : '#0f2044' }}>
                            {isRepaid(loan) ? '100' : paidPct}% paid
                          </span>
                        </div>
                        <div className="loan-progress-bar">
                          <div className="loan-progress-fill"
                            style={{ width: isRepaid(loan) ? '100%' : `${paidPct}%` }} />
                        </div>
                      </div>
                    )}

                    {/* Early payoff notice */}
                    {isRepaid(loan) && loan.endDate && new Date(loan.endDate) > new Date() && (
                      <div className="loan-info-banner info" style={{ marginTop: 14, marginBottom: 0 }}>
                        <span className="loan-info-banner-icon">✓</span>
                        <span>
                          <strong>Paid off early!</strong> Fully repaid ahead of the scheduled end date.
                          Loan is now <strong>Closed</strong>.
                        </span>
                      </div>
                    )}

                    {/* Rejection reason */}
                    {loan.rejectionReason && (
                      <div className="loan-info-banner danger" style={{ marginTop: 14, marginBottom: 0 }}>
                        <span className="loan-info-banner-icon">!</span>
                        <span><strong>Rejection reason:</strong> {loan.rejectionReason}</span>
                      </div>
                    )}

                    {/* Action buttons */}
                    {renderRepaymentAction(loan)}

                    {/* Repayment history */}
                    {historyOpen[key] && (
                      <div style={{
                        marginTop: 16, borderTop: '1px solid #f3f4f6', paddingTop: 16,
                      }}>
                        <div style={{
                          fontFamily: "\"Sora\", \"Manrope\", \"Segoe UI\", sans-serif",
                          fontSize: 14, color: '#0f2044', marginBottom: 12, fontWeight: 600,
                        }}>
                          Repayment history
                        </div>
                        {historyLoading[key] ? (
                          <p style={{ fontSize: 13, color: '#9ca3af' }}>Loading…</p>
                        ) : historyData[key]?.length > 0 ? (
                          <div style={{ overflowX: 'auto' }}>
                            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12.5 }}>
                              <thead>
                                <tr style={{ borderBottom: '2px solid #f3f4f6' }}>
                                  {['Date', 'Reference', 'Amount paid', 'Principal', 'Interest', 'Balance after'].map(h => (
                                    <th key={h} style={{
                                      textAlign: 'left', padding: '6px 10px',
                                      fontSize: 10.5, fontWeight: 700,
                                      textTransform: 'uppercase', letterSpacing: '.06em',
                                      color: '#9ca3af', fontFamily: "\"Sora\", \"Manrope\", \"Segoe UI\", sans-serif",
                                    }}>{h}</th>
                                  ))}
                                </tr>
                              </thead>
                              <tbody>
                                {historyData[key].map((r, i) => (
                                  <tr key={r.id} style={{
                                    borderBottom: '1px solid #f3f4f6',
                                    background: i % 2 === 0 ? '#fff' : '#fafafa',
                                  }}>
                                    <td style={{ padding: '8px 10px', color: '#4b5563' }}>{r.paymentDate}</td>
                                    <td style={{ padding: '8px 10px', fontFamily: "\"Sora\", \"Manrope\", \"Segoe UI\", sans-serif", fontSize: 11, color: '#9ca3af' }}>{r.reference}</td>
                                    <td style={{ padding: '8px 10px', fontFamily: "\"Sora\", \"Manrope\", \"Segoe UI\", sans-serif", fontWeight: 600 }}>{fmt(r.amountPaid)}</td>
                                    <td style={{ padding: '8px 10px', fontFamily: "\"Sora\", \"Manrope\", \"Segoe UI\", sans-serif", color: '#059669' }}>{fmt(r.principalComponent)}</td>
                                    <td style={{ padding: '8px 10px', fontFamily: "\"Sora\", \"Manrope\", \"Segoe UI\", sans-serif", color: '#dc2626' }}>{fmt(r.interestComponent)}</td>
                                    <td style={{ padding: '8px 10px', fontFamily: "\"Sora\", \"Manrope\", \"Segoe UI\", sans-serif", color: '#0f2044', fontWeight: 500 }}>{fmt(r.balanceAfter)}</td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        ) : (
                          <p style={{ fontSize: 13, color: '#9ca3af' }}>No repayments recorded yet.</p>
                        )}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>

          <p className="tax-disclaimer">
            * Loan information is subject to your loan agreement terms.
            Interest rates are set in accordance with Reserve Bank of Fiji guidelines.
            Contact Bank of Fiji on 132 652 for queries.
          </p>
        </>
      )}
    </div>
  );
}