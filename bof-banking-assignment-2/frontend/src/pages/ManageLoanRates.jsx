import React, { useState, useEffect } from 'react';
import api from '../services/api';
import './TaxReport.css';
import './LoanApplication.css';

/**
 * LoanRateManager — Admin / Teller only.
 *
 * Manages the default interest rates for each Bank of Fiji loan product.
 * Rates set here are automatically applied to all new loan applications.
 * Existing active loans are NOT retroactively changed.
 *
 * Loan products:
 *   Personal Loan  — default 8.5% p.a.
 *   Home Loan      — default 6.5% p.a.
 *   Vehicle Loan   — default 7.5% p.a.
 *   Business Loan  — default 9.0% p.a.
 */

const LOAN_PRODUCTS = [
  {
    type:    'Personal Loan',
    icon:    'personal',
    desc:    'Short-to-medium term personal lending',
    default: 8.5,
    color:   '#3b82f6',
    bg:      'rgba(59,130,246,0.08)',
  },
  {
    type:    'Home Loan',
    icon:    'home',
    desc:    'Residential property purchase or construction',
    default: 6.5,
    color:   '#10b981',
    bg:      'rgba(16,185,129,0.08)',
  },
  {
    type:    'Vehicle Loan',
    icon:    'vehicle',
    desc:    'New or used vehicle financing',
    default: 7.5,
    color:   '#f59e0b',
    bg:      'rgba(245,158,11,0.08)',
  },
  {
    type:    'Business Loan',
    icon:    'business',
    desc:    'Business growth and working capital',
    default: 9.0,
    color:   '#8b5cf6',
    bg:      'rgba(139,92,246,0.08)',
  },
];

const renderLoanIcon = (type, color) => {
  const iconMap = {
    personal: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
        <circle cx="12" cy="7" r="4"></circle>
      </svg>
    ),
    home: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"></path>
        <polyline points="9 22 9 12 15 12 15 22"></polyline>
      </svg>
    ),
    vehicle: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="5" cy="17" r="2"></circle>
        <circle cx="19" cy="17" r="2"></circle>
        <path d="M1 11h22l-2 6H3l-2-6z"></path>
        <path d="M6 7h12v4H6z"></path>
      </svg>
    ),
    business: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="4" width="18" height="16" rx="2" ry="2"></rect>
        <path d="M16 2v4"></path>
        <path d="M8 2v4"></path>
        <line x1="3" y1="10" x2="21" y2="10"></line>
      </svg>
    ),
  };
  
  return (
    <svg className="loan-icon-svg" style={{ color }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      {iconMap[type]?.props?.children}
    </svg>
  );
};

