import React, { useState, useEffect } from 'react';
import api from '../services/api';
import './TaxReport.css';
import './ManageInterestRate.css';

/**
 * InterestRateManager — Teller / Admin only.
 *
 * Default rate: 3.5% p.a. seeded by DataSeeder on fresh install.
 * Tellers can add new rates from today onwards at any time.
 * No backdating is permitted — rates can only take effect today or in the future.
 *
 * Backend key mapping (GET /api/interest-rates):
 *   data.currentAnnualRatePct      → annual rate as percent  e.g. 3.5000
 *   data.currentAnnualRate         → annual rate as decimal  e.g. 0.035000
 *   data.currentDailyRatePercent   → daily rate as percent   e.g. 0.009589
 *   data.effectiveSince            → date string             e.g. "2026-01-01"
 *   data.hasRate                   → boolean
 */
const InterestRateManager = () => {
  const [data, setData]               = useState(null);
  const [loading, setLoading]         = useState(true);
  const [error, setError]             = useState('');
  const [success, setSuccess]         = useState('');
  const [submitting, setSubmitting]   = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);

  const today   = new Date().toISOString().split('T')[0];
  const maxDate = new Date(new Date().setFullYear(new Date().getFullYear() + 1))
    .toISOString().split('T')[0];

  const [form, setForm] = useState({
    annualRatePercent: '',
    effectiveFrom: today,
    changeReason: '',
  });

  const [preview, setPreview] = useState(null);

  useEffect(() => { fetchRates(); }, []);

  useEffect(() => {
    const pct = parseFloat(form.annualRatePercent);
    if (!isNaN(pct) && pct > 0) {
      const annual = pct / 100;
      const daily  = annual / 365;
      setPreview({
        annual:      pct.toFixed(4),
        daily:       (daily * 100).toFixed(6),
        example100:  (100  * annual / 365).toFixed(4),
        example1000: (1000 * annual / 365).toFixed(4),
      });
    } else {
      setPreview(null);
    }
  }, [form.annualRatePercent]);

  // ── API calls ───────────────────────────────────────────────────────────────

  const fetchRates = async () => {
    setLoading(true); setError('');
    try {
      const res = await api.get('/interest-rates');
      setData(res.data);
    } catch (err) {
      setError(
        err.response?.status === 403
          ? 'Access denied. This page requires TELLER or ADMIN role.'
          : 'Failed to load interest rates.'
      );
    } finally {
      setLoading(false);
    }
  };

  const handleConfirmOpen = () => {
    setError(''); setSuccess('');
    const pct = parseFloat(form.annualRatePercent);
    if (isNaN(pct) || pct <= 0) { setError('Please enter a valid annual rate greater than 0%.'); return; }
    if (pct > 25) {
      setError('Annual rate cannot exceed 25% per RBF guidelines.'); return;
    }
    if (pct <= 0) {
      setError('Annual rate must be greater than 0%.'); return;
    }
    if (!form.effectiveFrom)     { setError('Effective date is required.'); return; }
    if (form.effectiveFrom < today) {
      setError('Backdating is not allowed. Effective date must be today or a future date.');
      return;
    }
    setConfirmOpen(true);
  };

  const handleDateChange = (e) => {
    const chosen = e.target.value;
    if (chosen < today) {
      setError('Backdating is not allowed. Please select today or a future date.');
      setForm(f => ({ ...f, effectiveFrom: today }));
    } else {
      setError('');
      setForm(f => ({ ...f, effectiveFrom: chosen }));
    }
  };

  const handleConfirmedSubmit = async () => {
    setConfirmOpen(false);
    setSubmitting(true);
    const pct = parseFloat(form.annualRatePercent);
    try {
      await api.post('/interest-rates', {
        annualRate:    pct / 100,
        effectiveFrom: form.effectiveFrom,
        changeReason:  form.changeReason || null,
      });
      setSuccess(
        form.effectiveFrom === today
          ? `Rate of ${pct}% p.a. is now active. All savings customers have been notified.`
          : `Rate of ${pct}% p.a. scheduled for ${form.effectiveFrom}. Customers will be notified.`
      );
      setForm({ annualRatePercent: '', effectiveFrom: today, changeReason: '' });
      await fetchRates();
    } catch (err) {
      if (err.response?.status === 409 || err.response?.status === 400) {
        setError(err.response?.data?.message || 'Invalid rate or duplicate effective date.');
      } else {
        setError('Failed to set interest rate. Please try again.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  // ── Helpers ─────────────────────────────────────────────────────────────────

  const statusBadge = (status) => {
    const map = { ACTIVE: 'badge-green', SCHEDULED: 'badge-blue', SUPERSEDED: 'badge-yellow' };
    return <span className={`badge ${map[status] ?? 'badge-yellow'}`}>{status}</span>;
  };

  const fmtRate = (val) => val != null ? `${parseFloat(val).toFixed(4)}%` : '—';

  const isScheduled   = form.effectiveFrom > today;
  const currentRatePct = data?.currentAnnualRatePct
    ? parseFloat(data.currentAnnualRatePct).toFixed(4) + '%'
    : 'None set';
  const newPct = parseFloat(form.annualRatePercent || 0).toFixed(4) + '%';

  // ── Render ───────────────────────────────────────────────────────────────────

  return (
    <div className="tax-page manage-interest-rate-page">

      {/* ── Confirmation modal ──────────────────────────────────────────────── */}
      {confirmOpen && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.55)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000,
        }}>
          <div style={{
            background: 'var(--surface, #ffffff)', border: '1px solid var(--border, #d9e2ec)',
            borderRadius: 'var(--radius, 12px)', padding: 32, width: 500, maxWidth: '92vw',
            color: 'var(--text-primary, #1f2937)',
            boxShadow: '0 20px 60px rgba(0,0,0,0.25)',
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 20 }}>
              <div style={{
                width: 42, height: 42, borderRadius: '50%',
                background: 'rgba(239,68,68,0.12)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: 20, flexShrink: 0,
              }}>!</div>
              <div>
                <h3 style={{ fontFamily: "'DM Serif Display', serif", fontWeight: 400, fontSize: 20, margin: 0 }}>
                  Confirm interest rate change
                </h3>
                <p style={{ fontSize: 13, color: 'var(--text-muted)', margin: '2px 0 0' }}>
                  This will affect all savings account customers.
                </p>
              </div>
            </div>

            {/* Change summary */}
            <div style={{
              background: 'var(--bg-secondary, #f8fafc)', border: '1px solid var(--border, #d9e2ec)',
              borderRadius: 8, padding: '14px 18px', marginBottom: 18,
            }}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontSize: 13, color: 'var(--text-muted, #64748b)' }}>Current rate</span>
                  <span style={{ fontSize: 15, fontWeight: 600, color: 'var(--text-secondary, #334155)' }}>
                    {currentRatePct}
                  </span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontSize: 13, color: 'var(--text-muted, #64748b)' }}>New rate</span>
                  <span style={{ fontSize: 18, fontWeight: 700, color: 'var(--navy, #0f2d55)' }}>
                    {newPct} p.a.
                  </span>
                </div>
                <div style={{ height: 1, background: 'var(--border, #d9e2ec)', margin: '2px 0' }} />
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontSize: 13, color: 'var(--text-muted, #64748b)' }}>Effective from</span>
                  <span style={{ fontSize: 14, fontWeight: 600 }}>
                    {form.effectiveFrom}
                    {!isScheduled && <span style={{ marginLeft: 8, color: 'var(--success)', fontSize: 12 }}>Today</span>}
                    {isScheduled  && <span style={{ marginLeft: 8, color: 'var(--info, #3b82f6)', fontSize: 12 }}>Scheduled</span>}
                  </span>
                </div>
                {form.changeReason && (
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16 }}>
                    <span style={{ fontSize: 13, color: 'var(--text-muted, #64748b)', flexShrink: 0 }}>Reason</span>
                    <span style={{ fontSize: 13, color: 'var(--text-secondary, #334155)', textAlign: 'right' }}>
                      {form.changeReason}
                    </span>
                  </div>
                )}
              </div>
            </div>

            {/* Impact notice */}
            <div className="interest-notice" style={{ marginBottom: 18 }}>
              <span className="notice-icon">i</span>
              <div style={{ fontSize: 13, lineHeight: 1.55 }}>
                {isScheduled
                  ? <>This rate is <strong>pre-scheduled</strong> for {form.effectiveFrom}. All savings customers will be notified immediately of the upcoming change.</>
                  : <>This rate takes effect <strong>today</strong>. All savings customers will be notified immediately.</>}
              </div>
            </div>

            {/* Compliance reminder */}
            <div style={{
              background: 'rgba(239,68,68,0.06)', border: '1px solid rgba(239,68,68,0.20)',
              borderRadius: 8, padding: '10px 14px', marginBottom: 22,
              fontSize: 13, color: 'var(--text-secondary)',
            }}>
              <strong>RBF compliance:</strong> Ensure this rate change is authorised by an official RBF
              directive. All changes are permanently recorded with your name and timestamp and cannot be deleted.
            </div>

            <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end' }}>
              <button className="btn-outline" onClick={() => setConfirmOpen(false)} style={{ minWidth: 100 }}>
                Cancel
              </button>
              <button className="btn-primary" onClick={handleConfirmedSubmit} style={{ minWidth: 160 }}>
                ✓ Confirm &amp; Set Rate
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Page header ─────────────────────────────────────────────────────── */}
      <div className="tax-header">
        <div className="tax-header-left">
          <div className="tax-badge">RBF</div>
          <div>
            <h1 className="tax-title">Savings Interest Rate</h1>
            <p className="tax-subtitle">Reserve Bank of Fiji — variable rate for all savings accounts</p>
          </div>
        </div>
      </div>

      {error && (
        <div className="alert alert-error" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span className="notice-icon">!</span>
          <span>{error}</span>
        </div>
      )}
      {success && (
        <div className="alert alert-success" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span className="notice-icon">✓</span>
          <span>{success}</span>
        </div>
      )}

      {loading ? (
        <div className="tax-loading"><div className="spinner" /><p>Loading interest rates…</p></div>
      ) : (
        <>
          {/* Default rate notice */}
          {data?.hasRate && data?.history?.length === 1 &&
           data.history[0]?.setBy === 'system' && (
            <div className="alert" style={{
              background: 'rgba(59,130,246,0.07)',
              border: '1px solid rgba(59,130,246,0.22)',
              borderRadius: 8, padding: '11px 16px', marginBottom: 18,
              fontSize: 13, color: '#1e3a5f',
              display: 'flex', alignItems: 'center', gap: 8,
            }}>
              <span className="notice-icon">i</span>
              <span>
                The system default rate of <strong>3.5% p.a.</strong> is currently active.
                You can update this at any time using the form below.
              </span>
            </div>
          )}

          {!data?.hasRate && (
            <div className="alert alert-error" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span className="notice-icon">!</span>
              <span>No interest rate configured. Set one below to enable monthly interest credits.</span>
            </div>
          )}

          {/* Summary cards */}
          <div className="summary-grid">
            <div className="summary-card green">
              <span className="summary-label">Current Annual Rate</span>
              <span className="summary-amount">
                {data?.hasRate ? fmtRate(data.currentAnnualRatePct) : 'Not set'}
              </span>
              <span className="summary-sub">
                {data?.effectiveSince ? `Effective since ${data.effectiveSince}` : 'No rate configured'}
              </span>
            </div>
            <div className="summary-card blue">
              <span className="summary-label">Daily Rate</span>
              <span className="summary-amount">
                {data?.hasRate ? fmtRate(data.currentDailyRatePercent) : '—'}
              </span>
              <span className="summary-sub">Annual ÷ 365 days</span>
            </div>
            <div className="summary-card purple">
              <span className="summary-label">Daily Interest on FJD 1,000</span>
              <span className="summary-amount">
                {data?.hasRate && data.currentAnnualRate
                  ? `FJD ${(1000 * parseFloat(data.currentAnnualRate) / 365).toFixed(4)}`
                  : '—'}
              </span>
              <span className="summary-sub">Example: FJD 1,000 balance</span>
            </div>
            <div className="summary-card red">
              <span className="summary-label">Scheduled Changes</span>
              <span className="summary-amount">
                {data?.history?.filter(r => r.status === 'SCHEDULED').length ?? 0}
              </span>
              <span className="summary-sub">Future-dated rates pending</span>
            </div>
          </div>

          {/* Set new rate form */}
          <div className="tax-card">
            <h2 className="card-title">Set New Interest Rate</h2>
            <p style={{ color: 'var(--text-muted)', marginBottom: 20, fontSize: 14, lineHeight: 1.6 }}>
              Enter the RBF-mandated rate and when it takes effect.
              The effective date must be <strong>today or a future date</strong> — backdating is not permitted.
              A confirmation dialog will appear before saving.
            </p>

            <div className="interest-notice" style={{ marginBottom: 20 }}>
              <span className="notice-icon">!</span>
              <div style={{ fontSize: 13 }}>
                <strong>No backdating.</strong> Rates can only take effect from today onwards.
                Past dates are disabled in the date picker and rejected by the system.
              </div>
            </div>

            <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginBottom: 16 }}>
              {/* Rate input */}
              <div style={{ flex: '1 1 160px' }}>
                <label style={labelStyle}>Annual Rate (%)</label>
                <div style={{ position: 'relative' }}>
                  <input
                    type="number" step="0.05" min="0.05" max="25"
                    placeholder="e.g. 3.5"
                    value={form.annualRatePercent}
                    onChange={e => setForm(f => ({ ...f, annualRatePercent: e.target.value }))}
                    style={inputStyle}
                  />
                  <span style={{
                    position: 'absolute', right: 12, top: '50%', transform: 'translateY(-50%)',
                    color: 'var(--text-muted)', fontSize: 14, pointerEvents: 'none',
                  }}>%</span>
                </div>
              </div>

              {/* Effective date */}
              <div style={{ flex: '1 1 180px' }}>
                <label style={labelStyle}>Effective From</label>
                <input
                  type="date"
                  value={form.effectiveFrom}
                  min={today}
                  max={maxDate}
                  onChange={handleDateChange}
                  style={inputStyle}
                />
                <span style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 4, display: 'block' }}>
                  {form.effectiveFrom === today
                    ? '● Takes effect today'
                    : `⧗ Pre-scheduled for ${form.effectiveFrom}`}
                </span>
              </div>

              {/* Reason */}
              <div style={{ flex: '2 1 240px' }}>
                <label style={labelStyle}>Reason / RBF Reference (optional)</label>
                <input
                  type="text"
                  placeholder="e.g. RBF directive Q2 2026 — ref RBF/2026/002"
                  value={form.changeReason}
                  onChange={e => setForm(f => ({ ...f, changeReason: e.target.value }))}
                  style={inputStyle}
                />
              </div>
            </div>

            {/* Live preview */}
            {preview && (
              <div style={{
                background: 'var(--bg-secondary)', border: '1px solid var(--border)',
                borderRadius: 8, padding: '12px 16px', marginBottom: 16,
                fontSize: 13, color: 'var(--text-muted)', display: 'flex', gap: 32, flexWrap: 'wrap',
              }}>
                <span>Annual: <strong style={{ color: 'var(--text-primary)' }}>{preview.annual}%</strong></span>
                <span>Daily: <strong style={{ color: 'var(--text-primary)' }}>{preview.daily}%</strong></span>
                <span>On FJD 100: <strong style={{ color: 'var(--success)' }}>FJD {preview.example100}/day</strong></span>
                <span>On FJD 1,000: <strong style={{ color: 'var(--success)' }}>FJD {preview.example1000}/day</strong></span>
              </div>
            )}

            <button
              className="btn-primary"
              onClick={handleConfirmOpen}
              disabled={submitting || !form.annualRatePercent || !form.effectiveFrom}
            >
              {submitting ? 'Saving…' : 'Set Interest Rate'}
            </button>
          </div>

          {/* Rate history */}
          <div className="tax-card">
            <h2 className="card-title">Rate History</h2>
            <p style={{ color: 'var(--text-muted)', fontSize: 13, marginBottom: 16 }}>
              The active rate on any given day is the most recent entry whose "Effective From"
              is on or before that day. All past rates are kept permanently for RBF audit purposes
              and cannot be edited or deleted.
            </p>

            {!data?.history?.length ? (
              <p style={{ color: 'var(--text-muted)', padding: '32px 0', textAlign: 'center' }}>
                No rates have been set yet.
              </p>
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table className="tax-table">
                  <thead>
                    <tr>
                      <th>Status</th>
                      <th>Annual Rate</th>
                      <th>Daily Rate</th>
                      <th>Effective From</th>
                      <th>Effective To</th>
                      <th>Set By</th>
                      <th>Reason / Reference</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.history.map(r => (
                      <tr key={r.id} style={
                        r.status === 'ACTIVE'
                          ? { background: 'var(--success-bg, rgba(16,185,129,0.05))' }
                          : r.status === 'SCHEDULED'
                          ? { background: 'var(--info-bg, rgba(59,130,246,0.05))' }
                          : {}
                      }>
                        <td>{statusBadge(r.status)}</td>
                        <td><strong style={{ fontSize: 15 }}>{fmtRate(r.annualRatePercent)}</strong></td>
                        <td style={{ color: 'var(--text-muted)', fontSize: 13 }}>
                          {fmtRate(r.dailyRate ? (parseFloat(r.dailyRate) * 100).toFixed(6) : null)}
                        </td>
                        <td>{r.effectiveFrom}</td>
                        <td style={{ color: 'var(--text-muted)' }}>
                          {r.effectiveTo ?? (r.status === 'ACTIVE' ? 'Present' : 'Future')}
                        </td>
                        <td style={{ fontSize: 13 }}>
                          {r.setBy === 'system'
                            ? <span style={{ color: 'var(--text-muted)', fontStyle: 'italic' }}>System default</span>
                            : r.setBy}
                        </td>
                        <td style={{ fontSize: 13, color: 'var(--text-muted)' }}>
                          {r.changeReason || <em>—</em>}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* How it works */}
          <div className="tax-card">
            <h2 className="card-title">How variable rates work</h2>
            <div className="income-grid">
              {[
                ['Default rate is 3.5% p.a.',             'Set automatically on system install. Update it here at any time.'],
                ['New rates take effect from today onwards', 'Backdating is not permitted — all changes are forward-looking.'],
                ['Interest credited on the 1st of each month', 'The scheduler runs at 01:00 Fiji time on the 1st of every month.'],
                ['Customers notified when a rate is set',  'In-app notification sent to all savings account holders immediately.'],
                ['Tax report uses credited interest only', 'Only amounts posted as INTEREST transactions are reported to FRCS.'],
                ['Past rates are never deleted',           'Full audit trail maintained for RBF and FRCS compliance.'],
              ].map(([title, sub]) => (
                <div className="income-row" key={title}>
                  <span>{title}</span>
                  <span style={{ color: 'var(--text-muted)', fontSize: 13 }}>{sub}</span>
                </div>
              ))}
            </div>
          </div>

          <p className="tax-disclaimer">
            * Interest rates are set per Reserve Bank of Fiji directives. All rate changes are permanently
            audited with the setting user's name and timestamp. No backdating is permitted.
            Contact the RBF or a compliance officer before changing rates outside of official directives.
            {' '}<a href="mailto:complaints@rbf.gov.fj">complaints@rbf.gov.fj</a> or call (679) 331 3611.
          </p>
        </>
      )}
    </div>
  );
};

const labelStyle = {
  display: 'block', marginBottom: 6,
  color: 'var(--text-muted)', fontSize: 13, fontWeight: 500,
};

const inputStyle = {
  width: '100%', padding: '10px 14px',
  border: '1px solid var(--border)', borderRadius: 8,
  background: 'var(--bg-secondary)', color: 'var(--text-primary)',
  fontSize: 15, boxSizing: 'border-box',
};

export default InterestRateManager;