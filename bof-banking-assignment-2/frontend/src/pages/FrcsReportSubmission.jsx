import React, { useState, useEffect } from 'react';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';
import ConfirmDialog from '../components/ConfirmDialog';
import './TaxReport.css';

/**
 * AdminTaxReport — Admin only.
 *
 * Step 2 & 3 of the tax submission flow:
 *  - Admin sees all customers, filtered by status (DRAFT / PENDING_FRCS / SUBMITTED)
 *  - For any PENDING_FRCS customer, admin clicks "Submit to FRCS"
 *  - Backend generates FRCS receipt, saves it, sends FRCS_TAX_SUBMITTED
 *    notification to the customer (in-app + email with receipt number)
 *  - Table row updates in-place — page does NOT navigate away
 */
const AdminTaxReport = () => {
  const { role } = useAuth();
  const isAdmin = String(role || '').trim().toUpperCase().replace(/^ROLE_/, '') === 'ADMIN';

  const currentYear = new Date().getFullYear();
  const yearOptions = Array.from({ length: 5 }, (_, i) => currentYear - i);

  const [year, setYear]             = useState(currentYear);
  const [rows, setRows]             = useState([]);
  const [loading, setLoading]       = useState(true);
  const [error, setError]           = useState('');
  const [filterStatus, setFilterStatus] = useState('ALL');

  // Per-row submit state — keyed by userEmail so multiple rows can be tracked
  const [submittingEmail, setSubmittingEmail] = useState(null);
  const [successEmail, setSuccessEmail]       = useState(null);
  const [successRef, setSuccessRef]           = useState('');

  // Confirm dialog
  const [confirmItem, setConfirmItem] = useState(null); // { userEmail, name }

  // Side drawer
  const [drawerEmail, setDrawerEmail]     = useState(null);
  const [drawerReport, setDrawerReport]   = useState(null);
  const [drawerLoading, setDrawerLoading] = useState(false);

  useEffect(() => {
    fetchSubmissions();
  }, [year]);

  const fetchSubmissions = async () => {
    setLoading(true);
    setError('');
    setSuccessEmail(null);
    setSuccessRef('');
    try {
      const res = await api.get(`/tax/admin/submissions?year=${year}`);
      setRows(res.data ?? []);
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load submissions. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  // ── Admin submits one customer's report to FRCS ─────────────────────────
  const handleSubmitToFrcs = async () => {
    if (!confirmItem) return;
    const { userEmail, name } = confirmItem;
    setConfirmItem(null);
    setSubmittingEmail(userEmail);
    setError('');

    try {
      const res = await api.post('/tax/admin/submit-to-frcs', { userEmail, year });
      const ref = res.data.frcsReference;

      // Update the row in-place so the table reflects the change immediately
      // without a full page reload
      setRows(prev => prev.map(r =>
        r.userEmail === userEmail
          ? { ...r, status: 'SUBMITTED', frcsReference: ref,
              frcsSubmissionDate: new Date().toISOString().split('T')[0] }
          : r
      ));

      setSuccessEmail(userEmail);
      setSuccessRef(ref);
    } catch (err) {
      setError(
        err.response?.data?.message ||
        `Failed to submit ${name}'s report to FRCS. Please try again.`
      );
    } finally {
      setSubmittingEmail(null);
    }
  };

  // ── Open side drawer with full report ───────────────────────────────────
  const openDrawer = async (userEmail) => {
    setDrawerEmail(userEmail);
    setDrawerReport(null);
    setDrawerLoading(true);
    try {
      const res = await api.get(
        `/tax/admin/report?userEmail=${encodeURIComponent(userEmail)}&year=${year}`
      );
      setDrawerReport(res.data);
    } catch {
      setDrawerReport({ _error: true });
    } finally {
      setDrawerLoading(false);
    }
  };

  const closeDrawer = () => {
    setDrawerEmail(null);
    setDrawerReport(null);
  };

  // ── Helpers ─────────────────────────────────────────────────────────────
  const fmt = (v) =>
    v != null
      ? `FJD ${parseFloat(v).toLocaleString('en-FJ', {
          minimumFractionDigits: 2, maximumFractionDigits: 2,
        })}`
      : 'FJD 0.00';

  const statusBadge = (s) => {
    if (s === 'SUBMITTED')    return <span className="badge badge-green">Submitted to FRCS</span>;
    if (s === 'PENDING_FRCS') return <span className="badge badge-blue">Awaiting FRCS</span>;
    return                           <span className="badge badge-yellow">Draft</span>;
  };

  const filtered = rows.filter(r => {
    if (filterStatus === 'PENDING_FRCS') return r.status === 'PENDING_FRCS';
    if (filterStatus === 'SUBMITTED')    return r.status === 'SUBMITTED';
    if (filterStatus === 'DRAFT')        return !r.status || r.status === 'DRAFT';
    return true;
  });

  const counts = {
    all:     rows.length,
    pending: rows.filter(r => r.status === 'PENDING_FRCS').length,
    submitted: rows.filter(r => r.status === 'SUBMITTED').length,
    draft:   rows.filter(r => !r.status || r.status === 'DRAFT').length,
  };

  const handleExportCSV = () => {
    const rows2 = [
      ['BANK OF FIJI — TAX SUBMISSIONS'],
      [`Tax Year: ${year}`, `Generated: ${new Date().toLocaleDateString('en-FJ')}`],
      [],
      ['Customer ID', 'Name', 'TIN', 'Status', 'FRCS Reference',
       'Submission Date', 'Gross Interest', 'RIWT', 'NRWHT', 'Net Interest'],
      ...filtered.map(r => [
        r.customerId, r.fullName, r.tinNumber || 'Not Provided', r.status || 'DRAFT',
        r.frcsReference || '—', r.frcsSubmissionDate || '—',
        r.interestEarned != null ? parseFloat(r.interestEarned).toFixed(2) : '0.00',
        r.riwtWithheld   != null ? parseFloat(r.riwtWithheld).toFixed(2)   : '0.00',
        r.nrwhtWithheld  != null ? parseFloat(r.nrwhtWithheld).toFixed(2)  : '0.00',
        r.netInterestPaid != null ? parseFloat(r.netInterestPaid).toFixed(2) : '0.00',
      ]),
    ];
    const BOM = '\uFEFF';
    const csv = BOM + rows2.map(r =>
      r.map(c => `"${String(c ?? '').replace(/"/g, '""')}"`).join(',')
    ).join('\r\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `TaxSubmissions-${year}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="tax-page">

      {/* ── Confirm dialog ──────────────────────────────────────────────────── */}
      {confirmItem && (
        <ConfirmDialog
          title="Submit to FRCS?"
          message={
            `Submit ${confirmItem.name}'s ${year} tax report to FRCS?\n\n` +
            `A unique FRCS receipt number will be generated and the customer ` +
            `will receive an in-app notification and email with the reference number.`
          }
          confirmText="Submit to FRCS"
          cancelText="Cancel"
          confirmVariant="success"
          variant="info"
          onConfirm={handleSubmitToFrcs}
          onCancel={() => setConfirmItem(null)}
        />
      )}

      {/* ── Side drawer ─────────────────────────────────────────────────────── */}
      {drawerEmail && (
        <div
          style={{
            position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.45)',
            display: 'flex', justifyContent: 'flex-end', zIndex: 1000,
          }}
          onClick={closeDrawer}
        >
          <div
            style={{
              width: 540, maxWidth: '95vw', height: '100%', overflowY: 'auto',
              background: 'var(--surface, #fff)',
              boxShadow: '-8px 0 40px rgba(0,0,0,0.18)',
              padding: '28px 32px',
            }}
            onClick={e => e.stopPropagation()}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
              <h2 style={{ fontFamily: "'DM Serif Display', serif", fontWeight: 400, fontSize: 22, margin: 0 }}>
                Customer Tax Report
              </h2>
              <button
                onClick={closeDrawer}
                style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 24, color: 'var(--text-muted)' }}
              >×</button>
            </div>

            {drawerLoading && (
              <div className="tax-loading"><div className="spinner" /><p>Loading…</p></div>
            )}

            {drawerReport && !drawerLoading && (
              drawerReport._error ? (
                <div className="alert alert-error">Failed to load report. Please try again.</div>
              ) : (
                <>
                  {/* Summary rows */}
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 0, marginBottom: 24 }}>
                    {[
                      ['Customer', drawerReport.fullName],
                      ['Customer ID', drawerReport.customerId],
                      ['TIN', drawerReport.tinNumber],
                      ['Tax Year', drawerReport.taxYear],
                      ['Status', statusBadge(drawerReport.status)],
                      ['Residency', drawerReport.isResident ? 'Fiji Resident' : 'Non-Resident'],
                      ['Interest Rate', drawerReport.interestRate ?? 'Not set'],
                      ['Gross Interest', fmt(drawerReport.interestEarned)],
                      ['RIWT Withheld', fmt(drawerReport.riwtWithheld)],
                      ['NRWHT Withheld', fmt(drawerReport.nrwhtWithheld)],
                      ['Net Interest', fmt(drawerReport.netInterestPaid)],
                      ['Total Tax Owed', fmt(drawerReport.totalTaxOwed)],
                      ...(drawerReport.frcsReference
                        ? [['FRCS Reference',
                            <span key="r" style={{ fontFamily: 'monospace', color: 'var(--success)' }}>
                              {drawerReport.frcsReference}
                            </span>]]
                        : []),
                    ].map(([label, val]) => (
                      <div key={label} style={{
                        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                        padding: '10px 0', borderBottom: '1px solid var(--border, #f0f4f8)', fontSize: 14,
                      }}>
                        <span style={{ color: 'var(--text-muted)', flexShrink: 0, marginRight: 16 }}>{label}</span>
                        <span style={{ fontWeight: 600, textAlign: 'right' }}>{val}</span>
                      </div>
                    ))}
                  </div>

                  {/* Monthly breakdown mini table */}
                  {drawerReport.monthlyBreakdown?.length > 0 && (
                    <>
                      <h3 style={{ fontFamily: "'DM Serif Display', serif", fontWeight: 400, fontSize: 16, margin: '0 0 12px' }}>
                        Monthly Breakdown
                      </h3>
                      <div style={{ overflowX: 'auto', marginBottom: 20 }}>
                        <table className="tax-table" style={{ fontSize: 13 }}>
                          <thead>
                            <tr><th>Month</th><th>Interest</th><th>Tax Withheld</th></tr>
                          </thead>
                          <tbody>
                            {drawerReport.monthlyBreakdown
                              .filter(m => parseFloat(m.interest ?? 0) > 0)
                              .map(m => (
                                <tr key={m.month}>
                                  <td>{m.month}</td>
                                  <td className="amount positive-text">{fmt(m.interest)}</td>
                                  <td className="amount negative-text">
                                    {parseFloat(m.taxWithheld ?? 0) > 0
                                      ? `−${fmt(m.taxWithheld)}`
                                      : <span style={{ color: 'var(--text-muted)' }}>—</span>}
                                  </td>
                                </tr>
                              ))}
                          </tbody>
                        </table>
                      </div>
                    </>
                  )}

                  {/* Submit button inside drawer */}
                  {isAdmin && drawerReport.status === 'PENDING_FRCS' && (
                    <button
                      className="btn-primary"
                      style={{ width: '100%', padding: '12px 0', fontSize: 14 }}
                      onClick={() => {
                        closeDrawer();
                        setConfirmItem({ userEmail: drawerEmail, name: drawerReport.fullName });
                      }}
                    >
                      Submit to FRCS →
                    </button>
                  )}

                  {drawerReport.status === 'SUBMITTED' && drawerReport.frcsReference && (
                    <div style={{
                      background: 'rgba(16,185,129,0.07)', border: '1px solid rgba(16,185,129,0.25)',
                      borderRadius: 8, padding: '14px 16px', fontSize: 13,
                    }}>
                      ✅ Submitted to FRCS — Ref: <strong style={{ fontFamily: 'monospace' }}>{drawerReport.frcsReference}</strong>
                    </div>
                  )}
                </>
              )
            )}
          </div>
        </div>
      )}

      {/* ── Page header ──────────────────────────────────────────────────────── */}
      <div className="tax-header">
        <div className="tax-header-left">
          <div className="tax-badge">Admin</div>
          <div>
            <h1 className="tax-title">Tax Submissions — {year}</h1>
            <p className="tax-subtitle">Review and submit customer tax reports to FRCS</p>
          </div>
        </div>
        <div className="tax-header-right no-print" style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <select className="year-select" value={year} onChange={e => setYear(parseInt(e.target.value))}>
            {yearOptions.map(y => <option key={y} value={y}>{y} Tax Year</option>)}
          </select>
          <button className="btn-outline" onClick={handleExportCSV} disabled={rows.length === 0}>
            ↓ Export CSV
          </button>
          <button className="btn-outline" onClick={fetchSubmissions} disabled={loading}>
            {loading ? '↻ Loading…' : '↻ Refresh'}
          </button>
        </div>
      </div>

      {error && <div className="alert alert-error">⚠️ {error}</div>}

      {/* ── Success banner — shown inline, page stays put ──────────────────── */}
      {successEmail && successRef && (
        <div style={{
          background: 'rgba(16,185,129,0.08)', border: '1px solid rgba(16,185,129,0.28)',
          borderRadius: 8, padding: '16px 20px', marginBottom: 20,
          display: 'flex', alignItems: 'flex-start', gap: 14,
        }}>
          <span style={{ fontSize: 26, flexShrink: 0 }}>✅</span>
          <div>
            <strong style={{ fontSize: 15 }}>
              {rows.find(r => r.userEmail === successEmail)?.fullName ?? successEmail} — submitted to FRCS
            </strong>
            <div style={{ fontSize: 13, color: 'var(--text-muted)', marginTop: 4 }}>
              FRCS Reference:{' '}
              <code style={{ background: 'rgba(0,0,0,0.05)', padding: '2px 6px', borderRadius: 4 }}>
                {successRef}
              </code>
              {' '}· Customer has been notified by in-app notification and email.
            </div>
            <button
              style={{
                marginTop: 8, background: 'none', border: 'none',
                cursor: 'pointer', fontSize: 12, color: 'var(--text-muted)',
                textDecoration: 'underline', padding: 0,
              }}
              onClick={() => { setSuccessEmail(null); setSuccessRef(''); }}
            >
              Dismiss
            </button>
          </div>
        </div>
      )}

      {/* ── Summary cards ────────────────────────────────────────────────────── */}
      <div className="summary-grid">
        <div className="summary-card blue">
          <span className="summary-label">Awaiting FRCS</span>
          <span className="summary-amount">{counts.pending}</span>
          <span className="summary-sub">Submitted by customer, action needed</span>
        </div>
        <div className="summary-card green">
          <span className="summary-label">Submitted to FRCS</span>
          <span className="summary-amount">{counts.submitted}</span>
          <span className="summary-sub">Receipt number generated</span>
        </div>
        <div className="summary-card red">
          <span className="summary-label">Draft</span>
          <span className="summary-amount">{counts.draft}</span>
          <span className="summary-sub">Not yet submitted by customer</span>
        </div>
        <div className="summary-card purple">
          <span className="summary-label">Total</span>
          <span className="summary-amount">{counts.all}</span>
          <span className="summary-sub">All customers for {year}</span>
        </div>
      </div>

      {/* ── Filter bar ───────────────────────────────────────────────────────── */}
      <div className="tax-card" style={{ padding: '14px 20px' }}>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
          <span style={{ fontSize: 13, color: 'var(--text-muted)', marginRight: 4 }}>Show:</span>
          {[
            { key: 'ALL',          label: `All (${counts.all})` },
            { key: 'PENDING_FRCS', label: `Awaiting FRCS (${counts.pending})` },
            { key: 'SUBMITTED',    label: `Submitted (${counts.submitted})` },
            { key: 'DRAFT',        label: `Draft (${counts.draft})` },
          ].map(f => (
            <button
              key={f.key}
              onClick={() => setFilterStatus(f.key)}
              style={{
                padding: '5px 14px', borderRadius: 20, fontSize: 12, fontWeight: 600,
                cursor: 'pointer', transition: 'all 0.15s',
                background: filterStatus === f.key ? 'var(--navy, #0f2044)' : 'var(--bg-secondary)',
                color: filterStatus === f.key ? '#fff' : 'var(--text-secondary)',
                border: '1px solid var(--border)',
              }}
            >
              {f.label}
            </button>
          ))}
        </div>
      </div>

      {/* ── Table ────────────────────────────────────────────────────────────── */}
      {loading ? (
        <div className="tax-loading"><div className="spinner" /><p>Loading submissions…</p></div>
      ) : (
        <div className="tax-card">
          <h2 className="card-title">Customer Tax Reports — {year}</h2>

          {filtered.length === 0 ? (
            <p style={{ color: 'var(--text-muted)', textAlign: 'center', padding: '40px 0' }}>
              {filterStatus === 'PENDING_FRCS'
                ? `No reports are awaiting FRCS submission for ${year}.`
                : `No ${filterStatus !== 'ALL' ? filterStatus.toLowerCase() + ' ' : ''}submissions found for ${year}.`}
            </p>
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table className="tax-table">
                <thead>
                  <tr>
                    <th>Customer</th>
                    <th>TIN</th>
                    <th>Status</th>
                    <th>Gross Interest</th>
                    <th>Tax Withheld</th>
                    <th>FRCS Reference</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(r => {
                    const withheld = (
                      parseFloat(r.riwtWithheld ?? 0) +
                      parseFloat(r.nrwhtWithheld ?? 0)
                    ).toFixed(2);
                    const isSending = submittingEmail === r.userEmail;
                    const justDone  = successEmail === r.userEmail;

                    return (
                      <tr
                        key={r.userEmail}
                        style={justDone ? { background: 'rgba(16,185,129,0.04)' } : {}}
                      >
                        <td>
                          <div style={{ fontWeight: 600, fontSize: 14 }}>{r.fullName}</div>
                          <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                            {r.customerId}
                          </div>
                        </td>
                        <td>
                          {r.tinNumber
                            ? <span style={{ fontFamily: 'monospace', fontSize: 13 }}>{r.tinNumber}</span>
                            : <span className="badge badge-yellow">No TIN</span>}
                        </td>
                        <td>{statusBadge(r.status)}</td>
                        <td className="amount">{fmt(r.interestEarned)}</td>
                        <td className="amount negative-text">
                          {parseFloat(withheld) > 0 ? `−FJD ${withheld}` : 'FJD 0.00'}
                        </td>
                        <td>
                          {r.frcsReference ? (
                            <span style={{ fontFamily: 'monospace', fontSize: 12, color: 'var(--success)' }}>
                              {r.frcsReference}
                            </span>
                          ) : (
                            <span style={{ color: 'var(--text-muted)', fontSize: 12 }}>—</span>
                          )}
                        </td>
                        <td>
                          <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                            <button
                              className="btn-ghost btn-sm"
                              style={{ fontSize: 12 }}
                              onClick={() => openDrawer(r.userEmail)}
                            >
                              View
                            </button>

                            {isAdmin && r.status === 'PENDING_FRCS' && (
                              <button
                                className="btn-primary"
                                style={{ fontSize: 12, padding: '5px 12px', minWidth: 120 }}
                                disabled={isSending}
                                onClick={() => setConfirmItem({ userEmail: r.userEmail, name: r.fullName })}
                              >
                                {isSending ? '…' : 'Submit to FRCS'}
                              </button>
                            )}

                            {r.status === 'SUBMITTED' && (
                              <span style={{ fontSize: 11, color: 'var(--success)', fontWeight: 600 }}>
                                ✓ Done
                              </span>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      <p className="tax-disclaimer">
        * Submitting to FRCS generates a permanent receipt and immediately notifies the customer.
        This action cannot be undone. Teller access is view-only; submit requires Admin role.
      </p>
    </div>
  );
};

export default AdminTaxReport;