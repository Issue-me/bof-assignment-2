import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import ConfirmDialog from '../components/ConfirmDialog';
import './TaxReport.css';

export default function AdminRiwtExemptionsPage() {
  const navigate = useNavigate();

  const [requests, setRequests]       = useState([]);
  const [loading, setLoading]         = useState(true);
  const [error, setError]             = useState('');
  const [actionMsg, setActionMsg]     = useState('');
  const [processing, setProcessing]   = useState(false);
  const [approveConfirm, setApproveConfirm] = useState(null);

  // Reject modal state
  const [rejectModal, setRejectModal]     = useState(null); // { customerEmail, taxYear, customerName }
  const [rejectReason, setRejectReason]   = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    setActionMsg('');
    try {
      const res = await api.get('/tax/riwt-exemption/pending');
      setRequests(res.data ?? []);
    } catch {
      setError('Failed to load exemption requests.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const handleApprove = (customerEmail, taxYear, customerName) => {
    setApproveConfirm({ customerEmail, taxYear, customerName });
  };

  const confirmApprove = async () => {
    if (!approveConfirm) return;

    const { customerEmail, taxYear, customerName } = approveConfirm;
    setApproveConfirm(null);

    setProcessing(true);
    setError('');
    try {
      await api.post(`/tax/riwt-exemption/${encodeURIComponent(customerEmail)}/approve`, { taxYear });
      setActionMsg(` RIWT exemption approved for ${customerName} (${taxYear}). Customer notified.`);
      await load();
    } catch (err) {
      setError(err?.response?.data?.message ?? 'Approval failed.');
    } finally {
      setProcessing(false);
    }
  };

  const handleRejectSubmit = async () => {
    if (!rejectModal || !rejectReason.trim()) return;
    setProcessing(true);
    setError('');
    try {
      await api.post(
        `/tax/riwt-exemption/${encodeURIComponent(rejectModal.customerEmail)}/reject`,
        { taxYear: rejectModal.taxYear, reason: rejectReason }
      );
      setActionMsg(`Exemption for ${rejectModal.customerName} (${rejectModal.taxYear}) rejected. Customer notified.`);
      setRejectModal(null);
      setRejectReason('');
      await load();
    } catch (err) {
      setError(err?.response?.data?.message ?? 'Rejection failed.');
    } finally {
      setProcessing(false);
    }
  };

  const statusConfig = {
    PENDING:  { cls: 'badge-yellow', label: 'Pending review' },
    APPROVED: { cls: 'badge-green',  label: 'Approved'       },
    REJECTED: { cls: 'badge-red',    label: 'Rejected'       },
  };

  const pending  = requests.filter(r => r.status === 'PENDING');
  const reviewed = requests.filter(r => r.status !== 'PENDING');

  return (
    <div className="tax-page">

      {/* Header */}
      <div className="tax-header">
        <div className="tax-header-left">
          <button className="btn-back no-print" onClick={() => navigate('/dashboard')}>← Back</button>
          <div className="tax-badge">FRCS</div>
          <div>
            <h1 className="tax-title">RIWT Exemptions</h1>
            <p className="tax-subtitle">Bank of Fiji — Review customer exemption certificates</p>
          </div>
        </div>
        <div className="tax-header-right no-print">
          <button className="btn-outline" onClick={load}>↻ Refresh</button>
        </div>
      </div>

      {error     && <div className="alert alert-error">⚠️ {error}</div>}
      {actionMsg && <div className="alert alert-success">✅ {actionMsg}</div>}

      {approveConfirm && (
        <ConfirmDialog
          title="Approve RIWT Exemption"
          message={`Approve RIWT exemption for ${approveConfirm.customerName} (${approveConfirm.taxYear})?\n\nThis confirms you have verified the FRCS Certificate of Exemption.\nThe customer will be notified and RIWT will no longer be deducted.`}
          confirmText="Approve"
          cancelText="Cancel"
          confirmVariant="success"
          cancelVariant="cancel-danger"
          variant="warning"
          onConfirm={confirmApprove}
          onCancel={() => setApproveConfirm(null)}
        />
      )}

      {/* Reject modal */}
      {rejectModal && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,.5)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000,
        }}>
          <div style={{
            background: 'var(--surface)', borderRadius: 'var(--radius)',
            padding: 28, width: 460, maxWidth: '90vw',
            border: '1px solid var(--border)',
          }}>
            <h3 style={{ fontFamily: "'DM Serif Display', serif", fontWeight: 400, margin: '0 0 6px' }}>
              Reject exemption — {rejectModal.customerName}
            </h3>
            <p style={{ fontSize: 13, color: 'var(--text-secondary)', margin: '0 0 16px' }}>
              Tax year {rejectModal.taxYear}. Provide a reason — this will be sent to the customer.
            </p>

            {/* FRCS verification reminder */}
            <div className="interest-notice" style={{ marginBottom: 14 }}>
              <span className="notice-icon">ℹ️</span>
              <div style={{ fontSize: 13 }}>
                Have you checked with FRCS? You can verify exemption status at{' '}
                <a
                  href="https://tpos.frcs.org.fj/taxpayerportal/#/Logon"
                  target="_blank"
                  rel="noopener noreferrer"
                  style={{ color: 'var(--navy, #0f2d55)' }}
                >
                  tpos.frcs.org.fj ↗
                </a>
              </div>
            </div>

            <textarea
              className="loan-input"
              rows={3}
              placeholder="e.g. Certificate could not be verified with FRCS. Please resubmit a valid certificate."
              value={rejectReason}
              onChange={e => setRejectReason(e.target.value)}
              style={{ resize: 'vertical', marginBottom: 16, width: '100%' }}
            />
            <div className="actions-buttons" style={{ justifyContent: 'flex-end' }}>
              <button
                className="btn-outline"
                onClick={() => { setRejectModal(null); setRejectReason(''); }}
              >
                Cancel
              </button>
              <button
                className="btn-primary"
                style={{ background: 'var(--red)', borderColor: 'var(--red)' }}
                disabled={!rejectReason.trim() || processing}
                onClick={handleRejectSubmit}
              >
                {processing ? 'Rejecting…' : 'Confirm rejection'}
              </button>
            </div>
          </div>
        </div>
      )}

      {loading ? (
        <div className="tax-loading"><div className="spinner" /><p>Loading exemption requests…</p></div>
      ) : (
        <>
          {/* Summary */}
          <div className="summary-grid">
            <div className="summary-card yellow">
              <span className="summary-label">Pending review</span>
              <span className="summary-amount">{pending.length}</span>
              <span className="summary-sub">Awaiting teller action</span>
            </div>
            <div className="summary-card green">
              <span className="summary-label">Approved</span>
              <span className="summary-amount">{requests.filter(r => r.status === 'APPROVED').length}</span>
              <span className="summary-sub">Exemptions active</span>
            </div>
            <div className="summary-card red">
              <span className="summary-label">Rejected</span>
              <span className="summary-amount">{requests.filter(r => r.status === 'REJECTED').length}</span>
              <span className="summary-sub">Could not be verified</span>
            </div>
            <div className="summary-card blue">
              <span className="summary-label">Total requests</span>
              <span className="summary-amount">{requests.length}</span>
              <span className="summary-sub">All time</span>
            </div>
          </div>

          {/* Pending requests — action required */}
          {pending.length > 0 && (
            <div className="tax-card">
              <h2 className="card-title">Pending Review</h2>

              {/* FRCS verification note */}
              <div className="interest-notice" style={{ marginBottom: 16 }}>
                <span className="notice-icon">ℹ️</span>
                <div>
                  <strong>Before approving</strong>, verify the certificate with FRCS at{' '}
                  <a
                    href="https://tpos.frcs.org.fj/taxpayerportal/#/Logon"
                    target="_blank"
                    rel="noopener noreferrer"
                    style={{ color: 'var(--navy, #0f2d55)', fontWeight: 600 }}
                  >
                    tpos.frcs.org.fj ↗
                  </a>
                  . Only approve if you can confirm the customer holds a valid FRCS RIWT exemption.
                </div>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                {pending.map(req => (
                  <div key={req.id} className="loan-card">
                    <div className="loan-card-header">
                      <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
                        <span className="loan-type-label">{req.customerName}</span>
                        <span className="info-value mono" style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                          {req.customerId}
                        </span>
                        <span className="badge badge-yellow">Pending review</span>
                      </div>
                      <div style={{ fontSize: 13, color: 'var(--text-secondary)' }}>
                        Tax year: <strong>{req.taxYear}</strong>
                        &nbsp;·&nbsp; Submitted: {req.submittedDate}
                      </div>
                    </div>

                    <div className="loan-card-body">
                      <div className="loan-stat">
                        <span className="loan-stat-label">Customer email: </span>
                        <span className="loan-stat-value" style={{ fontSize: 13 }}>{req.customerEmail}</span>
                      </div>
                      <div className="loan-stat">
                        <span className="loan-stat-label">TIN Number: </span>
                        <span className="loan-stat-value">{req.tinNumber || '—'}</span>
                      </div>
                      <div className="loan-stat">
                        <span className="loan-stat-label">Certificate file: </span>
                        <span className="loan-stat-value" style={{ fontSize: 13 }}>
                          {req.fileName ?? 'Uploaded'}
                        </span>
                      </div>
                      <div className="loan-stat">
                        <span className="loan-stat-label">RIWT withheld: </span>
                        <span className="loan-stat-value" style={{ color: 'var(--red)' }}>
                          FJD {parseFloat(req.riwtWithheld ?? 0).toLocaleString('en-FJ', {
                            minimumFractionDigits: 2, maximumFractionDigits: 2,
                          })}
                        </span>
                      </div>
                    </div>

                    {/* Certificate download link if available */}
                    {req.fileUrl && (
                      <div style={{ marginTop: 10 }}>
                        <a
                          href={req.fileUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="btn-outline"
                          style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 13, padding: '6px 14px', textDecoration: 'none' }}
                        >
                          📄 View Certificate
                        </a>
                      </div>
                    )}

                    <div className="actions-buttons" style={{ marginTop: 14 }}>
                      <button
                        className="btn-primary"
                        style={{ fontSize: 13, padding: '7px 18px', width: '20%' }}
                        disabled={processing}
                        onClick={() => handleApprove(req.customerEmail, req.taxYear, req.customerName)}
                      >
                        ✓ Approve Exemption
                      </button>
                      <button
                        className="btn-outline"
                        style={{ fontSize: 13, padding: '7px 18px', color: 'var(--red)', borderColor: 'var(--red)' }}
                        disabled={processing}
                        onClick={() => setRejectModal({
                          customerEmail: req.customerEmail,
                          taxYear: req.taxYear,
                          customerName: req.customerName,
                        })}
                      >
                        ✕ Reject
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Already reviewed */}
          {reviewed.length > 0 && (
            <div className="tax-card">
              <h2 className="card-title">Reviewed Requests</h2>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {reviewed.map(req => {
                  const cfg = statusConfig[req.status] ?? { cls: 'badge-blue', label: req.status };
                  return (
                    <div key={req.id} className="loan-card">
                      <div className="loan-card-header">
                        <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
                          <span className="loan-type-label">{req.customerName}</span>
                          <span className="info-value mono" style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                            {req.customerId}
                          </span>
                          <span className={`badge ${cfg.cls}`}>{cfg.label}</span>
                        </div>
                        <span style={{ fontSize: 13, color: 'var(--text-secondary)' }}>
                          Tax year {req.taxYear} · Reviewed: {req.reviewedDate ?? '—'}
                        </span>
                      </div>
                      <div className="loan-card-body">
                        <div className="loan-stat">
                          <span className="loan-stat-label">Email</span>
                          <span className="loan-stat-value" style={{ fontSize: 13 }}>{req.customerEmail}</span>
                        </div>
                        <div className="loan-stat">
                          <span className="loan-stat-label">Reviewed by</span>
                          <span className="loan-stat-value" style={{ fontSize: 13 }}>{req.reviewedBy ?? '—'}</span>
                        </div>
                      </div>
                      {req.status === 'REJECTED' && req.rejectionReason && (
                        <div className="loan-affordability warn" style={{ marginTop: 10 }}>
                          <strong>Rejection reason:</strong> {req.rejectionReason}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {requests.length === 0 && (
            <div className="tax-card" style={{ textAlign: 'center', padding: '48px 24px' }}>
              <p style={{ fontSize: 36, marginBottom: 12 }}>📋</p>
              <h2 className="card-title" style={{ justifyContent: 'center' }}>No exemption requests</h2>
              <p style={{ color: 'var(--text-secondary)' }}>
                No customers have submitted RIWT exemption certificates yet.
              </p>
            </div>
          )}

          <p className="tax-disclaimer">
            * RIWT exemptions must be verified against the FRCS Taxpayer Online Services portal before approval.
            Only approve requests where the customer holds a valid FRCS Certificate of Exemption.
            Contact compliance@bof.com.fj for queries.
          </p>
        </>
      )}
    </div>
  );
}