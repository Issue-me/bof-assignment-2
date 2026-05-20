import React, { useState, useEffect } from 'react';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';
import ConfirmDialog from '../components/ConfirmDialog';
import './TaxReport.css';
import './FrcsReport.css';
import { useNavigate } from 'react-router-dom';

/**
 * FrcsReportPage — Admin / Teller only.
 * Shows annual interest summary for all customers for FRCS remittance.
 */
const FrcsReportPage = () => {
  const { role } = useAuth();
  const isAdmin = String(role || '').trim().toUpperCase().replace(/^ROLE_/, '') === 'ADMIN';
  const navigate = useNavigate();
  const [report, setReport]               = useState(null);
  const [loading, setLoading]             = useState(true);
  const [error, setError]                 = useState('');
  const [year, setYear]                   = useState(new Date().getFullYear());
  const [recalculating, setRecalculating] = useState(false);
  const [submitting, setSubmitting]       = useState(false);
  const [statusMsg, setStatusMsg]         = useState('');
  const [statusType, setStatusType]       = useState('info');
  const [showConfirm, setShowConfirm]     = useState(false);

  const currentYear = new Date().getFullYear();
  const yearOptions = Array.from({ length: 5 }, (_, i) => currentYear - i);

  useEffect(() => { fetchReport(); }, [year]);

  const fetchReport = async () => {
    setLoading(true); setError(''); setStatusMsg('');
    try {
      const res = await api.get(`/tax/frcs/interest-summary?year=${year}`);
      setReport(res.data);
    } catch (err) {
      setError(
        err.response?.status === 403
          ? 'Access denied. This page is restricted to administrators and tellers.'
          : 'Failed to load FRCS report. Please try again.'
      );
    } finally { setLoading(false); }
  };

  const handleRecalculate = async () => {
    setRecalculating(true); setStatusMsg('');
    try {
      const res = await api.post(`/tax/admin/recalculate?year=${year}`);
      setStatusMsg(`Recalculation complete — ${res.data.usersProcessed} customers processed.`);
      setStatusType('info');
      await fetchReport();
    } catch { setError('Recalculation failed. Please try again.'); }
    finally   { setRecalculating(false); }
  };

  const handleMarkSubmitted = async () => {
    setShowConfirm(true);
  };

  const confirmMarkSubmitted = async () => {
    setShowConfirm(false);
    setSubmitting(true); setStatusMsg('');
    try {
      await api.post(`/tax/admin/mark-submitted?year=${year}`);
      setStatusMsg(`All ${year} summaries marked as submitted to FRCS.`);
      setStatusType('success');
      await fetchReport();
    } catch { setError('Failed to mark summaries as submitted.'); }
    finally   { setSubmitting(false); }
  };

  const fmt = (val) =>
    val != null
      ? `FJD ${parseFloat(val).toLocaleString('en-FJ', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
      : 'FJD 0.00';

  // FIX: use toFixed(2) before summing to avoid JS floating-point rounding drift
  const precise = (v) => Math.round(parseFloat(v ?? 0) * 100) / 100;

  const records = report?.userRecords ?? [];

  // Compute totals from records (server totals are authoritative, but these are used for cross-check)
  const nrwhtCount  = records.filter(r => precise(r.nrwhtWithheld)  > 0).length;
  const riwtCount   = records.filter(r => precise(r.riwtWithheld)   > 0).length;
  const exemptCount = records.filter(r => r.exemptionReason).length;
  const refundedCount = records.filter(r => r.nrwhtRefunded).length;

  // Use server-supplied totals — precise BigDecimal values from Java
  const totalGross    = precise(report?.totalGrossInterestPaid);
  const totalRiwt     = precise(report?.totalRiwtWithheld);
  const totalNrwht    = precise(report?.totalNrwhtWithheld);
  const totalNrwhtRefunded = precise(
    report?.totalNrwhtRefunded ?? records
      .filter(r => r.nrwhtRefunded)
      .reduce((sum, r) => sum + precise(r.nrwhtWithheld), 0)
  );
  const totalNrwhtToRemit = Math.max(0, Math.round((totalNrwht - totalNrwhtRefunded) * 100) / 100);
  const totalWithheld = precise(report?.totalWithholdingToRemit ?? (totalRiwt + totalNrwhtToRemit));
  const totalNet      = Math.round((totalGross - totalWithheld) * 100) / 100;

  // Expected values at 3.5% for reference (shown in log, not UI)
  // Perry:  100,000 × 0.035 / 12 × months = 291.67/month
  // Adrian: 6,000   × 0.035 / 12 × months = 17.50/month

  const handleExportCSV = () => {
    if (!report) return;

    const fmtNum = (v) => v != null ? `FJD ${parseFloat(v).toFixed(2)}` : 'FJD 0.00';
    const columns = [
      'Section',
      'Field',
      'Value',
      'Customer ID',
      'Full Name',
      'TIN',
      'Residency',
      'Senior Citizen',
      'Exemption',
      'Gross Interest (FJD)',
      'Tax Type',
      'Tax Withheld (FJD)',
      'Net Paid (FJD)',
      'NRWHT Refunded',
      'Notes',
    ];
    const pad = (row) => [...row, ...Array(Math.max(0, columns.length - row.length)).fill('')];
    const rows = [
      columns,
      pad(['Report', 'Title', 'BANK OF FIJI - FRCS INTEREST SUMMARY REPORT']),
      pad(['Report', 'Tax Year', report.taxYear]),
      pad(['Report', 'Generated', report.reportGeneratedDate]),
      pad(['Report', 'Bank', report.bankName]),
      pad(['Report', 'Bank TIN', report.bankTin]),
      pad(['Summary Totals', 'Total Gross Interest Paid', fmtNum(report.totalGrossInterestPaid)]),
      pad(['Summary Totals', 'Total RIWT Withheld', fmtNum(report.totalRiwtWithheld)]),
      pad(['Summary Totals', 'Total NRWHT Withheld', fmtNum(report.totalNrwhtWithheld)]),
      pad(['Summary Totals', 'Total NRWHT Refunded', `-${fmtNum(totalNrwhtRefunded)}`]),
      pad(['Summary Totals', 'Net NRWHT To Remit', `-${fmtNum(totalNrwhtToRemit)}`]),
      pad(['Summary Totals', 'Total Withholding Tax to Remit to FRCS', `-${fmtNum(totalWithheld)}`]),
      pad(['Summary Totals', 'Total Net Interest Received by Customers', fmtNum(totalNet)]),
      pad(['Summary Totals', 'Total Customers with Interest', records.length]),
      pad(['Summary Totals', 'Resident customers with TIN (RIWT)', riwtCount]),
      pad(['Summary Totals', 'Non-resident / no-TIN customers (NRWHT)', nrwhtCount]),
      pad(['Summary Totals', 'Exemptions (Senior citizen or RIWT cert)', exemptCount]),
      ...records.map(r => {
        const nrwht = precise(r.nrwhtWithheld);
        const riwt  = precise(r.riwtWithheld);
        const wType = nrwht > 0 ? 'NRWHT' : riwt > 0 ? 'RIWT' : r.exemptionReason ? 'Exempt' : 'None';
        const wAmt  = nrwht > 0 ? r.nrwhtWithheld : riwt > 0 ? r.riwtWithheld : '0.00';
        const expectedNet = Math.round((precise(r.grossInterestEarned) - nrwht - riwt) * 100) / 100;
        const netOk = Math.abs(expectedNet - precise(r.netInterestPaid)) < 0.01;
        return pad([
          'Customer Interest Records',
          '',
          '',
          r.customerId,
          r.fullName,
          r.tinNumber || 'Not Provided',
          r.isResident ? 'Fiji Resident' : 'Non-Resident',
          r.seniorCitizen ? 'Yes' : 'No',
          r.exemptionReason
            ? r.exemptionReason === 'SENIOR_CITIZEN_EXEMPTION' ? 'Senior citizen' : 'RIWT exempt'
            : '—',
          fmtNum(r.grossInterestEarned),
          wType,
          wType === 'Exempt' ? 'FJD 0.00' : `-${fmtNum(wAmt)}`,
          netOk ? fmtNum(r.netInterestPaid) : fmtNum(expectedNet),
          r.nrwhtRefunded ? `YES (${r.nrwhtRefundReference || 'N/A'})` : 'NO',
          netOk ? '' : 'Net interest recalculated',
        ]);
      }),
      pad(['Withholding Remittance Summary', 'Total gross interest paid to customers', fmtNum(totalGross)]),
      pad([
        'Withholding Remittance Summary',
        `RIWT withheld — ${riwtCount} resident customers with TIN (10%)`,
        `-${fmtNum(totalRiwt)}`,
      ]),
      pad([
        'Withholding Remittance Summary',
        `NRWHT withheld — ${nrwhtCount} non-resident / no-TIN customers (10%)`,
        `-${fmtNum(totalNrwht)}`,
      ]),
      pad(['Withholding Remittance Summary', 'Less NRWHT refunded to eligible customers', `-${fmtNum(totalNrwhtRefunded)}`]),
      pad(['Withholding Remittance Summary', 'Net NRWHT to remit', `-${fmtNum(totalNrwhtToRemit)}`]),
      pad(['Withholding Remittance Summary', 'TOTAL WITHHOLDING TAX TO REMIT TO FRCS', `-${fmtNum(totalWithheld)}`]),
      pad(['Withholding Remittance Summary', 'Total net interest received by customers', fmtNum(totalNet)]),
      pad(['Disclaimer', 'Line', `This report covers all customers who earned interest in ${report.taxYear}.`]),
      pad(['Disclaimer', 'Line', `Generated by ${report.bankName} — Bank TIN: ${report.bankTin}.`]),
      pad(['Disclaimer', 'Line', 'RIWT/NRWHT 10% per the Fiji Income Tax Act. Contact FRCS on 3243000 for queries.']),
    ];

    const BOM = '\uFEFF';
    const csv = BOM + rows.map(r =>
      r.map(c => `"${String(c ?? '').replace(/"/g, '""')}"`).join(',')
    ).join('\r\n');

    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url  = URL.createObjectURL(blob);
    const a    = document.createElement('a');
    a.href     = url;
    a.download = `FRCS-Interest-Summary-${report.taxYear}-${report.bankName.replace(/\s+/g, '-')}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="frcs-report-page">
      <div className="page-header">
        <h1>FRCS Interest Summary</h1>
        <p>Bank of Fiji annual interest report for all customers.</p>
      </div>

      <div className="card">
        <div className="frcs-controls no-print">
          <select
            className="year-select"
            value={year}
            onChange={(e) => setYear(parseInt(e.target.value))}
          >
            {yearOptions.map(y => (
              <option key={y} value={y}>{y} Tax Year</option>
            ))}
          </select>
          {isAdmin && (
            <button className="btn-outline" onClick={handleRecalculate} disabled={recalculating}>
              {recalculating ? 'Recalculating…' : '↻ Recalculate'}
            </button>
          )}
          <button className="btn-outline" onClick={() => window.print()}>
            ↓ Download PDF
          </button>
          <button className="btn-outline" onClick={handleExportCSV} disabled={!report || loading}>
            ↓ Export CSV
          </button>

          {isAdmin && (
            <button
              className="btn-outline"
              onClick={() => navigate('/admin/frcs-report-submission')}
            >
              📄 View Submissions
            </button>
          )}
        </div>

        {error && <div className="alert alert-error">! {error}</div>}
        {statusMsg && (
          <div className={`alert ${statusType === 'success' ? 'alert-success' : 'alert-info'}`}>
            {statusType === 'success' ? '✓' : 'i'} {statusMsg}
          </div>
        )}

      {loading ? (
        <div className="tax-loading"><div className="spinner"/><p>Loading FRCS interest summary…</p></div>
      ) : report ? (
        <>
          {/* Report metadata */}
          <div className="tax-card taxpayer-info">
            <div className="info-grid">
              {[
                ['Bank',                   report.bankName],
                ['Bank TIN',               report.bankTin],
                ['Tax Year',               report.taxYear],
                ['Report Generated',       report.reportGeneratedDate],
                ['Customers with Interest', records.length],
              ].map(([l, v]) => (
                <div className="info-item" key={l}>
                  <span className="info-label">{l}</span>
                  <span className="info-value">{v}</span>
                </div>
              ))}
            </div>
          </div>

          {/* Summary cards */}
          <div className="summary-grid">
            <div className="summary-card green">
              <span className="summary-label">Total Gross Interest Paid</span>
              <span className="summary-amount">{fmt(report.totalGrossInterestPaid)}</span>
              <span className="summary-sub">Across all {records.length} customers</span>
            </div>
            <div className="summary-card blue">
              <span className="summary-label">Total RIWT Withheld</span>
              <span className="summary-amount">{fmt(report.totalRiwtWithheld)}</span>
              <span className="summary-sub">{riwtCount} resident customers with TIN</span>
            </div>
            <div className="summary-card red">
              <span className="summary-label">Net NRWHT To Remit</span>
              <span className="summary-amount">{fmt(totalNrwhtToRemit)}</span>
              <span className="summary-sub">Withheld {fmt(totalNrwht)} less refunded {fmt(totalNrwhtRefunded)}</span>
            </div>
            <div className="summary-card purple">
              <span className="summary-label">Exemptions</span>
              <span className="summary-amount">{exemptCount}</span>
              <span className="summary-sub">Senior citizen or RIWT cert</span>
            </div>
          </div>

          {/* Customer records table */}
          <div className="tax-card">
            <h2 className="card-title">Customer Interest Records — {year}</h2>

            {/* Rate note */}
            <div className="interest-notice" style={{ marginBottom: 16 }}>
              <span className="notice-icon">i</span>
              <div style={{ fontSize: 13 }}>
                RIWT and NRWHT are charged at <strong>10%</strong> of gross interest per the Fiji Income Tax Act.
                Senior citizens and customers with an approved FRCS Certificate of Exemption pay <strong>0%</strong>.
              </div>
            </div>

            {records.length === 0 ? (
              <p style={{ color: 'var(--text-muted)', padding: '32px 0', textAlign: 'center' }}>
                No customers earned interest in {year}.
              </p>
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table className="tax-table">
                  <thead>
                    <tr>
                      <th>Customer ID</th>
                      <th>Full Name</th>
                      <th>TIN</th>
                      <th>Residency</th>
                      <th>Gross Interest</th>
                      <th>Tax Withheld</th>
                      <th>Net Paid</th>
                      <th style={{ textAlign: 'left' }}>NRWHT Refunded</th>                      
                      <th style={{textAlign: 'left'}}>Submission Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {records.map(r => {
                      const nrwht   = precise(r.nrwhtWithheld);
                      const riwt    = precise(r.riwtWithheld);
                      const isNrwht = nrwht > 0;
                      const isRiwt  = riwt  > 0;
                      const wType   = isNrwht ? 'NRWHT' : isRiwt ? 'RIWT' : null;
                      const wCls    = isNrwht ? 'tag-orange' : 'tag-blue';
                      const wAmt    = isNrwht ? r.nrwhtWithheld : r.riwtWithheld;

                      // Verify: net should equal gross − withheld
                      const expectedNet = Math.round(
                        (precise(r.grossInterestEarned) - nrwht - riwt) * 100) / 100;
                      const netOk = Math.abs(expectedNet - precise(r.netInterestPaid)) < 0.01;

                      return (
                        <tr key={r.customerId}>
                          <td><span className="info-value mono" style={{ fontSize: 13, whiteSpace: 'nowrap' }}>{r.customerId}</span></td>
                          <td>
                            <span style={{ display: 'block' }}>{r.fullName}</span>
                            {r.exemptionReason && (
                              <span className="tag tag-purple" style={{ marginTop: 4, fontSize: 11 }}>
                                {r.exemptionReason === 'SENIOR_CITIZEN_EXEMPTION' ? 'Senior citizen' : 'RIWT exempt'}
                              </span>
                            )}
                          </td>
                          <td>
                            {r.tinNumber
                              ? <span className="info-value mono" style={{ fontSize: 13, whiteSpace: 'nowrap' }}>{r.tinNumber}</span>
                              : <span className="badge badge-yellow">No TIN</span>}
                          </td>
                          <td>
                            <span className={`badge ${r.isResident ? 'badge-green' : 'badge-yellow'}`}>
                              {r.isResident ? 'Resident' : 'Non-Resident'}
                            </span>
                          </td>
                          <td className="amount">{(r.grossInterestEarned)}</td>
                          <td className="amount negative-text">
                            {r.exemptionReason ? (
                              <span style={{ color: 'var(--success)' }}>Exempt — 0.00</span>
                            ) : wType ? (
                              <span style={{ whiteSpace: 'nowrap' }}>
                                <span className={`tag ${wCls}`} style={{ marginRight: 6, fontSize: 11 }}>{wType}</span>
                                −{(wAmt)}
                              </span>
                            ) : '0.00'}
                          </td>
                          <td className="amount positive-text" style={{ whiteSpace: 'nowrap', textAlign: 'left' }}>
                            {/* FIX: show computed net (gross − withheld) if server net differs by rounding */}
                            {netOk ? (r.netInterestPaid) : (expectedNet)}
                          </td>
                          <td style={{ whiteSpace: 'nowrap' }}>
                            {r.nrwhtRefunded ? (
                              <span style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                                <span className="badge badge-green">Refunded</span>
                                {r.nrwhtRefundReference && (
                                  <span style={{ fontSize: 11, color: '#6b7280', fontFamily: 'monospace' }}>
                                    {r.nrwhtRefundReference}
                                  </span>
                                )}
                              </span>
                            ) : precise(r.nrwhtWithheld) > 0 ? (
                              <span className="badge badge-yellow">Withheld</span>
                            ) : (
                              <span style={{ color: 'var(--text-muted)', fontSize: 12 }}>—</span>
                            )}
                          </td>
                          <td style={{ whiteSpace: 'nowrap' }}>
                            {r.customerSubmitted
                              ? <span className="badge badge-green">Submitted</span>
                              : <span className="badge badge-yellow">Pending</span>}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                  <tfoot>
                    <tr className="total-row">
                      <td colSpan={4}><strong>Totals in FJD</strong></td>
                      <td className="amount"><strong>{(totalGross)}</strong></td>
                      <td className="amount"><strong>−{(totalWithheld)}</strong></td>
                      <td className="amount"><strong>{(totalNet)}</strong></td>
                      <td></td>
                      <td></td>  {/* NRWHT Refunded column */}
                    </tr>
                  </tfoot>
                </table>
              </div>
            )}
          </div>

          {/* Withholding remittance summary */}
          <div className="tax-card">
            <h2 className="card-title">Withholding Summary for FRCS Remittance</h2>
            <div className="income-grid">
              <div className="income-row income-row-highlight">
                <span>Total gross interest paid to customers</span>
                <span className="positive">{fmt(totalGross)}</span>
              </div>
              <div className="income-row">
                <span>RIWT withheld — {riwtCount} resident customers with TIN (10%)</span>
                <span className="negative">−{fmt(totalRiwt)}</span>
              </div>
              <div className="income-row">
                <span>NRWHT withheld — {nrwhtCount} non-resident / no-TIN customers (10%)</span>
                <span className="negative">−{fmt(totalNrwht)}</span>
              </div>
              {refundedCount > 0 && (
                <div className="income-row income-row-highlight">
                  <span>NRWHT refunded to customers ({refundedCount} customer{refundedCount !== 1 ? 's' : ''})</span>
                  <span className="positive">−{fmt(totalNrwhtRefunded)}</span>
                </div>
              )}
              <div className="income-row">
                <span>Net NRWHT to remit after refunds</span>
                <span className="negative">−{fmt(totalNrwhtToRemit)}</span>
              </div>
              <div className="income-row income-row-highlight">
                <span><strong>Total withholding tax to remit to FRCS</strong></span>
                <span className="negative"><strong>−{fmt(totalWithheld)}</strong></span>
              </div>
              <div className="income-row">
                <span>Total net interest received by customers</span>
                <span className="positive">{fmt(totalNet)}</span>
              </div>
            </div>
          </div>

          {/* Submit to FRCS */}
          {/* {isAdmin ? (
            <div className="tax-card actions-card no-print">
              <div className="actions-row">
                <div className="actions-info">
                  <h3>Ready to submit to FRCS?</h3>
                  <p>
                    Mark all {year} interest summaries as submitted. This records that Bank of Fiji
                    has transmitted this report to the Fiji Revenue &amp; Customs Service.
                  </p>
                </div>
                <div className="actions-buttons">
                  <button
                    className="btn-primary"
                    onClick={handleMarkSubmitted}
                    disabled={submitting}
                  >
                    {submitting ? 'Marking…' : '↑ Mark as Submitted to FRCS'}
                  </button>
                </div>
              </div>
            </div>
          ) : (
            <div className="tax-card no-print">
              <div className="interest-notice notice-success" style={{ marginBottom: 0 }}>
                <span className="notice-icon">✓</span>
                <div style={{ fontSize: 13 }}>
                  Teller access is view-only on this page. Recalculate and submit actions are available to admin users.
                </div>
              </div>
            </div>
          )} */}

          <p className="tax-disclaimer">
            * This report covers all customers who earned interest in {report.taxYear}.
            Generated by {report.bankName} — Bank TIN: {report.bankTin}.
            Report date: {report.reportGeneratedDate}.
            Submitted to FRCS as part of the mandatory annual interest reporting obligation under the Fiji Income Tax Act.
          </p>
        </>
      ) : null}
      
      {showConfirm && (
        <ConfirmDialog
          title="Confirm FRCS Submission"
          message={`Mark all ${year} interest summaries as submitted to FRCS?\n\nThis cannot be undone.`}
          confirmText="Mark as Submitted"
          cancelText="Cancel"
          confirmVariant="success"
          cancelVariant="cancel-danger"
          variant="warning"
          onConfirm={confirmMarkSubmitted}
          onCancel={() => setShowConfirm(false)}
        />
      )}
      </div>
    </div>
  );
};

export default FrcsReportPage;