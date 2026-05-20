import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import './TaxReport.css';
import './LoanApplication.css';

const BASE_PRODUCTS = [
  { type: 'Personal Loan',  icon: 'personal', color: '#3b82f6', desc: 'For personal expenses, holidays, education or emergencies.', defaultRate: 8.5,  maxAmount: 50000,  maxTermMonths: 60,  minAmount: 500   },
  { type: 'Home Loan',      icon: 'home', color: '#10b981', desc: 'Purchase or build your home in Fiji.',                        defaultRate: 6.5,  maxAmount: 500000, maxTermMonths: 300, minAmount: 10000 },
  { type: 'Vehicle Loan',   icon: 'vehicle', color: '#f59e0b', desc: 'Finance a new or used vehicle.',                              defaultRate: 7.5,  maxAmount: 80000,  maxTermMonths: 84,  minAmount: 2000  },
  { type: 'Business Loan',  icon: 'business', color: '#8b5cf6', desc: 'Grow your business with flexible financing.',                defaultRate: 9.0,  maxAmount: 200000, maxTermMonths: 120, minAmount: 5000  },
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

const EMPLOYMENT_TYPES = [
  'Permanent Employee', 'Contract Employee', 'Self-Employed',
  'Business Owner', 'Civil Servant', 'Retired',
];

const PURPOSES = {
  'Personal Loan':  ['Education', 'Medical', 'Holiday', 'Home Renovation', 'Debt Consolidation', 'Other'],
  'Home Loan':      ['Purchase', 'Construction', 'Renovation', 'Refinance'],
  'Vehicle Loan':   ['New Vehicle', 'Used Vehicle', 'Refinance'],
  'Business Loan':  ['Working Capital', 'Equipment', 'Expansion', 'Trade Finance', 'Other'],
};

const DOCUMENT_TYPES = [
  { key: 'PRIMARY_ID',          label: 'Primary ID',         desc: "Passport, national ID card or driver's licence" },
  { key: 'RESIDENCY_EVIDENCE',  label: 'Residency Evidence',  desc: 'Utility bill, rental agreement or rates notice' },
  { key: 'BANK_STATEMENT',      label: 'Bank Statement',      desc: 'Last 3 months from any bank' },
  { key: 'EMPLOYMENT_DOCUMENT', label: 'Employment Document', desc: 'Employment letter, payslip, or business registration' },
  { key: 'OTHER',               label: 'Other Documents',     desc: 'Any other supporting document' },
];

const steps = ['Loan type', 'Amount & term', 'Your details', 'Documents', 'Review'];

export default function LoanApplicationPage() {
  const navigate = useNavigate();
  const fileRefs = useRef({});

  const [step, setStep]             = useState(1);
  const [accounts, setAccounts]     = useState([]);
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted]   = useState(false);
  const [loanRef, setLoanRef]       = useState('');
  const [loanId, setLoanId]         = useState(null);
  const [error, setError]           = useState('');

  const [docFiles, setDocFiles]       = useState({});
  const [docUploading, setDocUploading] = useState({});
  const [docUploaded, setDocUploaded]   = useState({});
  const [docErrors, setDocErrors]       = useState({});

  const [form, setForm] = useState({
    loanType: '', purpose: '', amount: '', termMonths: 12,
    disbursementAccountId: '', employmentType: '', employer: '',
    monthlyIncome: '', otherIncome: '', existingLoans: '', agreedToTerms: false,
  });

  const [liveRates, setLiveRates] = useState({});

  useEffect(() => {
    api.get('/accounts').then(r => setAccounts(r.data || [])).catch(() => {});
    api.get('/loan-rates').then(res => {
      const rateMap = {};
      (res.data ?? []).forEach(r => { rateMap[r.loanType] = parseFloat(r.annualRatePct); });
      setLiveRates(rateMap);
    }).catch(() => {});
  }, []);

  const LOAN_PRODUCTS = BASE_PRODUCTS.map(p => ({
    ...p, minRate: liveRates[p.type] ?? p.defaultRate,
  }));

  const product = LOAN_PRODUCTS.find(p => p.type === form.loanType);
  const set = (f, v) => setForm(prev => ({ ...prev, [f]: v }));

  const calcRepayment = () => {
    if (!product || !form.amount || !form.termMonths) return null;
    const P = parseFloat(form.amount);
    const r = (product.minRate / 100) / 12;
    const n = parseInt(form.termMonths);
    if (!P || !r || !n) return null;
    return ((P * r * Math.pow(1 + r, n)) / (Math.pow(1 + r, n) - 1)).toFixed(2);
  };

  const repayment      = calcRepayment();
  const totalRepayable = repayment ? (parseFloat(repayment) * parseInt(form.termMonths)).toFixed(2) : null;
  const totalInterest  = totalRepayable && form.amount
    ? (parseFloat(totalRepayable) - parseFloat(form.amount)).toFixed(2) : null;

  const fmt = v => `FJD ${parseFloat(v).toLocaleString('en-FJ', {
    minimumFractionDigits: 2, maximumFractionDigits: 2,
  })}`;

  const canProceed = () => {
    if (step === 1) return form.loanType && form.purpose;
    if (step === 2) {
      const amt = parseFloat(form.amount);
      return amt >= product?.minAmount && amt <= product?.maxAmount && form.disbursementAccountId;
    }
    if (step === 3) return form.employmentType && form.monthlyIncome;
    if (step === 4) return true;
    if (step === 5) return form.agreedToTerms;
    return false;
  };

  const handleSubmit = async () => {
    setSubmitting(true);
    setError('');
    try {
      const res = await api.post('/loans/apply', {
        loanType:                  form.loanType,
        amount:                    parseFloat(form.amount),
        termMonths:                parseInt(form.termMonths),
        purpose:                   form.purpose,
        disbursementAccountId:     parseInt(form.disbursementAccountId),
        employmentType:            form.employmentType,
        employer:                  form.employer || null,
        monthlyIncome:             parseFloat(form.monthlyIncome),
        otherIncome:               form.otherIncome ? parseFloat(form.otherIncome) : null,
        existingMonthlyRepayments: form.existingLoans ? parseFloat(form.existingLoans) : null,
      });
      const id = res.data.id;
      setLoanRef(res.data.loanNumber);
      setLoanId(id);
      for (const dt of DOCUMENT_TYPES) {
        const file = docFiles[dt.key];
        if (file) {
          try {
            const fd = new FormData();
            fd.append('file', file);
            fd.append('documentType', dt.key);
            await api.post(`/loans/${id}/documents`, fd, {
              headers: { 'Content-Type': 'multipart/form-data' },
            });
            setDocUploaded(prev => ({ ...prev, [dt.key]: true }));
          } catch { /* non-fatal */ }
        }
      }
      setSubmitted(true);
    } catch (err) {
      setError(err.response?.data?.message || 'Application failed. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleFileSelect = (docType, file) => {
    if (!file) return;
    if (file.size > 10 * 1024 * 1024) {
      setDocErrors(prev => ({ ...prev, [docType]: 'File exceeds 10 MB limit.' }));
      return;
    }
    setDocFiles(prev  => ({ ...prev, [docType]: file }));
    setDocErrors(prev => ({ ...prev, [docType]: '' }));
  };

  const dsrBlock = (() => {
    if (!form.monthlyIncome || !repayment) return null;
    const income = parseFloat(form.monthlyIncome);
    if (!income) return null;
    const dsr = (parseFloat(repayment) + parseFloat(form.existingLoans || 0)) / income * 100;
    const ok  = dsr <= 40;
    return (
      <div className={`loan-affordability ${ok ? 'ok' : 'warn'}`}>
        <strong>Debt service ratio: {dsr.toFixed(1)}%</strong>
        <p style={{ margin: '6px 0 0', fontSize: 13, fontWeight: 400 }}>
          {ok
            ? 'Within the recommended 40% RBF guideline — good affordability.'
            : 'Exceeds the recommended 40% RBF guideline. Consider reducing the loan amount or extending the term.'}
        </p>
      </div>
    );
  })();

  return (
    <div className="tax-page">
      {/* Header */}
      <div className="tax-header">
        <div className="tax-header-left">
          <button className="btn-back no-print" onClick={() => navigate('/dashboard')}>← Back</button>
          <div className="tax-badge">BoF</div>
          <div>
            <h1 className="tax-title">Loan Application</h1>
            <p className="tax-subtitle">Bank of Fiji — Apply online in minutes</p>
          </div>
        </div>
      </div>

      {/* Step progress */}
      <div className="loan-stepper">
        {steps.map((s, i) => (
          <React.Fragment key={s}>
            <div className={`loan-step ${i + 1 === step ? 'active' : i + 1 < step ? 'done' : ''}`}>
              <div className="loan-step-circle">{i + 1 < step ? '✓' : i + 1}</div>
              <span className="loan-step-label">{s}</span>
            </div>
            {i < steps.length - 1 && (
              <div className={`loan-step-line ${i + 1 < step ? 'done' : ''}`} />
            )}
          </React.Fragment>
        ))}
      </div>

      {error && <div className="alert alert-error">! {error}</div>}

      {/* ── STEP 1 — Loan type ── */}
      {step === 1 && (
        <div className="tax-card">
          <h2 className="card-title">Choose your loan type</h2>
          <div className="loan-product-grid">
            {LOAN_PRODUCTS.map(p => (
              <div key={p.type}
                className={`loan-product-card ${form.loanType === p.type ? 'selected' : ''}`}
                onClick={() => { set('loanType', p.type); set('purpose', ''); }}>
                <span className="loan-product-icon" style={{ backgroundColor: p.color + '15', color: p.color }}>
                  {renderLoanIcon(p.icon, p.color)}
                </span>
                <h3 className="loan-product-name">{p.type}</h3>
                <p className="loan-product-desc">{p.desc}</p>
                <div className="loan-product-rate">from {p.minRate}% p.a.</div>
                <div className="loan-product-max">Up to {fmt(p.maxAmount)}</div>
              </div>
            ))}
          </div>
          {form.loanType && (
            <div style={{ marginTop: 26 }}>
              <div className="loan-section-divider">Select a purpose</div>
              <div className="loan-pill-group">
                {PURPOSES[form.loanType]?.map(p => (
                  <button key={p}
                    className={`loan-pill ${form.purpose === p ? 'selected' : ''}`}
                    onClick={() => set('purpose', p)}>{p}</button>
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {/* ── STEP 2 — Amount & term ── */}
      {step === 2 && product && (
        <div className="tax-card">
          <h2 className="card-title">{form.loanType} — Amount &amp; Term</h2>
          <div className="loan-form-grid">
            <div className="loan-field">
              <label className="loan-label">Loan amount (FJD)</label>
              <input type="number" className="loan-input"
                placeholder={`${product.minAmount} – ${product.maxAmount}`}
                value={form.amount} min={product.minAmount} max={product.maxAmount}
                onChange={e => set('amount', e.target.value)} />
              <span className="loan-hint">Min {fmt(product.minAmount)} — Max {fmt(product.maxAmount)}</span>
            </div>
            <div className="loan-field">
              <label className="loan-label">Loan term</label>
              <select className="loan-input" value={form.termMonths}
                onChange={e => set('termMonths', e.target.value)}>
                {[6,12,18,24,36,48,60,72,84,96,120,180,240,300]
                  .filter(t => t <= product.maxTermMonths)
                  .map(t => (
                    <option key={t} value={t}>
                      {t >= 12 ? `${t/12} year${t/12 > 1 ? 's' : ''}` : `${t} months`}
                    </option>
                  ))}
              </select>
            </div>
            <div className="loan-field">
              <label className="loan-label">Disbursement account</label>
              <select className="loan-input" value={form.disbursementAccountId}
                onChange={e => set('disbursementAccountId', e.target.value)}>
                <option value="">Select account</option>
                {accounts.map(a => (
                  <option key={a.id} value={a.id}>{a.accountName} — {a.accountNumber}</option>
                ))}
              </select>
              <span className="loan-hint">Approved funds will be deposited here</span>
            </div>
          </div>

          {repayment && (
            <div className="loan-estimate">
              <div className="loan-estimate-title">Repayment estimate</div>
              <div className="summary-grid" style={{ marginBottom: 0 }}>
                <div className="summary-card blue">
                  <span className="summary-label">Monthly repayment</span>
                  <span className="summary-amount">{fmt(repayment)}</span>
                  <span className="summary-sub">at {product.minRate}% p.a.</span>
                </div>
                <div className="summary-card green">
                  <span className="summary-label">Total repayable</span>
                  <span className="summary-amount">{fmt(totalRepayable)}</span>
                  <span className="summary-sub">over {form.termMonths} months</span>
                </div>
                <div className="summary-card red">
                  <span className="summary-label">Total interest</span>
                  <span className="summary-amount">{fmt(totalInterest)}</span>
                  <span className="summary-sub">cost of borrowing</span>
                </div>
              </div>
              <p className="loan-estimate-note">
                * Estimate only. Actual rate and repayment subject to credit assessment.
              </p>
            </div>
          )}
        </div>
      )}

      {/* ── STEP 3 — Employment & income ── */}
      {step === 3 && (
        <div className="tax-card">
          <h2 className="card-title">Employment &amp; income</h2>
          <p style={{ color: '#6b7280', fontSize: 14, marginBottom: 22, lineHeight: 1.6 }}>
            This information helps us assess your repayment capacity in line with responsible lending standards.
          </p>
          <div className="loan-form-grid">
            <div className="loan-field">
              <label className="loan-label">Employment type</label>
              <select className="loan-input" value={form.employmentType}
                onChange={e => set('employmentType', e.target.value)}>
                <option value="">Select type</option>
                {EMPLOYMENT_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
              </select>
            </div>
            <div className="loan-field">
              <label className="loan-label">Employer / Business name</label>
              <input type="text" className="loan-input"
                placeholder="e.g. Fiji Sugar Corporation"
                value={form.employer} onChange={e => set('employer', e.target.value)} />
            </div>
            <div className="loan-field">
              <label className="loan-label">Monthly gross income (FJD)</label>
              <input type="number" className="loan-input" placeholder="e.g. 4200"
                value={form.monthlyIncome} onChange={e => set('monthlyIncome', e.target.value)} />
            </div>
            <div className="loan-field">
              <label className="loan-label">
                Other monthly income (FJD){' '}
                <span style={{ fontWeight: 400, textTransform: 'none', fontSize: 11 }}>optional</span>
              </label>
              <input type="number" className="loan-input" placeholder="Rental, side income etc."
                value={form.otherIncome} onChange={e => set('otherIncome', e.target.value)} />
            </div>
            <div className="loan-field">
              <label className="loan-label">
                Existing monthly repayments (FJD){' '}
                <span style={{ fontWeight: 400, textTransform: 'none', fontSize: 11 }}>optional</span>
              </label>
              <input type="number" className="loan-input" placeholder="0 if none"
                value={form.existingLoans} onChange={e => set('existingLoans', e.target.value)} />
            </div>
          </div>
          {dsrBlock}
        </div>
      )}

      {/* ── STEP 4 — Documents ── */}
      {step === 4 && (
        <div className="tax-card">
          <h2 className="card-title">Supporting documents</h2>
          <p style={{ color: '#6b7280', fontSize: 14, marginBottom: 20, lineHeight: 1.6 }}>
            Upload supporting documents before submitting. Documents are locked after submission.
          </p>

          <div className="loan-info-banner info" style={{ marginBottom: 22 }}>
            <span className="loan-info-banner-icon">i</span>
            <span>
              Documents are optional but recommended — especially <strong>Primary ID</strong> and a
              <strong> Bank Statement</strong>. Once you submit, documents cannot be changed.
            </span>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {DOCUMENT_TYPES.map(dt => {
              const file     = docFiles[dt.key];
              const uploaded = docUploaded[dt.key];
              const err      = docErrors[dt.key];
              const locked   = submitted;

              return (
                <div key={dt.key} className={`doc-row ${uploaded ? 'uploaded' : ''}`}
                  style={{ opacity: locked && !uploaded ? 0.5 : 1 }}>
                  <div className="doc-row-info">
                    <div className="doc-row-title">
                      {dt.label}
                      {uploaded && (
                        <span style={{ marginLeft: 8, fontSize: 11, color: '#059669', fontWeight: 600 }}>
                          ✓ Ready
                        </span>
                      )}
                    </div>
                    <div className="doc-row-desc">{dt.desc} · PDF, JPG or PNG · max 10 MB</div>
                    {err && <div className="doc-row-error">! {err}</div>}
                    {file && !uploaded && (
                      <div className="doc-row-filename">{file.name} ({(file.size / 1024).toFixed(1)} KB)</div>
                    )}
                  </div>

                  <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexShrink: 0 }}>
                    {uploaded ? (
                      <div className="doc-uploaded-badge">✓ Staged</div>
                    ) : !locked ? (
                      <>
                        <input
                          ref={el => fileRefs.current[dt.key] = el}
                          type="file" accept=".pdf,.jpg,.jpeg,.png"
                          style={{ display: 'none' }}
                          onChange={e => handleFileSelect(dt.key, e.target.files[0])}
                        />
                        <button className="btn-ghost btn-sm"
                          onClick={() => fileRefs.current[dt.key]?.click()}>
                          {file ? 'Change' : 'Choose file'}
                        </button>
                      </>
                    ) : (
                      <span style={{ fontSize: 12, color: '#9ca3af' }}>! LOCKED</span>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* ── STEP 5 — Review ── */}
      {step === 5 && !submitted && (
        <div className="tax-card">
          <h2 className="card-title">Review your application</h2>

          <div className="loan-review-grid">
            <div className="loan-review-section">
              <div className="loan-review-heading">Loan details</div>
              <div className="income-grid">
                {[
                  ['Loan type',         form.loanType,              null],
                  ['Purpose',           form.purpose,               null],
                  ['Amount',            fmt(form.amount),            'positive'],
                  ['Term',              `${form.termMonths} months`, null],
                  ['Rate (indicative)', `${product?.minRate}% p.a.`,null],
                  ['Est. monthly',      repayment ? fmt(repayment) : '—', 'positive'],
                  ['Disbursement acct', accounts.find(a => String(a.id) === String(form.disbursementAccountId))?.accountNumber || '—', null],
                ].map(([label, val, cls]) => (
                  <div className="income-row" key={label}>
                    <span style={{ color: '#4b5563' }}>{label}</span>
                    <span className={cls ?? ''} style={{ fontFamily: "\"Sora\", \"Manrope\", \"Segoe UI\", sans-serif" }}>{val}</span>
                  </div>
                ))}
              </div>
            </div>

            <div className="loan-review-section">
              <div className="loan-review-heading">Employment details</div>
              <div className="income-grid">
                {[
                  ['Employment type', form.employmentType, null],
                  ['Employer',        form.employer || '—', null],
                  ['Monthly income',  fmt(form.monthlyIncome), 'positive'],
                  ...(form.otherIncome  ? [['Other income',       fmt(form.otherIncome),  'positive']] : []),
                  ...(form.existingLoans ? [['Existing repayments', `−${fmt(form.existingLoans)}`, 'negative']] : []),
                ].map(([label, val, cls]) => (
                  <div className="income-row" key={label}>
                    <span style={{ color: '#4b5563' }}>{label}</span>
                    <span className={cls ?? ''} style={{ fontFamily: "\"Sora\", \"Manrope\", \"Segoe UI\", sans-serif" }}>{val}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Documents summary */}
          <div className="loan-review-section" style={{ marginBottom: 22 }}>
            <div className="loan-review-heading">Documents staged</div>
            <div className="income-grid">
              {DOCUMENT_TYPES.map(dt => (
                <div className="income-row" key={dt.key}>
                  <span style={{ color: '#4b5563' }}>{dt.label}</span>
                  <span>
                    {docFiles[dt.key]
                      ? <span style={{ color: '#059669', fontWeight: 600, fontSize: 13 }}>✓ {docFiles[dt.key].name}</span>
                      : <span style={{ color: '#9ca3af', fontSize: 13 }}>Not attached</span>}
                  </span>
                </div>
              ))}
            </div>
          </div>

          <div className="loan-tnc">
            <label className="loan-tnc-label">
              <input type="checkbox" checked={form.agreedToTerms}
                onChange={e => set('agreedToTerms', e.target.checked)} />
              <span>
                I confirm the information provided is accurate. I authorise Bank of Fiji to
                conduct credit checks and verify my details. I agree to the{' '}
                <strong>Loan Terms &amp; Conditions</strong> and RBF responsible lending guidelines.
              </span>
            </label>
          </div>
        </div>
      )}

      {/* ── Success ── */}
      {step === 5 && submitted && (
        <div className="tax-card">
          <div className="loan-success-card">
            <span className="loan-success-icon">✓</span>
            <div className="loan-success-title">Application submitted!</div>
            <p style={{ color: '#6b7280', fontSize: 15, margin: '0 0 4px' }}>
              Your {form.loanType} application has been received.
            </p>
            <p style={{ color: '#9ca3af', fontSize: 13 }}>
              Our team will review it within 2–3 business days.
            </p>
            <div className="loan-success-ref">{loanRef}</div>
            <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
              <button className="btn-outline" style={{ padding: '11px 24px' }}
                onClick={() => navigate('/loans')}>
                View my loans
              </button>
              <button className="btn-primary" style={{ padding: '11px 28px' }}
                onClick={() => navigate('/dashboard')}>
                Back to dashboard
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Navigation bar ── */}
      {!submitted && (
        <div className="tax-card actions-card no-print" style={{ background: '#f9fafb' }}>
          <div className="actions-row">
            <p style={{ margin: 0, fontSize: 13, color: '#9ca3af', fontFamily: "\"Sora\", \"Manrope\", \"Segoe UI\", sans-serif" }}>
              Step {step} of {steps.length} — <span style={{ color: '#0f2044', fontWeight: 600 }}>{steps[step - 1]}</span>
            </p>
            <div className="actions-buttons">
              {step > 1 && (
                <button className="btn-outline" style={{ padding: '10px 22px' }}
                  onClick={() => setStep(s => s - 1)}>
                  ← Back
                </button>
              )}
              {step < 5 ? (
                <button className="btn-primary" style={{ padding: '10px 28px', minWidth: 130 }}
                  disabled={!canProceed()}
                  onClick={() => setStep(s => s + 1)}>
                  Continue →
                </button>
              ) : (
                <button className="btn-primary" style={{ padding: '10px 28px', minWidth: 180 }}
                  disabled={!canProceed() || submitting}
                  onClick={handleSubmit}>
                  {submitting ? 'Submitting…' : 'Submit Application'}
                </button>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}