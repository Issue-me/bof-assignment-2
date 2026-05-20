import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import './TaxReport.css';
import './LoanApplication.css';

const COUNTABLE_STATUSES = ['PENDING', 'ACTIVE', 'REJECTED', 'CLOSED', 'PAID_OFF'];

const DOC_TYPE_LABELS = {
  PRIMARY_ID:          'Primary ID',
  RESIDENCY_EVIDENCE:  'Residency Evidence',
  BANK_STATEMENT:      'Bank Statement',
  EMPLOYMENT_DOCUMENT: 'Employment Document',
  OTHER:               'Other Documents',
};

const ICON = {
  'application/pdf': 'PDF',
  'image/jpeg':      'JPG',
  'image/jpg':       'JPG',
  'image/png':       'PNG',
};

export default function AdminLoansPage() {
  const navigate = useNavigate();

  const [loans, setLoans]           = useState([]);
  const [loading, setLoading]       = useState(true);
  const [filter, setFilter]         = useState('ALL');
  const [error, setError]           = useState('');
  const [actionMsg, setActionMsg]   = useState('');
  const [processing, setProcessing] = useState(false);

  const [confirmModal, setConfirmModal] = useState(null);
  const [rejectModal, setRejectModal]   = useState(null);
  const [rejectReason, setRejectReason] = useState('');
  const [rateModal, setRateModal]       = useState(null);
  const [rateInput, setRateInput]       = useState('');
  const [docsPanel, setDocsPanel]       = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    setActionMsg('');
    try {
      const res = await api.get('/loans/admin/all');
      setLoans(res.data ?? []);
    } catch {
      setError('Failed to load loan applications.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const filtered = filter === 'ALL' ? loans : loans.filter(l => {
    if (filter === 'CLOSED') return l.status === 'CLOSED' || l.status === 'PAID_OFF';
    return l.status === filter;
  });

  const stats = useMemo(() => {
    const counts = { PENDING: 0, ACTIVE: 0, REJECTED: 0, CLOSED: 0 };
    let totalExposure = 0, totalApplied = 0;
    for (const l of loans) {
      if (l.status === 'PENDING')                              counts.PENDING++;
      else if (l.status === 'ACTIVE')                          counts.ACTIVE++;
      else if (l.status === 'REJECTED')                        counts.REJECTED++;
      else if (l.status === 'CLOSED' || l.status === 'PAID_OFF') counts.CLOSED++;
      if (l.status === 'ACTIVE')  totalExposure += parseFloat(l.outstandingBalance ?? 0);
      if (l.status === 'PENDING') totalApplied  += parseFloat(l.principalAmount   ?? 0);
    }
    return { counts, totalExposure, totalApplied };
  }, [loans]);

  const fmt = (v) =>
    `FJD ${parseFloat(v ?? 0).toLocaleString('en-FJ', {
      minimumFractionDigits: 2, maximumFractionDigits: 2,
    })}`;

  const handleApprove = (loan) => {
    setError(''); setActionMsg('');
    setConfirmModal({
      title:        `Approve loan ${loan.loanNumber}?`,
      message:      `This will disburse ${fmt(loan.principalAmount)} to ${loan.customerFullName}'s account immediately. This action cannot be undone.`,
      confirmLabel: '✓ Approve & Disburse',
      danger:       false,
      onConfirm: async () => {
        setProcessing(true);
        try {
          await api.post(`/loans/${loan.id}/approve`);
          setActionMsg(`Loan ${loan.loanNumber} approved — ${fmt(loan.principalAmount)} disbursed.`);
          await load();
        } catch (err) {
          setError(err?.response?.data?.message ?? 'Approval failed.');
        } finally {
          setProcessing(false);
        }
      },
    });
  };

  const handleRejectSubmit = async () => {
    if (!rejectModal || !rejectReason.trim()) return;
    setProcessing(true);
    setError(''); setActionMsg('');
    try {
      await api.post(`/loans/${rejectModal.loanId}/reject`, { reason: rejectReason });
      setActionMsg(`Loan ${rejectModal.loanNumber} rejected.`);
      setRejectModal(null);
      setRejectReason('');
      await load();
    } catch {
      setError('Rejection failed.');
    } finally {
      setProcessing(false);
    }
  };

  const handleRateSubmit = async () => {
    if (!rateModal || !rateInput) return;
    const pct = parseFloat(rateInput);
    if (isNaN(pct) || pct <= 0 || pct > 100) {
      setError('Enter a valid rate between 0.01% and 100%'); return;
    }
    const decimal = (pct / 100).toFixed(6);
    setProcessing(true);
    setError(''); setActionMsg('');
    try {
      await api.patch(`/loans/${rateModal.loanId}/interest-rate`, { annualRate: parseFloat(decimal) });
      setActionMsg(`Interest rate updated to ${pct}% p.a. on loan ${rateModal.loanNumber}.`);
      setRateModal(null);
      setRateInput('');
      await load();
    } catch (err) {
      setError(err?.response?.data?.message ?? 'Rate update failed.');
    } finally {
      setProcessing(false);
    }
  };

  const handleDownloadDoc = async (loanId, docId, fileName) => {
    try {
      const res = await api.get(`/loans/admin/${loanId}/documents/${docId}`, { responseType: 'blob' });
      const url  = URL.createObjectURL(new Blob([res.data]));
      const link = document.createElement('a');
      link.href  = url;
      link.download = fileName;
      link.click();
      URL.revokeObjectURL(url);
    } catch {
      setError('Failed to download document.');
    }
  };

  const statusConfig = {
    PENDING:  { cls: 'badge-yellow', label: 'Pending',      cardClass: 'pending-card'  },
    ACTIVE:   { cls: 'badge-green',  label: 'Active',       cardClass: 'active-card'   },
    REJECTED: { cls: 'badge-red',    label: 'Rejected',     cardClass: 'rejected-card' },
    CLOSED:   { cls: 'badge-blue',   label: 'Closed',       cardClass: 'closed-card'   },
    PAID_OFF: { cls: 'badge-green',  label: 'Paid Off',     cardClass: 'closed-card'   },
  };

  const { counts, totalExposure, totalApplied } = stats;

  return (
    <div className="tax-page admin-loans-page teller-loan-approval-page">

      {/* ── Confirm modal ── */}
      {confirmModal && (
        <div className="loan-modal-overlay" onClick={e => e.target === e.currentTarget && !processing && setConfirmModal(null)}>
          <div className="loan-modal" style={{ width: 460 }}>
            <div className="loan-modal-title">{confirmModal.title}</div>
            <p style={{ fontSize: 14, color: '#4b5563', margin: '8px 0 24px', lineHeight: 1.65 }}>
              {confirmModal.message}
            </p>
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
              <button className="btn-ghost btn-sm" disabled={processing}
                onClick={() => setConfirmModal(null)}>Cancel</button>
              <button
                className="btn-primary btn-sm"
                style={confirmModal.danger ? { background: '#dc2626', borderColor: '#dc2626' } : {}}
                disabled={processing}
                onClick={async () => { await confirmModal.onConfirm(); setConfirmModal(null); }}
              >
                {processing ? 'Processing…' : confirmModal.confirmLabel}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Reject modal ── */}
      {rejectModal && (
        <div className="loan-modal-overlay" onClick={e => e.target === e.currentTarget && setRejectModal(null)}>
          <div className="loan-modal" style={{ width: 460 }}>
            <div className="loan-modal-title">Reject loan {rejectModal.loanNumber}</div>
            <p className="loan-modal-sub">Provide a reason — this will be visible to the customer.</p>
            <textarea
              className="loan-input"
              rows={4}
              placeholder="e.g. Insufficient income to meet repayment obligations"
              value={rejectReason}
              onChange={e => setRejectReason(e.target.value)}
              style={{ resize: 'vertical', marginBottom: 20 }}
            />
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
              <button className="btn-ghost btn-sm"
                onClick={() => { setRejectModal(null); setRejectReason(''); }}>Cancel</button>
              <button className="btn-danger btn-sm"
                disabled={!rejectReason.trim() || processing}
                onClick={handleRejectSubmit}>
                {processing ? 'Rejecting…' : 'Confirm rejection'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Rate modal ── */}
      {rateModal && (
        <div className="loan-modal-overlay" onClick={e => e.target === e.currentTarget && setRateModal(null)}>
          <div className="loan-modal" style={{ width: 440 }}>
            <div className="loan-modal-title">Update Interest Rate</div>
            <p className="loan-modal-sub">{rateModal.loanType} — {rateModal.loanNumber}</p>

            <div style={{
              background: '#f9fafb', border: '1px solid #e5e7eb',
              borderRadius: 10, padding: '14px 18px', marginBottom: 20,
              display: 'flex', justifyContent: 'space-between', alignItems: 'center',
            }}>
              <span style={{ fontSize: 13, color: '#6b7280' }}>Current rate</span>
              <span style={{ fontFamily: "'DM Mono', monospace", fontWeight: 700, color: '#0f2044', fontSize: 15 }}>
                {(parseFloat(rateModal.currentRate) * 100).toFixed(2)}% p.a.
              </span>
            </div>

            <div style={{ marginBottom: 16 }}>
              <label className="loan-label">New Annual Rate (%)</label>
              <div style={{ position: 'relative' }}>
                <input type="number" step="0.05" min="0.05" max="100"
                  className="loan-input" placeholder="e.g. 9.5"
                  value={rateInput} onChange={e => setRateInput(e.target.value)}
                  style={{ paddingRight: 36 }}
                />
                <span style={{
                  position: 'absolute', right: 14, top: '50%', transform: 'translateY(-50%)',
                  color: '#9ca3af', fontSize: 14, pointerEvents: 'none', fontWeight: 600,
                }}>%</span>
              </div>
              {rateInput && !isNaN(parseFloat(rateInput)) && parseFloat(rateInput) > 0 && (
                <div style={{ fontSize: 12, color: '#6b7280', marginTop: 6 }}>
                  Est. new monthly payment:&nbsp;
                  <strong style={{ color: '#0f2044', fontFamily: "'DM Mono', monospace" }}>
                    {(() => {
                      const r = parseFloat(rateInput) / 100 / 12;
                      const n = rateModal.remainingMonths;
                      const P = parseFloat(rateModal.outstanding);
                      if (!r || !n || !P) return '—';
                      const M = (P * r * Math.pow(1 + r, n)) / (Math.pow(1 + r, n) - 1);
                      return `FJD ${M.toFixed(2)}`;
                    })()}
                  </strong>
                </div>
              )}
            </div>

            <div className="loan-info-banner danger" style={{ marginBottom: 20 }}>
              <span className="loan-info-banner-icon">!</span>
              <span>Rate changes must comply with RBF guidelines. All changes are permanently logged.</span>
            </div>

            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
              <button className="btn-ghost btn-sm"
                onClick={() => { setRateModal(null); setRateInput(''); }}>Cancel</button>
              <button className="btn-primary btn-sm" style={{ minWidth: 140 }}
                disabled={!rateInput || isNaN(parseFloat(rateInput)) || processing}
                onClick={handleRateSubmit}>
                {processing ? 'Saving…' : 'Update Rate'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Document viewer ── */}
      {docsPanel && (
        <div className="loan-modal-overlay" onClick={e => e.target === e.currentTarget && setDocsPanel(null)}>
          <div className="loan-modal" style={{ width: 580 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 20 }}>
              <div>
                <div className="loan-modal-title">Application Documents</div>
                <div className="loan-modal-sub">
                  {docsPanel.loan.loanNumber} — {docsPanel.loan.customerFullName}
                </div>
              </div>
              <button onClick={() => setDocsPanel(null)} style={{
                background: 'none', border: 'none', fontSize: 24,
                cursor: 'pointer', color: '#9ca3af', lineHeight: 1,
              }}>×</button>
            </div>

            {!docsPanel.loan.documents?.length ? (
              <div style={{ textAlign: 'center', padding: '40px 0', color: '#9ca3af' }}>
                <p style={{ fontSize: 14 }}>No documents uploaded for this application.</p>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {docsPanel.loan.documents.map(doc => (
                  <div key={doc.id} className="doc-row">
                    <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                      <span style={{ fontSize: 24 }}>{ICON[doc.contentType] ?? 'DOC'}</span>
                      <div className="doc-row-info">
                        <div className="doc-row-title">
                          {DOC_TYPE_LABELS[doc.documentType] ?? doc.documentType}
                        </div>
                        <div className="doc-row-filename">
                          {doc.fileName} · {(doc.fileSize / 1024).toFixed(1)} KB
                          · {doc.uploadedAt?.replace('T', ' ').substring(0, 16)}
                        </div>
                      </div>
                    </div>
                    <button className="btn-ghost btn-sm"
                      onClick={() => handleDownloadDoc(docsPanel.loan.id, doc.id, doc.fileName)}>
                      Download
                    </button>
                  </div>
                ))}
              </div>
            )}

            <div style={{ marginTop: 22, textAlign: 'right' }}>
              <button className="btn-ghost btn-sm" onClick={() => setDocsPanel(null)}>Close</button>
            </div>
          </div>
        </div>
      )}

      {/* ── Page header ── */}
      <div className="tax-header loan-page-header">
        <div className="tax-header-left">
          <button className="btn-back no-print" style={{color: '#fff'}} onClick={() => navigate('/dashboard')}>← Back</button>
          <div className="tax-badge">TELLER</div>
          <div>
            <h1 className="tax-title">Loan Approval Queue</h1>
            <p className="tax-subtitle">Bank of Fiji — Teller credit assessment and disbursement workflow</p>
          </div>
        </div>
        <div className="tax-header-right no-print">
          <button className="btn-outline" onClick={load} disabled={loading}>
            {loading ? '↻ Loading…' : '↻ Refresh'}
          </button>
        </div>
      </div>

      {error && (
        <div className="alert alert-error">
          ! {error}
          <button style={{ marginLeft: 'auto', background: 'none', border: 'none', cursor: 'pointer', color: 'inherit', fontSize: 16 }}
            onClick={() => setError('')}>×</button>
        </div>
      )}
      {actionMsg && <div className="alert alert-success">✓ {actionMsg}</div>}

      {loading ? (
        <div className="tax-loading"><div className="spinner" /><p>Loading applications…</p></div>
      ) : (
        <>
          {/* Summary cards */}
          <div className="summary-grid">
            <div className="summary-card yellow">
              <span className="summary-label">Pending review</span>
              <span className="summary-amount">{counts.PENDING}</span>
              <span className="summary-sub">{fmt(totalApplied)} requested</span>
            </div>
            <div className="summary-card green">
              <span className="summary-label">Active loans</span>
              <span className="summary-amount">{counts.ACTIVE}</span>
              <span className="summary-sub">Disbursed &amp; repaying</span>
            </div>
            <div className="summary-card red">
              <span className="summary-label">Total exposure</span>
              <span className="summary-amount">{fmt(totalExposure)}</span>
              <span className="summary-sub">Outstanding across active</span>
            </div>
            <div className="summary-card blue">
              <span className="summary-label">Total applications</span>
              <span className="summary-amount">{loans.length}</span>
              <span className="summary-sub">All time</span>
            </div>
          </div>

          {/* Filter + list */}
          <div className="tax-card">
            <div style={{
              display: 'flex', alignItems: 'center', justifyContent: 'space-between',
              flexWrap: 'wrap', gap: 12, marginBottom: 20,
              paddingBottom: 16, borderBottom: '1px solid #f3f4f6',
            }}>
              <h2 className="card-title" style={{ margin: 0, border: 'none', padding: 0 }}>
                Applications
              </h2>
              <div className="filter-pills">
                {[
                  ['ALL',      `All (${loans.length})`],
                  ['PENDING',  `Pending (${counts.PENDING})`],
                  ['ACTIVE',   `Active (${counts.ACTIVE})`],
                  ['REJECTED', `Rejected (${counts.REJECTED})`],
                  ['CLOSED',   `Closed (${counts.CLOSED})`],
                ].map(([val, label]) => (
                  <button key={val}
                    className={filter === val ? 'filter-pill filter-pill-active' : 'filter-pill'}
                    onClick={() => setFilter(val)}>
                    {label}
                  </button>
                ))}
              </div>
            </div>

            {filtered.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '48px 0', color: '#9ca3af' }}>
                <p style={{ fontSize: 14 }}>No {filter.toLowerCase()} applications.</p>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                {filtered.map(loan => {
                  const cfg = statusConfig[loan.status] ?? { cls: 'badge-blue', label: loan.status, cardClass: '' };

                  return (
                    <div key={loan.id} className={`loan-card ${cfg.cardClass ?? ''}`}>
                      {/* Header row */}
                      <div className="loan-card-header">
                        <div>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap', marginBottom: 6 }}>
                            <span className="loan-type-label">{loan.loanType}</span>
                            <code style={{
                              fontFamily: "'DM Mono', monospace", fontSize: 11.5,
                              color: '#9ca3af', background: '#f3f4f6',
                              padding: '2px 8px', borderRadius: 4,
                            }}>{loan.loanNumber}</code>
                            <span className={`badge ${cfg.cls}`}>{cfg.label}</span>
                            {loan.hasDocuments && (
                              <button
                                onClick={() => setDocsPanel({ loan })}
                                style={{
                                  background: '#eff6ff', border: '1px solid #bfdbfe',
                                  color: '#1d4ed8', fontSize: 12, fontWeight: 600,
                                  padding: '3px 10px', borderRadius: 20, cursor: 'pointer',
                                  fontFamily: 'inherit',
                                }}>
                                {loan.documents?.length} doc{loan.documents?.length !== 1 ? 's' : ''}
                              </button>
                            )}
                          </div>
                          <div className="admin-customer-row">
                            <span className="admin-customer-name">{loan.customerFullName}</span>
                            <span className="admin-customer-id">{loan.customerId}</span>
                          </div>
                        </div>
                      </div>

                      {/* Stats */}
                      <div className="loan-card-body">
                        {[
                          ['Amount',         fmt(loan.principalAmount),                                        null],
                          ['Outstanding',    fmt(loan.outstandingBalance),                                     '#dc2626'],
                          ['Monthly',        fmt(loan.monthlyPayment),                                         null],
                          ['Rate',           `${(parseFloat(loan.interestRate) * 100).toFixed(2)}% p.a.`,      null],
                          ['Term',           `${loan.termMonths} months`,                                      null],
                          ['Applied',        loan.applicationDate,                                             null],
                          ...(loan.purpose         ? [['Purpose',    loan.purpose,            null]] : []),
                          ...(loan.employmentType  ? [['Employment', loan.employmentType,     null]] : []),
                          ...(loan.monthlyIncome   ? [['Income',     fmt(loan.monthlyIncome), '#059669']] : []),
                        ].map(([label, val, color]) => (
                          <div className="loan-stat" key={label}>
                            <span className="loan-stat-label">{label}</span>
                            <span className="loan-stat-value" style={color ? { color } : {}}>
                              {val}
                            </span>
                          </div>
                        ))}
                      </div>

                      {/* Rejection reason */}
                      {loan.rejectionReason && (
                        <div className="loan-info-banner danger" style={{ marginTop: 14, marginBottom: 0 }}>
                          <span className="loan-info-banner-icon">!</span>
                          <span><strong>Rejection reason:</strong> {loan.rejectionReason}</span>
                        </div>
                      )}

                      {/* Action bar */}
                      <div className="admin-action-bar">
                        <button className="btn-ghost btn-sm"
                          onClick={() => setDocsPanel({ loan })}>
                          Documents {loan.documents?.length ? `(${loan.documents.length})` : ''}
                        </button>

                        {loan.status === 'PENDING' && (
                          <>
                            <button className="btn-primary btn-sm"
                              style={{ minWidth: 160 }}
                              disabled={processing}
                              onClick={() => handleApprove(loan)}>
                              ✓ Approve &amp; disburse
                            </button>
                            <button className="btn-danger btn-sm"
                              disabled={processing}
                              onClick={() => setRejectModal({ loanId: loan.id, loanNumber: loan.loanNumber })}>
                              ✕ Reject
                            </button>
                          </>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          <p className="tax-disclaimer">
            * All loan decisions must comply with Reserve Bank of Fiji responsible lending guidelines.
            Maximum Debt Service Ratio: 40%. Contact compliance@bof.com.fj for queries.
          </p>
        </>
      )}
    </div>
  );
}