export default function LoanRateManager() {
  const [rates, setRates]       = useState([]);
  const [loading, setLoading]   = useState(true);
  const [error, setError]       = useState('');
  const [success, setSuccess]   = useState('');

  // One edit state per loan type
  const [editing, setEditing]       = useState({});   // { loanType: bool }
  const [rateInput, setRateInput]   = useState({});   // { loanType: string }
  const [reasonInput, setReasonInput] = useState({}); // { loanType: string }
  const [saving, setSaving]         = useState({});   // { loanType: bool }

  // Confirm modal
  const [confirmModal, setConfirmModal] = useState(null);

  useEffect(() => { fetchRates(); }, []);

  const fetchRates = async () => {
    setLoading(true); setError('');
    try {
      const res = await api.get('/loan-rates');
      setRates(res.data ?? []);
    } catch (err) {
      setError(err?.response?.status === 403
        ? 'Access denied. This page requires TELLER or ADMIN role.'
        : 'Failed to load loan rates.');
    } finally {
      setLoading(false);
    }
  };

  const getRateForType = (loanType) =>
    rates.find(r => r.loanType === loanType);

  const openEdit = (loanType) => {
    const current = getRateForType(loanType);
    const pct = current
      ? parseFloat(current.annualRatePct).toFixed(2)
      : LOAN_PRODUCTS.find(p => p.type === loanType)?.default.toFixed(2);
    setRateInput(prev  => ({ ...prev,  [loanType]: pct ?? '' }));
    setReasonInput(prev => ({ ...prev, [loanType]: '' }));
    setEditing(prev    => ({ ...prev,  [loanType]: true }));
    setError(''); setSuccess('');
  };

  const cancelEdit = (loanType) => {
    setEditing(prev => ({ ...prev, [loanType]: false }));
  };

  const handleSave = (loanType) => {
    const pct = parseFloat(rateInput[loanType]);
    if (isNaN(pct) || pct < 0.5) {
      setError('Please enter a valid rate greater than 0.5%.'); return;
    }
    if (pct > 30) {
      setError('Rate cannot exceed 30%.'); return;
    }
    setError(''); setSuccess('');
    setConfirmModal({
      loanType,
      newRatePct: pct,
      reason: reasonInput[loanType] || null,
    });
  };

  const handleConfirmedSave = async () => {
    const { loanType, newRatePct, reason } = confirmModal;
    setConfirmModal(null);
    setSaving(prev => ({ ...prev, [loanType]: true }));
    try {
      // URL-encode the loan type (e.g. "Personal Loan" → "Personal%20Loan")
      const encoded = encodeURIComponent(loanType);
      await api.patch(`/loan-rates/${encoded}`, {
        annualRate:    newRatePct / 100,
        changeReason:  reason || null,
      });
      setSuccess(`${loanType} rate updated to ${newRatePct}% p.a.`);
      setEditing(prev => ({ ...prev, [loanType]: false }));
      await fetchRates();
    } catch (err) {
      setError(err?.response?.data?.message ?? 'Failed to update rate. Please try again.');
    } finally {
      setSaving(prev => ({ ...prev, [loanType]: false }));
    }
  };

  // Live monthly payment preview for a given rate + example amount
  const previewMonthly = (ratePct, principal = 10000, termMonths = 36) => {
    const r = (ratePct / 100) / 12;
    if (!r) return '—';
    const M = (principal * r * Math.pow(1 + r, termMonths)) /
              (Math.pow(1 + r, termMonths) - 1);
    return `FJD ${M.toFixed(2)}`;
  };

  const fmtRate = (pct) =>
    pct != null ? `${parseFloat(pct).toFixed(4)}%` : '—';

  return (
    <div className="tax-page loan-rates-page">

      {/* ── Confirm modal ─────────────────────────────────────────────── */}
      {confirmModal && (
        <div 
          onClick={(e) => {
            if (e.target === e.currentTarget) setConfirmModal(null);
          }}
          style={{
            position: 'fixed', 
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            background: 'rgba(0,0,0,.6)',
            display: 'flex', 
            alignItems: 'center', 
            justifyContent: 'center',
            zIndex: 9999,
            padding: 16,
            backdropFilter: 'blur(2px)',
          }}
        >
          <div 
            onClick={(e) => e.stopPropagation()}
            style={{
              background: '#ffffff', 
              borderRadius: 12,
              padding: 32, 
              width: 480, 
              maxWidth: '95vw',
              maxHeight: '90vh',
              overflowY: 'auto',
              border: '1px solid #e5e7eb',
              boxShadow: '0 25px 50px -12px rgba(0,0,0,.35)',
              position: 'relative',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'flex-start', gap: 14, marginBottom: 24 }}>
              <div style={{
                width: 44, 
                height: 44, 
                borderRadius: '50%',
                background: 'rgba(245,158,11,0.15)',
                display: 'flex', 
                alignItems: 'center', 
                justifyContent: 'center', 
                fontSize: 22,
                color: '#f59e0b',
                fontWeight: 700,
                flexShrink: 0,
              }}>!</div>
              <div style={{ flex: 1 }}>
                <h3 style={{ 
                  fontFamily: "\"Sora\", \"Manrope\", \"Segoe UI\", sans-serif", 
                  fontWeight: 700, 
                  fontSize: 20, 
                  margin: 0,
                  color: '#111827',
                  lineHeight: 1.3,
                }}>
                  Confirm rate change
                </h3>
                <p style={{ 
                  fontSize: 14, 
                  color: '#6b7280', 
                  margin: '6px 0 0',
                  lineHeight: 1.5,
                }}>
                  All new {confirmModal.loanType} applications will use this rate.
                </p>
              </div>
            </div>

            <div style={{
              background: '#f9fafb', 
              border: '1px solid #e5e7eb',
              borderRadius: 10, 
              padding: '18px 20px', 
              marginBottom: 20,
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
                <span style={{ fontSize: 13, color: '#6b7280', fontWeight: 500 }}>Loan type</span>
                <span style={{ fontWeight: 600, color: '#111827' }}>{confirmModal.loanType}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
                <span style={{ fontSize: 13, color: '#6b7280', fontWeight: 500 }}>Current rate</span>
                <span style={{ fontWeight: 600, color: '#6b7280' }}>
                  {fmtRate(getRateForType(confirmModal.loanType)?.annualRatePct)}
                </span>
              </div>
              <div style={{ 
                display: 'flex', 
                justifyContent: 'space-between', 
                alignItems: 'center',
                paddingTop: 12,
                borderTop: '2px solid #e5e7eb',
              }}>
                <span style={{ fontSize: 14, color: '#374151', fontWeight: 600 }}>New rate</span>
                <span style={{ 
                  fontSize: 22, 
                  fontWeight: 700, 
                  color: '#0f2d55',
                  letterSpacing: '-0.02em',
                }}>
                  {confirmModal.newRatePct.toFixed(2)}% <span style={{ fontSize: 14, color: '#6b7280' }}>p.a.</span>
                </span>
              </div>
              {confirmModal.reason && (
                <div style={{ 
                  marginTop: 14, 
                  borderTop: '1px solid #e5e7eb', 
                  paddingTop: 12,
                }}>
                  <span style={{ fontSize: 13, color: '#6b7280', fontWeight: 500 }}>Reason: </span>
                  <span style={{ fontSize: 13, color: '#111827' }}>{confirmModal.reason}</span>
                </div>
              )}
            </div>

            <div style={{
              background: 'rgba(239,68,68,0.08)', 
              border: '1px solid rgba(239,68,68,0.25)',
              borderRadius: 8, 
              padding: '12px 16px', 
              marginBottom: 24,
              fontSize: 13, 
              color: '#374151',
              lineHeight: 1.6,
            }}>
              <strong style={{ color: '#b91c1c' }}>Important:</strong> This only affects <em>new</em> applications. Active loans
              already disbursed are not changed. All changes are permanently logged.
            </div>

            <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end' }}>
              <button 
                className="btn-outline" 
                onClick={() => setConfirmModal(null)}
                style={{
                  padding: '10px 20px',
                  fontSize: 14,
                  fontWeight: 600,
                }}
              >
                Cancel
              </button>
              <button 
                className="btn-primary" 
                onClick={handleConfirmedSave}
                style={{
                  padding: '10px 24px',
                  fontSize: 14,
                  fontWeight: 600,
                }}
              >
                Confirm &amp; Save
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Header ────────────────────────────────────────────────────── */}
      <div className="tax-header">
        <div className="tax-header-left">
          <div className="tax-badge">BoF</div>
          <div>
            <h1 className="tax-title">Loan Interest Rates</h1>
            <p className="tax-subtitle">Bank of Fiji — Default rates applied to new loan applications</p>
          </div>
        </div>
        <div className="tax-header-right no-print">
          <button className="btn-outline" onClick={fetchRates} disabled={loading}>
            {loading ? 'Loading…' : 'Refresh'}
          </button>
        </div>
      </div>

      {error   && <div className="alert alert-error">! {error}<button style={{ marginLeft: 12, background: 'none', border: 'none', cursor: 'pointer', color: 'inherit', fontSize: 16 }} onClick={() => setError('')}>×</button></div>}
      {success && <div className="alert alert-success">✓ {success}</div>}

      {loading ? (
        <div className="tax-loading"><div className="spinner" /><p>Loading loan rates…</p></div>
      ) : (
        <>
          {/* Info banner */}
          <div className="loan-rates-info-banner" style={{
            background: 'rgba(122,64,255,0.08)', border: '1px solid rgba(122,64,255,0.25)',
            borderRadius: 8, padding: '11px 16px', marginBottom: 22,
            fontSize: 13, color: '#4a2ab5', display: 'flex', alignItems: 'flex-start', gap: 8,
          }}>
            <span style={{ fontSize: 16, flexShrink: 0 }}>i</span>
            <span>
              Rates set here apply to <strong>all new loan applications</strong> of that type.
              Existing active loans are not affected. All changes are permanently logged with
              your name and timestamp for RBF compliance.
            </span>
          </div>

          {/* Rate cards */}
          <div className="loan-rates-grid" style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(310px, 1fr))',
            gap: 18,
            marginBottom: 28,
          }}>
            {LOAN_PRODUCTS.map(product => {
              const saved    = getRateForType(product.type);
              const currentPct = saved
                ? parseFloat(saved.annualRatePct)
                : product.default;
              const isEdit   = editing[product.type];
              const isSaving = saving[product.type];
              const newPct   = parseFloat(rateInput[product.type] || 0);
              const hasPreview = isEdit && !isNaN(newPct) && newPct > 0;

              return (
                <div key={product.type} className="loan-rates-card" style={{
                  background: 'var(--surface)',
                  border: `1px solid ${isEdit ? product.color : 'var(--border)'}`,
                  borderTop: `3px solid ${product.color}`,
                  borderRadius: 'var(--radius)',
                  padding: 22,
                  boxShadow: isEdit ? `0 0 0 3px ${product.color}22` : 'var(--shadow)',
                  transition: 'border-color 0.15s, box-shadow 0.15s',
                }}>

                  {/* Card header */}
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
                    <div style={{
                      width: 60, height: 60, borderRadius: 12,
                      background: product.bg,
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      flexShrink: 0,
                    }}>
                      {renderLoanIcon(product.icon, product.color)}
                    </div>
                    <div>
                      <div style={{ fontWeight: 700, fontSize: 15, color: 'var(--text-primary)' }}>
                        {product.type}
                      </div>
                      <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 2 }}>
                        {product.desc}
                      </div>
                    </div>
                  </div>

                  {/* Current rate display */}
                  {!isEdit && (
                    <>
                      <div style={{
                        display: 'flex', justifyContent: 'space-between', alignItems: 'baseline',
                        marginBottom: 6,
                      }}>
                        <span style={{ fontSize: 12, color: 'var(--text-muted)', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                          Current rate
                        </span>
                        <span style={{ fontSize: 26, fontWeight: 700, color: product.color }}>
                          {currentPct.toFixed(2)}%
                          <span style={{ fontSize: 13, fontWeight: 400, color: 'var(--text-muted)', marginLeft: 4 }}>p.a.</span>
                        </span>
                      </div>

                      <div style={{
                        background: 'var(--bg-secondary)', border: '1px solid var(--border)',
                        borderRadius: 6, padding: '8px 12px', marginBottom: 14, fontSize: 12,
                        color: 'var(--text-muted)', display: 'flex', justifyContent: 'space-between',
                      }}>
                        <span>On FJD 10,000 over 3 years:</span>
                        <strong style={{ color: 'var(--text-primary)' }}>
                          {previewMonthly(currentPct)}/mo
                        </strong>
                      </div>

                      {saved?.setBy && (
                        <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 12 }}>
                          Last updated by{' '}
                          <strong>{saved.setBy === 'system' ? 'System default' : saved.setBy}</strong>
                          {saved.updatedAt && (
                            <> · {new Date(saved.updatedAt).toLocaleDateString('en-FJ', {
                              day: 'numeric', month: 'short', year: 'numeric',
                            })}</>
                          )}
                          {saved.changeReason && (
                            <div style={{ marginTop: 3 }}>"{saved.changeReason}"</div>
                          )}
                        </div>
                      )}

                      <button
                        className="btn-primary"
                        style={{
                          width: '100%', fontSize: 13, padding: '9px 0',
                          background: product.color, borderColor: product.color,
                        }}
                        onClick={() => openEdit(product.type)}
                      >
                        Update rate
                      </button>
                    </>
                  )}

                  {/* Edit form */}
                  {isEdit && (
                    <>
                      <div style={{ marginBottom: 12 }}>
                        <label style={{
                          display: 'block', fontSize: 11, fontWeight: 600,
                          textTransform: 'uppercase', letterSpacing: '0.06em',
                          color: 'var(--text-muted)', marginBottom: 6,
                        }}>
                          New Annual Rate (%)
                        </label>
                        <div style={{ position: 'relative' }}>
                          <input
                            type="number"
                            step="0.05"
                            min="0.05"
                            max="30"
                            placeholder={`e.g. ${product.default}`}
                            value={rateInput[product.type] ?? ''}
                            onChange={e => setRateInput(prev => ({ ...prev, [product.type]: e.target.value }))}
                            style={{
                              width: '100%', padding: '10px 36px 10px 14px',
                              border: `1.5px solid ${product.color}`,
                              borderRadius: 8, fontSize: 16, fontWeight: 600,
                              color: 'var(--text-primary)', background: 'var(--surface)',
                              outline: 'none', boxSizing: 'border-box',
                            }}
                            autoFocus
                          />
                          <span style={{
                            position: 'absolute', right: 12, top: '50%',
                            transform: 'translateY(-50%)',
                            color: 'var(--text-muted)', fontSize: 14, pointerEvents: 'none',
                          }}>%</span>
                        </div>
                      </div>

                      <div style={{ marginBottom: 14 }}>
                        <label style={{
                          display: 'block', fontSize: 11, fontWeight: 600,
                          textTransform: 'uppercase', letterSpacing: '0.06em',
                          color: 'var(--text-muted)', marginBottom: 6,
                        }}>
                          Reason / RBF Reference <span style={{ fontWeight: 400 }}>(optional)</span>
                        </label>
                        <input
                          type="text"
                          placeholder="e.g. RBF directive Q2 2026"
                          value={reasonInput[product.type] ?? ''}
                          onChange={e => setReasonInput(prev => ({ ...prev, [product.type]: e.target.value }))}
                          style={{
                            width: '100%', padding: '9px 14px',
                            border: '1px solid var(--border)', borderRadius: 8,
                            fontSize: 13, color: 'var(--text-primary)',
                            background: 'var(--bg-secondary)', outline: 'none',
                            boxSizing: 'border-box',
                          }}
                        />
                      </div>

                      {/* Live preview */}
                      {hasPreview && (
                        <div style={{
                          background: product.bg, border: `1px solid ${product.color}33`,
                          borderRadius: 6, padding: '8px 12px', marginBottom: 14,
                          fontSize: 12, display: 'flex', justifyContent: 'space-between',
                          color: 'var(--text-muted)',
                        }}>
                          <span>Est. on FJD 10,000 / 3 yrs:</span>
                          <strong style={{ color: product.color }}>
                            {previewMonthly(newPct)}/mo
                          </strong>
                        </div>
                      )}

                      <div style={{ display: 'flex', gap: 8 }}>
                        <button
                          className="btn-outline"
                          style={{ flex: 1, fontSize: 13, padding: '8px 0' }}
                          disabled={isSaving}
                          onClick={() => cancelEdit(product.type)}
                        >
                          Cancel
                        </button>
                        <button
                          className="btn-primary"
                          style={{
                            flex: 2, fontSize: 13, padding: '8px 0',
                            background: product.color, borderColor: product.color,
                          }}
                          disabled={isSaving || !rateInput[product.type]}
                          onClick={() => handleSave(product.type)}
                        >
                          {isSaving ? 'Saving…' : 'Save rate'}
                        </button>
                      </div>
                    </>
                  )}
                </div>
              );
            })}
          </div>

          {/* How it works */}
          <div className="tax-card">
            <h2 className="card-title">How loan rates work</h2>
            <div className="income-grid">
              {[
                ['Rates apply to new applications only',   'Existing active loans are not retroactively changed when you update a rate.'],
                ['Takes effect immediately',               'As soon as you save, the next loan application of that type uses the new rate.'],
                ['Monthly payment recalculated',           'The amortisation formula recalculates the monthly repayment amount for each new application.'],
                ['DSR assessment uses the new rate',       'Debt Service Ratio checks for affordability also use the updated rate automatically.'],
                ['All changes permanently logged',         'Rate history is retained for RBF and FRCS compliance audits with teller name and timestamp.'],
                ['Customers see rates on the loan form',   'The loan application page fetches live rates from this table so customers always see current rates.'],
              ].map(([title, sub]) => (
                <div className="income-row" key={title}>
                  <span style={{ fontWeight: 500 }}>{title}</span>
                  <span style={{ color: 'var(--text-muted)', fontSize: 13 }}>{sub}</span>
                </div>
              ))}
            </div>
          </div>

          <p className="tax-disclaimer">
            * All loan interest rate changes must comply with Reserve Bank of Fiji guidelines.
            Changes are permanently audited. Contact compliance@bof.com.fj or RBF on (679) 331 3611.
          </p>
        </>
      )}
    </div>
  );
}