import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';
import './TaxReport.css';

const TaxReportPage = () => {
  const { user }  = useAuth();
  const navigate  = useNavigate();
  const fileInputRef = useRef();

  const [report, setReport]         = useState(null);
  const [loading, setLoading]       = useState(true);
  const [error, setError]           = useState('');
  const [year, setYear]             = useState(new Date().getFullYear());
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted]   = useState(false);
  const [submitRef, setSubmitRef]   = useState('');
  const [exemptFile, setExemptFile]           = useState(null);
  const [exemptUploading, setExemptUploading] = useState(false);
  const [exemptUploaded, setExemptUploaded]   = useState(false);
  const [exemptError, setExemptError]         = useState('');
  const [lastFetched, setLastFetched]         = useState(null);
  const [confirmingSubmit, setConfirmingSubmit] = useState(false);
  const [justSubmitted, setJustSubmitted]       = useState(false);

  const currentYear = new Date().getFullYear();
  const yearOptions = Array.from({ length: 5 }, (_, i) => currentYear - i);

  useEffect(() => { fetchReport(); }, [year]);

  const fetchReport = async () => {
    setLoading(true); setError(''); setSubmitted(false); setExemptUploaded(false);
    setJustSubmitted(false); setConfirmingSubmit(false);
    try {
      const res = await api.get(`/tax/report?year=${year}`);
      setReport(res.data);
      setLastFetched(new Date());
    }
    catch { setError('Could not load your tax report. Please try again.'); }
    finally { setLoading(false); }
  };

  const handleSubmit = async () => {
    setSubmitting(true);
    try {
      const res = await api.post(`/tax/submit?year=${year}`);
      setSubmitRef(res.data.referenceNumber);
      setJustSubmitted(true);
      setConfirmingSubmit(false);
      await fetchReport();
    } catch {
      setError('Could not submit. Please try again.');
      setConfirmingSubmit(false);
    } finally {
      setSubmitting(false);
    }
  };

  const handleExemptUpload = async () => {
    if (!exemptFile) return;
    setExemptUploading(true); setExemptError('');
    try {
      const fd = new FormData();
      fd.append('file', exemptFile);
      fd.append('year', year);
      fd.append('riwtWithheld', withheldAmt ?? '0');
      await api.post('/tax/riwt-exemption/upload', fd, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      setExemptUploaded(true);
    } catch (err) {
      setExemptError(err?.response?.data?.message ?? 'Upload failed. Please try again.');
    } finally { setExemptUploading(false); }
  };

  const handlePrint = () => {
    document.body.classList.add('print-tax-report');
    window.print();
    window.addEventListener('afterprint', () =>
      document.body.classList.remove('print-tax-report'), { once: true });
  };

  const handleExportCSV = () => {
    if (!report) return;

    const reportId   = `${report.customerId}-${report.taxYear}`;
    const customerId = report.customerId;
    const taxYear    = String(report.taxYear);

    const HEADER = [
      'report_id', 'customer_id', 'tax_year', 'section', 'record_type',
      'field_code', 'period', 'tax_type', 'value_type', 'value',
      'currency', 'source_field',
    ];

    const rows = [HEADER];

    // ── Row helpers ──────────────────────────────────────────────────────
    const pushRow = (section, fieldCode, period, taxType, valueType, value, currency, sourceField) => {
      rows.push([
        reportId, customerId, taxYear, section, 'field',
        fieldCode, period, taxType, valueType,
        value != null ? String(value) : '',
        currency, sourceField,
      ]);
    };

    const pushMoneyRow = (section, fieldCode, value, sourceField, taxType = '') => {
      const dec = value != null ? Number(value) : null;
      pushRow(section, fieldCode, '', taxType, 'decimal',
        dec != null && !Number.isNaN(dec) ? dec.toFixed(2) : null,
        'FJD', sourceField);
    };

    const pushIntegerRow = (section, fieldCode, value, sourceField) => {
      const int = value != null ? Number(value) : null;
      pushRow(section, fieldCode, '', '', 'integer',
        int != null && !Number.isNaN(int) ? String(Math.round(int)) : null,
        '', sourceField);
    };

    const pushBooleanRow = (section, fieldCode, value, sourceField) => {
      pushRow(section, fieldCode, '', '', 'boolean',
        value === true ? 'true' : value === false ? 'false' : null,
        '', sourceField);
    };

    const pushTextRow = (section, fieldCode, value, sourceField) => {
      pushRow(section, fieldCode, '', '', 'text',
        value != null ? String(value) : null,
        '', sourceField);
    };

    const pushDateRow = (section, fieldCode, value, sourceField) => {
      // Coerce to ISO YYYY-MM-DD if it looks like a date
      let iso = null;
      if (value != null) {
        const d = new Date(value);
        iso = !Number.isNaN(d.getTime()) ? d.toISOString().slice(0, 10) : String(value);
      }
      pushRow(section, fieldCode, '', '', 'date', iso, '', sourceField);
    };

    const pushDecimalRow = (section, fieldCode, value, sourceField) => {
      const dec = value != null ? Number(value) : null;
      pushRow(section, fieldCode, '', '', 'decimal',
        dec != null && !Number.isNaN(dec) ? String(dec) : null,
        '', sourceField);
    };

    const pushMonthlyRow = (period, fieldCode, valueType, value, currency, sourceField, taxType = '') => {
      rows.push([
        reportId, customerId, taxYear, 'monthly_breakdown', 'monthly_breakdown',
        fieldCode, period, taxType, valueType,
        value != null ? String(value) : '',
        currency, sourceField,
      ]);
    };

    // Determine withholding tax_type for monthly rows.
    // If only one type was charged this year, every monthly tax row is that type.
    // If both were charged (mid-year change), we cannot attribute per-month, so leave blank.
    const nrwht = parseFloat(report.nrwhtWithheld ?? 0);
    const riwt  = parseFloat(report.riwtWithheld  ?? 0);
    const monthlyTaxType = nrwht > 0 && riwt > 0 ? ''       // mid-year: genuinely mixed
      : nrwht > 0                                ? 'NRWHT'
      : riwt  > 0                                ? 'RIWT'
      :                                            '';

    // ── Section: metadata ────────────────────────────────────────────────
    pushTextRow('metadata',    'full_name',          report.fullName,       'fullName');
    pushTextRow('metadata',    'customer_id',        report.customerId,     'customerId');
    pushTextRow('metadata',    'tin_number',         report.tinNumber,      'tinNumber');
    pushIntegerRow('metadata', 'tax_year',           report.taxYear,        'taxYear');
    pushTextRow('metadata',    'status',             report.status,         'status');
    pushBooleanRow('metadata', 'is_resident',        report.isResident,     'isResident');
    pushBooleanRow('metadata', 'is_senior_citizen',  report.isSeniorCitizen, 'isSeniorCitizen');
    // Parse numeric rate from backend string like "3.5% p.a."
    const parsedRate = report.interestRate != null
      ? parseFloat(String(report.interestRate).replace(/[^\d.]/g, ''))
      : null;
    pushDecimalRow('metadata', 'interest_rate',      !Number.isNaN(parsedRate) ? parsedRate : null, 'interestRate');
    pushTextRow('metadata',    'interest_rate_unit',  'percent_per_annum',  'interestRate');
    pushBooleanRow('metadata', 'riwt_exempt',        report.riwtExempt,     'riwtExempt');
    pushBooleanRow('metadata', 'riwt_rejected',      report.riwtRejected,   'riwtRejected');

    // ── Section: interest ────────────────────────────────────────────────
    pushMoneyRow('interest', 'interest_earned',         report.interestEarned,        'interestEarned');
    pushMoneyRow('interest', 'riwt_withheld',           report.riwtWithheld,          'riwtWithheld',  'RIWT');
    pushMoneyRow('interest', 'nrwht_withheld',          report.nrwhtWithheld,         'nrwhtWithheld', 'NRWHT');
    pushBooleanRow('interest', 'nrwht_refunded',        report.nrwhtRefunded,         'nrwhtRefunded');
    pushTextRow('interest', 'nrwht_refund_reference',   report.nrwhtRefundReference,  'nrwhtRefundReference');
    pushMoneyRow('interest', 'net_interest_paid',       report.netInterestPaid,       'netInterestPaid');

    // ── Section: income ──────────────────────────────────────────────────
    pushMoneyRow('income',    'gross_income',       report.grossIncome,      'grossIncome');
    pushMoneyRow('income',    'total_credits',      report.totalCredits,     'totalCredits');
    pushMoneyRow('income',    'total_debits',       report.totalDebits,      'totalDebits');
    pushMoneyRow('income',    'taxable_income',     report.taxableIncome,    'taxableIncome');
    pushIntegerRow('income',  'transaction_count',  report.transactionCount, 'transactionCount');

    // ── Section: tax ─────────────────────────────────────────────────────
    pushMoneyRow('tax', 'paye_owed',         report.payeOwed,        'payeOwed',        'PAYE');
    pushMoneyRow('tax', 'vat_on_fees',       report.vatOnFees,       'vatOnFees',       'VAT');
    pushMoneyRow('tax', 'bank_fees_charged', report.bankFeesCharged, 'bankFeesCharged');
    pushMoneyRow('tax', 'fnpf_employee',     report.fnpfEmployee,    'fnpfEmployee');
    pushMoneyRow('tax', 'fnpf_employer',     report.fnpfEmployer,    'fnpfEmployer');
    pushMoneyRow('tax', 'total_tax_owed',    report.totalTaxOwed,    'totalTaxOwed');

    // ── Section: frcs ────────────────────────────────────────────────────
    pushTextRow('frcs',    'frcs_reference',       report.frcsReference,      'frcsReference');
    pushBooleanRow('frcs', 'submitted_to_frcs',    report.submittedToFrcs,    'submittedToFrcs');
    pushDateRow('frcs',    'frcs_submission_date',  report.frcsSubmissionDate, 'frcsSubmissionDate');

    // ── Section: monthly_breakdown ───────────────────────────────────────
    const monthly = report.monthlyBreakdown || [];
    monthly.forEach((m, idx) => {
      const mm     = String(idx + 1).padStart(2, '0');
      const period = `${report.taxYear}-${mm}`;
      const dec    = (v) => {
        const n = v != null ? Number(v) : null;
        return n != null && !Number.isNaN(n) ? n.toFixed(2) : null;
      };
      pushMonthlyRow(period, 'income',       'decimal', dec(m.income),      'FJD', 'monthlyBreakdown.income');
      pushMonthlyRow(period, 'interest',     'decimal', dec(m.interest),    'FJD', 'monthlyBreakdown.interest');
      pushMonthlyRow(period, 'tax_withheld', 'decimal', dec(m.taxWithheld), 'FJD', 'monthlyBreakdown.taxWithheld', monthlyTaxType);
    });

    // ── Build CSV string and download ────────────────────────────────────
    const csv = rows.map(r =>
      r.map(c => `"${String(c ?? '').replace(/"/g, '""')}"`).join(',')
    ).join('\r\n');

    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url  = URL.createObjectURL(blob);
    const a    = document.createElement('a');
    a.href     = url;
    a.download = `Tax-Report-${report.customerId}-${report.taxYear}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const fmt = (val) => val != null
    ? `FJD ${parseFloat(val).toLocaleString('en-FJ', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
    : 'FJD 0.00';

  const fmtTime = (date) => date
    ? date.toLocaleTimeString('en-FJ', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
    : '';

  // ── Withholding logic — handles BOTH NRWHT and RIWT in the same year ───────
  //
  // After a resident-without-TIN registers their TIN:
  //   - nrwhtWithheld > 0 (was charged Jan–Mar)
  //   - riwtWithheld  = 0 (not charged yet, will apply from now)
  //   - The NRWHT has been REFUNDED to their account by NrwhtRefundService
  //
  // After a mid-year residency change:
  //   - Both nrwhtWithheld > 0 AND riwtWithheld > 0
  //   - Both are shown separately
  //
  // In the normal case only one of the two is non-zero.

  const nrwhtAmt = parseFloat(report?.nrwhtWithheld ?? 0);
  const riwtAmt  = parseFloat(report?.riwtWithheld  ?? 0);

  // Was NRWHT charged but user now has a TIN? That means refund happened.
  // const nrwhtWasRefunded =
  //   nrwhtAmt > 0 &&
  //   report?.tinNumber && report.tinNumber !== 'Not Provided' &&
  //   report?.isResident;
  const nrwhtWasRefunded = report?.nrwhtRefunded === true;

  // Mid-year change: both types were charged
  const midYearChange = nrwhtAmt > 0 && riwtAmt > 0;

  // For the single-type display (used in the interest stats panel)
  const getWithholding = () => {
    if (!report || report.isSeniorCitizen) return null;

    // If NRWHT was refunded (resident now has TIN), show the refund scenario
    if (nrwhtWasRefunded && riwtAmt === 0) {
      return {
        code: 'NRWHT',
        amount: report.nrwhtWithheld,
        tagClass: 'tag-orange',
        title: 'NRWHT withheld this year (refunded)',
        wasRefunded: true,
      };
    }
    // Mid-year — show NRWHT primarily (handled separately below)
    if (midYearChange) {
      return { code: 'MIXED', amount: (nrwhtAmt + riwtAmt).toFixed(2), tagClass: 'tag-orange', title: 'Mixed withholding — see detail below' };
    }
    // Normal NRWHT (no TIN or non-resident)
    if (!report.isResident || report.tinNumber === 'Not Provided') {
      return {
        code: 'NRWHT', amount: report.nrwhtWithheld, tagClass: 'tag-orange',
        title: !report.isResident ? 'Tax withheld — non-resident' : 'Tax withheld — no TIN registered',
        fix: !report.isResident ? null : 'Registering your TIN will trigger an automatic refund of NRWHT charged this year.',
        wasRefunded: false,
      };
    }
    // Normal RIWT (resident with TIN)
    return {
      code: 'RIWT', amount: report.riwtWithheld, tagClass: 'tag-blue',
      title: 'Resident Interest Withholding Tax (RIWT)', fix: null, wasRefunded: false,
    };
  };

  const withholding    = getWithholding();
  const interestGross  = parseFloat(report?.interestEarned ?? 0);
  const withheldAmt    = midYearChange
    ? (nrwhtAmt + riwtAmt)
    : parseFloat(withholding?.amount ?? 0);
  const withholdPct    = interestGross > 0
    ? ((withheldAmt / interestGross) * 100).toFixed(1) : '0.0';
  const isRiwtExempt   = report?.riwtExempt === true;
  const isRiwtRejected = report?.riwtRejected === true;

  const isFrcsSubmitted = report?.status === 'SUBMITTED';
  const isPendingFrcs   = report?.status === 'PENDING_FRCS';

  const showExemptBlock = report
    && !isRiwtExempt
    && !report.isSeniorCitizen
    && report.isResident
    && report.tinNumber !== 'Not Provided';

  return (
    <div className="tax-page">
      {/* Header */}
      <div className="tax-header">
        <div className="tax-header-left">
          <div className="tax-badge">FRCS</div>
          <div>
            <h1 className="tax-title">My Tax Summary</h1>
            <p className="tax-subtitle">Bank of Fiji — {year} tax year</p>
          </div>
        </div>
        <div className="tax-header-right no-print">
          <select className="year-select" value={year} onChange={e => setYear(parseInt(e.target.value))}>
            {yearOptions.map(y => <option key={y} value={y}>{y}</option>)}
          </select>
          <button className="btn-outline" onClick={fetchReport} disabled={loading}
            title="Recalculate report with the latest interest rate and transactions">
            {loading ? '↻ Loading…' : '↻ Refresh'}
          </button>
          <button className="btn-outline" onClick={handlePrint}>⬇ Download PDF</button>
          <button className="btn-outline" onClick={handleExportCSV}>↓ Export CSV</button>
        </div>
      </div>

      {/* Rate info banner */}
      {report && lastFetched && !loading && (
        <div style={{
          background: 'rgba(59,130,246,0.06)', border: '1px solid rgba(59,130,246,0.18)',
          borderRadius: 8, padding: '9px 16px', marginBottom: 16,
          fontSize: 13, color: '#1e40af',
          display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap',
        }}>
          <span>
            📊 Report calculated using savings rate of{' '}
            <strong>{report.interestRate ?? 'N/A'}</strong>.
            Press <strong>↻ Refresh</strong> to recalculate after any changes.
          </span>
          <span style={{ fontSize: 12, color: '#6b7280', whiteSpace: 'nowrap' }}>
            Last updated: {fmtTime(lastFetched)}
          </span>
        </div>
      )}

     {/* Replace the existing nrwhtWasRefunded block with: */}
      {report && nrwhtWasRefunded && riwtAmt === 0 && (
        <div style={{
          background: 'rgba(16,185,129,0.07)', border: '1px solid rgba(16,185,129,0.28)',
          borderRadius: 8, padding: '16px 20px', marginBottom: 18,
          display: 'flex', alignItems: 'flex-start', gap: 14,
        }}>
          <span style={{ fontSize: 26, flexShrink: 0 }}>💸</span>
          <div>
            <strong style={{ fontSize: 14 }}>NRWHT refund credited to your account</strong>
            <div style={{ fontSize: 13, color: 'var(--text-secondary)', marginTop: 4, lineHeight: 1.6 }}>
              Because you registered your TIN (<strong>{report.tinNumber}</strong>), the NRWHT
              of <strong>{fmt(report.nrwhtWithheld)}</strong> withheld this year has been
              automatically refunded to your savings account. Future interest applies RIWT (10%).
              {report.nrwhtRefundReference && (
                <span style={{ display: 'block', marginTop: 6, fontSize: 12, color: '#6b7280' }}>
                  Refund reference: <code>{report.nrwhtRefundReference}</code>
                </span>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Mid-year tax type change notice */}
      {report && midYearChange && (
        <div style={{
          background: 'rgba(245,158,11,0.07)', border: '1px solid rgba(245,158,11,0.28)',
          borderRadius: 8, padding: '14px 18px', marginBottom: 18,
          display: 'flex', alignItems: 'flex-start', gap: 12,
        }}>
          <span style={{ fontSize: 22, flexShrink: 0 }}>🔄</span>
          <div style={{ fontSize: 13, lineHeight: 1.6 }}>
            <strong>Your withholding tax type changed during {year}.</strong>{' '}
            NRWHT was applied for some months ({fmt(report.nrwhtWithheld)}) and RIWT for others ({fmt(report.riwtWithheld)}).
            Both rates are 10% — no extra tax was charged. If you believe NRWHT was incorrectly
            applied, contact FRCS on 3243000 or visit{' '}
            <a href="https://tpos.frcs.org.fj" target="_blank" rel="noreferrer">tpos.frcs.org.fj</a>.
          </div>
        </div>
      )}

      {/* Just-submitted success */}
      {justSubmitted && submitRef && (
        <div className="alert alert-success" style={{ marginBottom: 16 }}>
          ✅ <strong>Return submitted to Bank of Fiji.</strong> Reference:{' '}
          <code>{submitRef}</code>. The bank will forward this to FRCS and notify you.
        </div>
      )}

      {error && <div className="alert alert-error">⚠️ {error}</div>}

      {loading ? (
        <div className="tax-loading"><div className="spinner"/><p>Loading…</p></div>
      ) : report ? (
        <>
          {/* RIWT rejection notice */}
          {isRiwtRejected && !isRiwtExempt && (
            <div className="alert alert-error" style={{ display:'flex', alignItems:'flex-start', gap:10 }}>
              <span style={{ fontSize:20 }}>⚠️</span>
              <div>
                <strong>Your RIWT exemption was not approved.</strong> RIWT (10%) is being
                deducted from your interest. Upload a new certificate below or apply for a
                refund through the FRCS portal.
              </div>
            </div>
          )}

          {/* Taxpayer identity */}
          <div className="tax-card taxpayer-info">
            <div className="info-grid">
              {[
                ['Name',         report.fullName],
                ['Tax year',     report.taxYear],
                ['Customer ID',  report.customerId],
                ['Tax ID (TIN)', report.tinNumber],
              ].map(([label, val]) => (
                <div className="info-item" key={label}>
                  <span className="info-label">{label}</span>
                  <span className="info-value mono">{val}
                    {label === 'Tax ID (TIN)' && val === 'Not Provided' && (
                      <span className="tin-warning-badge">! NRWHT applies</span>
                    )}
                  </span>
                </div>
              ))}
              <div className="info-item">
                <span className="info-label">Residency</span>
                <span className={`badge ${report.isResident ? 'badge-green' : 'badge-yellow'}`}>
                  {report.isResident ? 'Fiji resident' : 'Non-resident'}
                </span>
              </div>
              <div className="info-item">
                <span className="info-label">Savings interest rate</span>
                <span className="badge badge-purple">{report.interestRate ?? 'Not set'}</span>
              </div>
              {report.isSeniorCitizen && (
                <div className="info-item">
                  <span className="info-label">Senior citizen</span>
                  <span className="badge badge-purple">No tax on interest</span>
                </div>
              )}
              {isRiwtExempt && (
                <div className="info-item">
                  <span className="info-label">RIWT exemption</span>
                  <span className="badge badge-green">Approved</span>
                </div>
              )}
              {isRiwtRejected && !isRiwtExempt && (
                <div className="info-item">
                  <span className="info-label">RIWT exemption</span>
                  <span className="badge badge-red">Not approved</span>
                </div>
              )}
              <div className="info-item">
                <span className="info-label">Return status</span>
                <span className={`badge ${isFrcsSubmitted ? 'badge-green' : isPendingFrcs ? 'badge-blue' : 'badge-yellow'}`}>
                  {isFrcsSubmitted ? 'Submitted to FRCS' : isPendingFrcs ? 'Pending FRCS submission' : 'Draft'}
                </span>
              </div>
              {isFrcsSubmitted && report.frcsReference && (
                <div className="info-item">
                  <span className="info-label">FRCS reference</span>
                  <span className="info-value mono badge badge-green">{report.frcsReference}</span>
                </div>
              )}
            </div>
          </div>

          {/* Interest summary */}
          <div className="tax-card interest-panel">
            <h2 className="card-title">
              Savings interest — {report.taxYear}
              {report.interestRate && (
                <span className="card-title-badge badge-purple" style={{ fontSize:12 }}>
                  {report.interestRate}
                </span>
              )}
            </h2>

            {/* ── If BOTH nrwht and riwt: show two rows ── */}
            {midYearChange ? (
              <div className="income-grid" style={{ marginBottom: 16 }}>
                <div className="income-row income-row-highlight">
                  <span>Gross interest earned</span>
                  <span className="positive">{fmt(report.interestEarned)}</span>
                </div>
                <div className="income-row">
                  <span>
                    <span className="tag tag-orange" style={{ marginRight: 8, fontSize: 11 }}>NRWHT</span>
                    Withheld (no TIN period)
                  </span>
                  <span className="negative">−{fmt(report.nrwhtWithheld)}</span>
                </div>
                <div className="income-row">
                  <span>
                    <span className="tag tag-blue" style={{ marginRight: 8, fontSize: 11 }}>RIWT</span>
                    Withheld (after residency/TIN change)
                  </span>
                  <span className="negative">−{fmt(report.riwtWithheld)}</span>
                </div>
                <div className="income-row income-row-highlight">
                  <span><strong>Net interest credited to account</strong></span>
                  <span className="positive"><strong>{fmt(report.netInterestPaid)}</strong></span>
                </div>
              </div>
            ) : (
              <div className="interest-stats">
                <div className="interest-stat">
                  <span className="istat-label">Gross interest earned on savings</span>
                  <span className="istat-amount positive">{fmt(report.interestEarned)}</span>
                  <span className="istat-sub">Credited to your savings accounts in {report.taxYear}</span>
                </div>
                <div className="interest-stat-divider"/>
                <div className="interest-stat">
                  <span className="istat-label">
                    {report.isSeniorCitizen || isRiwtExempt ? 'Tax withheld'
                      : withholding?.wasRefunded ? 'NRWHT withheld (refunded to your account)'
                      : withholding?.title ?? 'Tax withheld'}
                    {withholding && !isRiwtExempt && (
                      <span className={`tag ${withholding.tagClass}`}
                        style={{ marginLeft:8, fontSize:11 }}>{withholding.code}</span>
                    )}
                    {withholding?.wasRefunded && (
                      <span className="tag tag-green" style={{ marginLeft:6, fontSize:11 }}>REFUNDED</span>
                    )}
                    {isRiwtExempt && (
                      <span className="tag tag-green" style={{ marginLeft:8, fontSize:11 }}>Exempt</span>
                    )}
                  </span>
                  <span className="istat-amount" style={{ color: withholding?.wasRefunded ? 'var(--success)' : 'var(--danger)' }}>
                    {report.isSeniorCitizen || isRiwtExempt
                      ? 'FJD 0.00'
                      : withholding?.wasRefunded
                        ? `${fmt(withheldAmt)} ↩ refunded`
                        : `-${fmt(withheldAmt)}`}
                  </span>
                  <span className="istat-sub">
                    {report.isSeniorCitizen ? 'Senior citizen — no tax on interest.'
                      : isRiwtExempt        ? 'FRCS exemption on file — no tax on interest.'
                      : withholding?.wasRefunded ? 'NRWHT was deducted before your TIN was registered. The full amount has been refunded to your savings account.'
                      : withheldAmt > 0     ? `${withholdPct}% of gross interest sent to FRCS.`
                      : 'No tax withheld.'}
                  </span>
                </div>
                <div className="interest-stat-divider"/>
                <div className="interest-stat highlight">
                  <span className="istat-label">Interest credited to your account</span>
                  <span className="istat-amount positive">{fmt(report.netInterestPaid)}</span>
                  <span className="istat-sub">
                    Gross interest minus tax withheld
                    {withholding?.wasRefunded && ' — refund also credited separately'}
                  </span>
                </div>
              </div>
            )}

            {/* Interest bar — hide for refunded NRWHT since net = gross */}
            {interestGross > 0 && !withholding?.wasRefunded && (
              <div className="interest-bar-wrap">
                <div className="interest-bar-labels">
                  <span>Received ({(100 - parseFloat(withholdPct)).toFixed(1)}%)</span>
                  {!report.isSeniorCitizen && !isRiwtExempt && withheldAmt > 0 && (
                    <span>FRCS ({withholdPct}%)</span>
                  )}
                </div>
                <div className="interest-bar">
                  <div className="interest-bar-net"
                    style={{ width: (report.isSeniorCitizen || isRiwtExempt) ? '100%' : `${100 - parseFloat(withholdPct)}%` }}/>
                  {!report.isSeniorCitizen && !isRiwtExempt && withheldAmt > 0 && (
                    <div className="interest-bar-withheld" style={{ width:`${withholdPct}%` }}/>
                  )}
                </div>
              </div>
            )}

            {withholding?.fix && (
              <div className="interest-notice">
                <span className="notice-icon">💡</span>
                <div><strong>You could pay less tax.</strong> {withholding.fix}</div>
              </div>
            )}
            {report.isSeniorCitizen && (
              <div className="interest-notice notice-success">
                <span className="notice-icon">✅</span>
                <div><strong>Senior citizen exemption.</strong> No tax deducted from interest.</div>
              </div>
            )}
            {isRiwtExempt && !report.isSeniorCitizen && (
              <div className="interest-notice notice-success">
                <span className="notice-icon">✅</span>
                <div><strong>RIWT exemption active.</strong> Your FRCS certificate is on file. No RIWT deducted.</div>
              </div>
            )}
          </div>

          {/* Summary cards */}
          <div className="summary-grid">
            <div className="summary-card green">
              <span className="summary-label">Money received</span>
              <span className="summary-amount">{fmt(report.grossIncome)}</span>
              <span className="summary-sub">{report.transactionCount} transactions</span>
            </div>
            <div className="summary-card red">
              <span className="summary-label">Total tax</span>
              <span className="summary-amount">{fmt(report.totalTaxOwed)}</span>
              <span className="summary-sub">PAYE + RIWT/NRWHT + VAT</span>
            </div>
            <div className="summary-card purple">
              <span className="summary-label">Interest earned</span>
              <span className="summary-amount">{fmt(report.interestEarned)}</span>
              <span className="summary-sub">Received: {fmt(report.netInterestPaid)}</span>
            </div>
            {nrwhtWasRefunded && riwtAmt === 0 && (
              <div className="summary-card" style={{ background: 'rgba(16,185,129,0.08)', border: '1px solid rgba(16,185,129,0.3)' }}>
                <span className="summary-label" style={{ color: 'var(--success)' }}>NRWHT Refunded</span>
                <span className="summary-amount" style={{ color: 'var(--success)' }}>+{fmt(report.nrwhtWithheld)}</span>
                <span className="summary-sub">Credited to your savings account</span>
              </div>
            )}
          </div>

          {/* RIWT exemption upload block */}
          {showExemptBlock && (
            <div className="tax-card">
              <h2 className="card-title">
                {isRiwtRejected ? '⚠️ Re-submit RIWT Exemption' : 'RIWT Exemption — stop interest tax'}
              </h2>
              <p style={{ fontSize:14, color:'var(--text-secondary)', marginBottom:20, lineHeight:1.6 }}>
                {isRiwtRejected
                  ? 'Your previous certificate was not verified. Upload a new one below.'
                  : 'If FRCS has issued you a Certificate of Exemption from RIWT, upload it here. ' +
                    'The bank will review and stop deducting RIWT from your interest within 2 business days.'}
              </p>
              <div style={{ display:'grid', gridTemplateColumns:'repeat(auto-fit,minmax(260px,1fr))', gap:16 }}>
                <div style={{ background:'var(--navy-50,#f0f5fd)', border:'1px solid var(--navy-100,#dce8f8)', borderRadius:8, padding:18 }}>
                  <p style={{ fontWeight:600, fontSize:14, marginBottom:6 }}>📄 Upload your exemption certificate</p>
                  <p style={{ fontSize:13, color:'var(--text-secondary)', marginBottom:14, lineHeight:1.55 }}>PDF, JPG or PNG, max 10 MB.</p>
                  {exemptUploaded ? (
                    <div className="alert alert-success" style={{ marginBottom:0 }}>
                      ✅ Uploaded. We'll review within 2 business days.
                    </div>
                  ) : (
                    <>
                      <input ref={fileInputRef} type="file" accept=".pdf,.jpg,.jpeg,.png"
                        style={{ display:'none' }}
                        onChange={e => setExemptFile(e.target.files[0] ?? null)} />
                      <div style={{ display:'flex', gap:8, flexWrap:'wrap' }}>
                        <button className="btn-outline" style={{ fontSize:13, padding:'7px 14px' }}
                          onClick={() => fileInputRef.current?.click()}>
                          {exemptFile ? `📎 ${exemptFile.name}` : '📎 Choose file'}
                        </button>
                        {exemptFile && (
                          <button className="btn-primary" style={{ fontSize:13, padding:'7px 14px' }}
                            disabled={exemptUploading} onClick={handleExemptUpload}>
                            {exemptUploading ? 'Uploading…' : '⬆ Send to bank'}
                          </button>
                        )}
                      </div>
                      {exemptError && <div className="alert alert-error" style={{ marginTop:10 }}>⚠️ {exemptError}</div>}
                    </>
                  )}
                </div>
                <div style={{ background:'var(--amber-50,#fefbec)', border:'1px solid rgba(201,125,0,0.20)', borderRadius:8, padding:18 }}>
                  <p style={{ fontWeight:600, fontSize:14, marginBottom:6 }}>💰 Apply for a refund from FRCS</p>
                  <p style={{ fontSize:13, color:'var(--text-secondary)', marginBottom:14, lineHeight:1.55 }}>
                    If RIWT was deducted when it shouldn't have been, claim it back through the FRCS Taxpayer Portal.
                  </p>
                  <a href="https://tpos.frcs.org.fj/taxpayerportal/#/Logon"
                    target="_blank" rel="noopener noreferrer" className="btn-primary"
                    style={{ display:'inline-flex', alignItems:'center', gap:6, fontSize:13, padding:'8px 14px', textDecoration:'none', borderRadius:6 }}>
                    Go to FRCS portal ↗
                  </a>
                </div>
              </div>
            </div>
          )}

          {/* Tax breakdown */}
          <div className="tax-card">
            <h2 className="card-title">Full tax breakdown</h2>
            <table className="tax-table">
              <thead><tr><th>Tax type</th><th>Rate</th><th>Amount</th></tr></thead>
              <tbody>
                {report.isResident && (
                  <tr>
                    <td><span className="tag tag-red">PAYE</span>
                      <span style={{ marginLeft:8, fontSize:13 }}>Personal income tax (0–20%)</span></td>
                    <td>0%–20%</td>
                    <td className="amountright">{fmt(report.payeOwed)}</td>
                  </tr>
                )}
                {/* Show NRWHT row if it was charged */}
                {nrwhtAmt > 0 && !isRiwtExempt && (
                  <tr>
                    <td>
                      <span className="tag tag-orange">NRWHT</span>
                      <span style={{ marginLeft:8, fontSize:13 }}>
                        Non-resident/no-TIN withholding
                        {nrwhtWasRefunded && riwtAmt === 0 && (
                          <span className="tag tag-green" style={{ marginLeft:8, fontSize:11 }}>REFUNDED</span>
                        )}
                      </span>
                    </td>
                    <td>10%</td>
                    <td className="amountright" style={{ color: nrwhtWasRefunded && riwtAmt === 0 ? 'var(--success)' : undefined }}>
                      {nrwhtWasRefunded && riwtAmt === 0
                        ? <span>{fmt(report.nrwhtWithheld)} <span style={{ fontSize:11 }}>↩ refunded</span></span>
                        : fmt(report.nrwhtWithheld)}
                    </td>
                  </tr>
                )}
                {/* Show RIWT row if it was charged */}
                {riwtAmt > 0 && !isRiwtExempt && (
                  <tr>
                    <td><span className="tag tag-blue">RIWT</span>
                      <span style={{ marginLeft:8, fontSize:13 }}>Resident withholding tax on savings</span></td>
                    <td>10%</td>
                    <td className="amountright">{fmt(report.riwtWithheld)}</td>
                  </tr>
                )}
                {(isRiwtExempt || report.isSeniorCitizen) && (
                  <tr>
                    <td><span className="tag tag-green">EXEMPT</span>
                      <span style={{ marginLeft:8, fontSize:13 }}>
                        {report.isSeniorCitizen ? 'Senior citizen' : 'FRCS certificate on file'}
                      </span></td>
                    <td>—</td>
                    <td className="amountright">FJD 0.00</td>
                  </tr>
                )}
                <tr>
                  <td><span className="tag tag-yellow">VAT</span>
                    <span style={{ marginLeft:8, fontSize:13 }}>Value Added Tax on bank fees</span></td>
                  <td>15%</td>
                  <td className="amountright">{fmt(report.vatOnFees)}</td>
                </tr>
                <tr className="total-row">
                  <td colSpan={2}><strong>Total tax liability</strong></td>
                  <td className="amount" style={{ textAlign:'right' }}><strong>{fmt(report.totalTaxOwed)}</strong></td>
                </tr>
              </tbody>
            </table>

            <h3 style={{ fontFamily:"'DM Serif Display',serif", fontWeight:400, fontSize:16, margin:'20px 0 10px' }}>
              Income detail
            </h3>
            <div className="income-grid">
              <div className="income-row"><span>Total credits (income)</span><span className="positive">{fmt(report.totalCredits)}</span></div>
              <div className="income-row"><span>Total debits (expenses)</span><span className="negative">−{fmt(report.totalDebits)}</span></div>
              <div className="income-row income-row-highlight">
                <span>Gross savings interest ({report.interestRate})</span>
                <span className="positive">{fmt(report.interestEarned)}</span>
              </div>
              {nrwhtAmt > 0 && !isRiwtExempt && (
                <div className="income-row">
                  <span>
                    NRWHT — 10% of interest
                    {nrwhtWasRefunded && riwtAmt === 0 && <span style={{ marginLeft:8, fontSize:12, color:'var(--success)' }}>(refunded to your account)</span>}
                  </span>
                  <span className={nrwhtWasRefunded && riwtAmt === 0 ? 'positive' : 'negative'}>
                    {nrwhtWasRefunded && riwtAmt === 0 ? `↩ ${fmt(report.nrwhtWithheld)}` : `−${fmt(report.nrwhtWithheld)}`}
                  </span>
                </div>
              )}
              {riwtAmt > 0 && !isRiwtExempt && (
                <div className="income-row">
                  <span>RIWT — 10% of interest</span>
                  <span className="negative">−{fmt(report.riwtWithheld)}</span>
                </div>
              )}
              <div className="income-row income-row-highlight">
                <span>Net interest credited</span>
                <span className="positive">{fmt(report.netInterestPaid)}</span>
              </div>
              <div className="income-row"><span>Bank fees</span><span className="negative">−{fmt(report.bankFeesCharged)}</span></div>
            </div>

            {/* Monthly breakdown */}
            {report.monthlyBreakdown && (
              <>
                <h3 style={{ fontFamily:"'DM Serif Display',serif", fontWeight:400, fontSize:16, margin:'20px 0 10px' }}>
                  Month by month
                </h3>
                <div style={{ overflowX:'auto' }}>
                  <table className="tax-table">
                    <thead>
                      <tr>
                        <th>Month</th><th>Income</th><th>Gross interest</th>
                        <th>Tax withheld</th>
                        <th>Net interest</th>
                      </tr>
                    </thead>
                    <tbody>
                      {report.monthlyBreakdown.map(m => {
                        const net = (parseFloat(m.interest) - parseFloat(m.taxWithheld || 0)).toFixed(2);
                        return (
                          <tr key={m.month}>
                            <td>{m.month}</td>
                            <td>{fmt(m.income)}</td>
                            <td className="amount">{fmt(m.interest)}</td>
                            <td className="amount negative-text">
                              {(isRiwtExempt || report.isSeniorCitizen) ? '—'
                                : parseFloat(m.taxWithheld) > 0 ? `−${fmt(m.taxWithheld)}` : '—'}
                            </td>
                            <td className="amount positive-text">{fmt(net)}</td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </>
            )}
          </div>

          {/* Submit / Status */}
          <div className="tax-card actions-card no-print">
            {isFrcsSubmitted ? (
              <div className="submit-success">
                <span className="success-icon">✅</span>
                <div>
                  <p><strong>Submitted to FRCS by Bank of Fiji.</strong></p>
                  {report.frcsReference && <p className="ref-number">FRCS Reference: <code>{report.frcsReference}</code></p>}
                  <p className="hint">Your {report.taxYear} return has been lodged. No further changes can be made.</p>
                </div>
              </div>
            ) : isPendingFrcs ? (
              <div className="submit-success">
                <span className="success-icon">⏳</span>
                <div>
                  <p><strong>Return received by Bank of Fiji.</strong></p>
                  {report.frcsReference && <p className="ref-number">Bank Reference: <code>{report.frcsReference}</code></p>}
                  <p className="hint">Pending FRCS submission. You will receive a notification once forwarded.</p>
                </div>
              </div>
            ) : !confirmingSubmit ? (
              <div className="actions-row">
                <div className="actions-info">
                  <h3>Ready to submit?</h3>
                  <p>Sends your {report.taxYear} return to Bank of Fiji for forwarding to FRCS.</p>
                </div>
                <div className="actions-buttons">
                  <button className="btn-outline" onClick={handlePrint}>⬇ Download PDF</button>
                  <button className="btn-outline" onClick={handleExportCSV}>↓ Export CSV</button>
                  <button className="btn-primary" onClick={() => setConfirmingSubmit(true)}>
                    📤 Submit to Bank
                  </button>
                </div>
              </div>
            ) : (
              <div style={{ background:'#fef2f2', border:'1px solid #fecaca', borderRadius:8, padding:'14px 16px', display:'flex', flexDirection:'column', gap:10 }}>
                <p style={{ margin:0, fontSize:13, color:'#991b1b', fontWeight:600 }}>
                  ⚠️ This will submit your {report.taxYear} tax return to Bank of Fiji. This cannot be undone.
                </p>
                <div style={{ display:'flex', gap:8 }}>
                  <button className="btn-outline" style={{ fontSize:13 }}
                    onClick={() => setConfirmingSubmit(false)} disabled={submitting}>Cancel</button>
                  <button className="btn-primary" style={{ fontSize:13, background:'#dc2626' }}
                    disabled={submitting} onClick={handleSubmit}>
                    {submitting ? 'Submitting…' : 'Confirm — Submit to Bank'}
                  </button>
                </div>
              </div>
            )}
          </div>

          <p className="tax-disclaimer">
            * Based on your Bank of Fiji activity for {report.taxYear}.
            Savings interest rate {report.interestRate ?? 'N/A'} per RBF directive.
            RIWT/NRWHT 10% per the Fiji Income Tax Act. Contact FRCS on 3243000 for queries.
          </p>
        </>
      ) : (
        <div className="tax-loading"><p>No data found for {year}. Try a different year above.</p></div>
      )}
    </div>
  );
};

export default TaxReportPage;