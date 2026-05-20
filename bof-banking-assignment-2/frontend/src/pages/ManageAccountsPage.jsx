import React, { useEffect, useState, useCallback } from 'react';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';
import customerService from '../services/customerService';
import ConfirmDialog from '../components/ConfirmDialog';
import './ManageAccounts.css';

// ─────────────────────────────────────────────────────────────────────────────
// ManageAccountsPage — Admin / Teller account administration dashboard
//
// Interest is credited AUTOMATICALLY by InterestAccrualScheduler on the 1st
// of every month at 01:00 Fiji time. There is NO manual run button — admins
// can only VIEW the history of completed interest runs and account balances.
//
// Role rules:
//   • ADMIN  — full access including activate / deactivate accounts
//   • TELLER — can view accounts and interest history; cannot activate / deactivate
// ─────────────────────────────────────────────────────────────────────────────

const BANK_INTERNAL_ACCOUNT = 'BOF90000001';

// Handles ADMIN, ROLE_ADMIN, admin, role_admin from Spring Security JWT
const isAdminOrTeller = (role) => {
  if (!role) return false;
  const r = String(role).toUpperCase().replace('ROLE_', '');
  return r === 'ADMIN' || r === 'TELLER';
};

const isAdminRole = (role) => {
  if (!role) return false;
  return String(role).toUpperCase().replace('ROLE_', '') === 'ADMIN';
};

// ─────────────────────────────────────────────────────────────────────────────
// Main page component
// ─────────────────────────────────────────────────────────────────────────────

const ManageAccountsPage = () => {
  const { role } = useAuth();

  // Accounts data
  const [summary, setSummary]                       = useState(null);
  const [accounts, setAccounts]                     = useState([]);
  const [filteredAccounts, setFilteredAccounts]     = useState([]);
  const [liveSavingsRate, setLiveSavingsRate]       = useState(null);
  const [liveSavingsRatePct, setLiveSavingsRatePct] = useState(null);
  const [selectedAccount, setSelectedAccount]       = useState(null);
  const [customers, setCustomers]                   = useState([]);

  // Interest history data
  const [interestHistory, setInterestHistory]   = useState([]);
  const [interestSummary, setInterestSummary]   = useState(null);
  const [historyMonth, setHistoryMonth]         = useState(new Date().getMonth() + 1);
  const [historyYear, setHistoryYear]           = useState(new Date().getFullYear());
  const [historyLoading, setHistoryLoading]     = useState(false);

  // UI
  const [loading, setLoading]   = useState(true);
  const [error, setError]       = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [customersLoading, setCustomersLoading] = useState(false);
  const [showAddAccountForm, setShowAddAccountForm] = useState(false);
  const [addAccountSubmitting, setAddAccountSubmitting] = useState(false);
  const [accountActionConfirm, setAccountActionConfirm] = useState(null);
  const [addAccountForm, setAddAccountForm] = useState({
    customerUserId: '',
    accountType: 'SAVINGS',
    initialDeposit: '0.00',
    accountName: '',
  });

  // Filters
  const [searchTerm, setSearchTerm]     = useState('');
  const [typeFilter, setTypeFilter]     = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');

  // ── Filter logic ──────────────────────────────────────────────────────────

  const applyFilters = useCallback(() => {
    let filtered = [...accounts];
    if (searchTerm) {
      const term = searchTerm.toLowerCase();
      filtered = filtered.filter(acc =>
        acc.accountNumber.toLowerCase().includes(term) ||
        (acc.accountName && acc.accountName.toLowerCase().includes(term))
      );
    }
    if (typeFilter !== 'ALL')
      filtered = filtered.filter(acc => acc.accountType === typeFilter);
    if (statusFilter !== 'ALL')
      filtered = filtered.filter(acc => acc.active === (statusFilter === 'ACTIVE'));
    setFilteredAccounts(filtered);
  }, [accounts, searchTerm, typeFilter, statusFilter]);

  useEffect(() => {
    fetchData();
    fetchCustomers();
  }, []);
  useEffect(() => { applyFilters(); }, [applyFilters]);

  // Auto-refresh every 30 s so any scheduler-triggered run is reflected
  useEffect(() => {
    const timer = setInterval(() => fetchData(true), 30_000);
    return () => clearInterval(timer);
  }, []);

  // Reload interest history when month/year selector changes
  useEffect(() => {
    fetchInterestHistory();
  }, [historyMonth, historyYear]);

  // ── Data fetching ─────────────────────────────────────────────────────────

  const fetchData = async (silent = false) => {
    if (!silent) setLoading(true);
    setError('');
    try {
      const [summaryRes, accountsRes, rateRes] = await Promise.all([
        api.get('/accounts/summary'),
        api.get('/accounts'),
        api.get('/interest-rates').catch(() => null),
      ]);

      setSummary(summaryRes.data);

      let liveDecimal = null;
      let livePct     = null;
      if (rateRes?.data?.hasRate) {
        liveDecimal = rateRes.data.currentAnnualRate
          ? parseFloat(rateRes.data.currentAnnualRate) : null;
        livePct = rateRes.data.currentAnnualRatePct
          ? parseFloat(rateRes.data.currentAnnualRatePct) : null;
      }
      setLiveSavingsRate(liveDecimal);
      setLiveSavingsRatePct(livePct);

      // Patch live RBF rate onto customer SAVINGS accounts only
      const enriched = accountsRes.data.map(acc => {
        const isCustomerSavings =
          acc.accountType === 'SAVINGS' &&
          acc.accountNumber !== BANK_INTERNAL_ACCOUNT;
        return isCustomerSavings && liveDecimal !== null
          ? { ...acc, interestRate: liveDecimal }
          : acc;
      });
      setAccounts(enriched);

    } catch (err) {
      if (!silent)
        setError(err?.response?.data?.message || 'Unable to load accounts data');
    } finally {
      if (!silent) setLoading(false);
    }
  };

  const fetchCustomers = async () => {
    setCustomersLoading(true);
    try {
      const data = await customerService.getAllCustomers();
      const activeCustomers = (Array.isArray(data) ? data : []).filter((c) => c.active);
      setCustomers(activeCustomers);
      setAddAccountForm((prev) => ({
        ...prev,
        customerUserId: prev.customerUserId || (activeCustomers[0]?.id ? String(activeCustomers[0].id) : ''),
      }));
    } catch {
      setCustomers([]);
    } finally {
      setCustomersLoading(false);
    }
  };

  const fetchInterestHistory = async () => {
    setHistoryLoading(true);
    try {
      const [histRes, sumRes] = await Promise.all([
        api.get(`/admin/interest/history?month=${historyMonth}&year=${historyYear}`),
        api.get(`/admin/interest/summary?year=${historyYear}`),
      ]);
      setInterestHistory(histRes.data ?? []);
      setInterestSummary(sumRes.data ?? null);
    } catch {
      setInterestHistory([]);
      setInterestSummary(null);
    } finally {
      setHistoryLoading(false);
    }
  };

  // ── Account modal ─────────────────────────────────────────────────────────

  const handleViewDetails = async (account) => {
    try {
      const { data } = await api.get(`/accounts/${account.id}`);
      const isCustomerSavings =
        data.accountType === 'SAVINGS' &&
        data.accountNumber !== BANK_INTERNAL_ACCOUNT;
      if (isCustomerSavings && liveSavingsRate !== null)
        data.interestRate = liveSavingsRate;
      setSelectedAccount(data);
    } catch {
      setError('Unable to load account details');
    }
  };

  const handleDeactivate = async (accountId) => {
    if (!isAdminRole(role)) { setError('Only ADMIN can deactivate accounts'); return; }
    setAccountActionConfirm({ type: 'deactivate', accountId });
  };

  const handleActivate = async (accountId) => {
    if (!isAdminRole(role)) { setError('Only ADMIN can activate accounts'); return; }
    setAccountActionConfirm({ type: 'activate', accountId });
  };

  const confirmAccountAction = async () => {
    if (!accountActionConfirm) return;

    const { type, accountId } = accountActionConfirm;
    setAccountActionConfirm(null);

    try {
      if (type === 'deactivate') {
        await api.delete(`/accounts/${accountId}`);
      } else {
        await api.put(`/accounts/${accountId}/activate`);
      }
      setSelectedAccount(null);
      await fetchData();
    } catch (err) {
      setError(
        err?.response?.data?.message ||
        (type === 'deactivate' ? 'Unable to deactivate account' : 'Unable to activate account')
      );
    }
  };

  const handleAddAccountFormChange = (field, value) => {
    setAddAccountForm((prev) => ({ ...prev, [field]: value }));
  };

  const handleCreateAccount = async (e) => {
    e.preventDefault();
    if (!isAdminOrTeller(role)) {
      setError('Only TELLER or ADMIN can create accounts');
      return;
    }

    const depositValue = addAccountForm.initialDeposit === ''
      ? 0
      : Number(addAccountForm.initialDeposit);

    if (!addAccountForm.customerUserId) {
      setError('Select an existing customer to create an account');
      return;
    }
    if (!Number.isFinite(depositValue) || depositValue < 0) {
      setError('Initial deposit must be a valid non-negative amount');
      return;
    }

    setAddAccountSubmitting(true);
    setError('');
    setSuccessMessage('');

    try {
      const created = await api.post('/accounts', {
        customerUserId: Number(addAccountForm.customerUserId),
        accountType: addAccountForm.accountType,
        initialDeposit: depositValue,
        accountName: addAccountForm.accountName?.trim() || null,
      });

      setSuccessMessage(`Account ${created.data?.accountNumber ?? ''} created successfully`);
      setShowAddAccountForm(false);
      setAddAccountForm((prev) => ({
        ...prev,
        accountType: 'SAVINGS',
        initialDeposit: '0.00',
        accountName: '',
      }));
      await fetchData();
    } catch (err) {
      setError(err?.response?.data?.message || 'Unable to create account');
    } finally {
      setAddAccountSubmitting(false);
    }
  };

  // ── Helpers ───────────────────────────────────────────────────────────────

  const fmtCurrency = (v) =>
    new Intl.NumberFormat('en-FJ', {
      style: 'currency', currency: 'FJD', minimumFractionDigits: 2,
    }).format(v ?? 0);

  const months = [
    'January','February','March','April','May','June',
    'July','August','September','October','November','December',
  ];
  const yearOptions = Array.from({ length: 3 }, (_, i) => new Date().getFullYear() - i);
  const currentMonthName = months[historyMonth - 1];
  const canCreateAccounts = isAdminOrTeller(role);

  // ── Render ────────────────────────────────────────────────────────────────

  return (
    <div className="manage-accounts-page">

      {/* ── Page header ──────────────────────────────────────────────────── */}
      <div className="page-header">
        <div>
          <h1 className="page-title">Accounts Management</h1>
          <p className="page-subtitle">System-wide account administration and monitoring</p>
        </div>
        <div className="page-header-actions">
          <button
            className="btn btn-primary btn-add-account"
            onClick={() => {
              setError('');
              setSuccessMessage('');
              setShowAddAccountForm((prev) => !prev);
            }}
            disabled={!canCreateAccounts || customersLoading || customers.length === 0}
            title={
              customers.length === 0
                ? 'No active customers available for account creation'
                : 'Create a new account'
            }
          >
            {showAddAccountForm ? 'Close Add Account' : '+ Add Account'}
          </button>
          <button
            className="btn-refresh"
            onClick={() => { fetchData(); fetchInterestHistory(); fetchCustomers(); }}
            disabled={loading || customersLoading}
          >
            {(loading || customersLoading) ? '↻ Loading…' : '↻ Refresh'}
          </button>
        </div>
      </div>

      {/* ── Scheduler notice ─────────────────────────────────────────────── */}
      <div className="scheduler-notice">
        <span className="scheduler-notice-icon" style={{ display: 'flex', alignItems: 'center', color: '#3b82f6' }}>
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10" />
            <polyline points="12 6 12 12 16 14" />
          </svg>
        </span>
        <div>
          <strong>Automatic monthly interest</strong> — interest is credited to all
          active savings accounts by the system scheduler on the{' '}
          <strong>1st of every month at 01:00 Fiji time</strong>.
          No manual action is required.
          {liveSavingsRatePct != null && (
            <> Current rate: <strong>{liveSavingsRatePct.toFixed(4)}% p.a.</strong></>
          )}
        </div>
      </div>

      {/* ── Error banner ──────────────────────────────────────────────────── */}
      {error && (
        <div className="error-banner">
          ⚠️ {error}
          <button className="error-dismiss" onClick={() => setError('')}>×</button>
        </div>
      )}

      {successMessage && (
        <div className="success-banner">
          ✅ {successMessage}
          <button className="error-dismiss" onClick={() => setSuccessMessage('')}>×</button>
        </div>
      )}

      {loading ? (
        <div className="loading-state">
          <div className="spinner" />
          <p>Loading accounts data…</p>
        </div>
      ) : (
        <>
          {/* ── Overview cards ─────────────────────────────────────────────── */}
          <OverviewCards summary={summary} />

          {/* ── Interest history panel ───────────────────────────────────────
              Shows records written by the scheduler / SavingsInterestService.
              READ-ONLY — admins can view but not trigger runs.
          ── */}
          {isAdminOrTeller(role) && (
            <div className="panel">
              <div className="panel-header">
                <div className="panel-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ color: '#3b82f6' }}>
                    <line x1="12" y1="2" x2="12" y2="22" />
                    <path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" />
                  </svg>
                  Interest Run History
                </div>
                <p className="panel-subtitle">
                  Records of interest credited by the monthly scheduler.
                  Interest runs automatically on the 1st of each month.
                </p>
              </div>

              {/* Year summary strip */}
              {interestSummary && (
                <div className="year-summary">
                  <div className="year-summary-label">
                    {historyYear} year-to-date totals
                  </div>
                  <div className="year-summary-stats">
                    <div className="ys-stat">
                      <span className="ys-label">Gross credited</span>
                      <span className="ys-value ys-value--green">
                        {fmtCurrency(interestSummary.totalGross)}
                      </span>
                    </div>
                    <div className="ys-stat">
                      <span className="ys-label">RIWT withheld</span>
                      <span className="ys-value ys-value--red">
                        {fmtCurrency(interestSummary.totalRiwt)}
                      </span>
                    </div>
                    {!!interestSummary.totalNrwht && (
                      <div className="ys-stat">
                        <span className="ys-label">NRWHT withheld (refundable)</span>
                        <span className="ys-value ys-value--red">
                          {fmtCurrency(interestSummary.totalNrwht)}
                        </span>
                      </div>
                    )}
                    <div className="ys-stat">
                      <span className="ys-label">Net to customers</span>
                      <span className="ys-value ys-value--green">
                        {fmtCurrency(interestSummary.totalNet)}
                      </span>
                    </div>
                    <div className="ys-stat">
                      <span className="ys-label">Transactions</span>
                      <span className="ys-value">{interestSummary.txnCount ?? 0}</span>
                    </div>
                  </div>
                </div>
              )}

              {/* Month / year picker */}
              <div className="history-controls">
                <div className="field-group">
                  <label className="field-label">Month</label>
                  <select
                    className="field-select"
                    value={historyMonth}
                    onChange={e => setHistoryMonth(parseInt(e.target.value))}
                  >
                    {months.map((m, i) => (
                      <option key={m} value={i + 1}>{m}</option>
                    ))}
                  </select>
                </div>
                <div className="field-group">
                  <label className="field-label">Year</label>
                  <select
                    className="field-select"
                    value={historyYear}
                    onChange={e => setHistoryYear(parseInt(e.target.value))}
                  >
                    {yearOptions.map(y => (
                      <option key={y} value={y}>{y}</option>
                    ))}
                  </select>
                </div>
                <div className="history-count">
                  {historyLoading
                    ? 'Loading…'
                    : `${interestHistory.length} record${interestHistory.length !== 1 ? 's' : ''} for ${currentMonthName} ${historyYear}`}
                </div>
              </div>

              {/* History table */}
              {historyLoading ? (
                <div className="history-loading">
                  <div className="spinner spinner--sm" />
                  <span>Loading history…</span>
                </div>
              ) : interestHistory.length === 0 ? (
                <div className="history-empty">
                  <span>📭</span>
                  <span>
                    No interest records for {currentMonthName} {historyYear}.
                    The scheduler runs on the 1st of each month.
                  </span>
                </div>
              ) : (
                <div className="history-table-wrap">
                  <table className="history-table">
                    <thead>
                      <tr>
                        <th>Account</th>
                        <th>Customer Email</th>
                        <th className="col-right">Balance Snapshot</th>
                        <th className="col-right">Rate</th>
                        <th className="col-right">Gross</th>
                        <th className="col-right">RIWT</th>
                        <th className="col-center">NRWHT Refund</th>
                        <th className="col-right">Net Credited</th>
                        <th className="col-center">Exempt</th>
                        <th>Reference</th>
                        <th>Credited At</th>
                      </tr>
                    </thead>
                    <tbody>
                      {interestHistory.map(row => (
                        <tr key={row.id}>
                          <td className="cell-mono">{row.accountNumber}</td>
                          <td className="cell-email">{row.customerEmail}</td>
                          <td className="col-right">{fmtCurrency(row.balanceSnapshot)}</td>
                          <td className="col-right">
                            {row.annualRate != null
                              ? (parseFloat(row.annualRate) * 100).toFixed(2) + '%'
                              : '—'}
                          </td>
                          <td className="col-right cell-green">{fmtCurrency(row.grossInterest)}</td>
                          <td className="col-right cell-red">
                            {parseFloat(row.riwtDeducted ?? 0) > 0
                              ? fmtCurrency(row.riwtDeducted)
                              : '—'}
                          </td>
                          <td className="col-center">
                            {row.nrwhtRefunded
                              ? <span className="badge badge-status badge-status--active" title={row.nrwhtRefundReference || ''}>✓ Refunded</span>
                              : <span style={{ color: '#94a3b8', fontSize: 12 }}>—</span>}
                          </td>
                          <td className="col-right cell-bold">{fmtCurrency(row.netInterest)}</td>
                          <td className="col-center">
                            {row.riwtExempt
                              ? <span className="badge badge-status badge-status--active">Exempt</span>
                              : <span style={{ color: '#94a3b8', fontSize: 12 }}>—</span>}
                          </td>
                          <td className="cell-mono cell-ref">{row.reference}</td>
                          <td className="cell-date">{row.creditedAt?.replace('T', ' ').substring(0, 16)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}

          {/* ── Filters ────────────────────────────────────────────────────── */}
          <div className="filters-section">
            <div className="search-box">
              <span className="search-icon" style={{ display: 'flex', alignItems: 'center', color: '#64748b' }}>
                <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="11" cy="11" r="8" />
                  <path d="m21 21-4.35-4.35" />
                </svg>
              </span>
              <input
                type="text"
                className="search-input"
                placeholder="Search by account number or name…"
                value={searchTerm}
                onChange={e => setSearchTerm(e.target.value)}
              />
              {searchTerm && (
                <button className="search-clear" onClick={() => setSearchTerm('')}>×</button>
              )}
            </div>
            <div className="filter-controls">
              <div className="filter-group">
                <label className="filter-label">Type</label>
                <select
                  className="filter-select"
                  value={typeFilter}
                  onChange={e => setTypeFilter(e.target.value)}
                >
                  <option value="ALL">All Types</option>
                  <option value="SAVINGS">Savings</option>
                  <option value="SIMPLE_ACCESS">Simple Access</option>
                </select>
              </div>
              <div className="filter-group">
                <label className="filter-label">Status</label>
                <select
                  className="filter-select"
                  value={statusFilter}
                  onChange={e => setStatusFilter(e.target.value)}
                >
                  <option value="ALL">All Status</option>
                  <option value="ACTIVE">Active</option>
                  <option value="INACTIVE">Inactive</option>
                </select>
              </div>
              <div className="filter-count">
                {filteredAccounts.length} account{filteredAccounts.length !== 1 ? 's' : ''}
              </div>
            </div>
          </div>

          {showAddAccountForm && (
            <div className="panel add-account-panel">
              <div className="panel-header">
                <div className="panel-title">Create Account for Existing Customer</div>
                <p className="panel-subtitle">
                  Available account types: Savings and Simple Access.
                </p>
              </div>
              <form className="add-account-form" onSubmit={handleCreateAccount}>
                <div className="add-account-grid">
                  <div className="field-group">
                    <label className="field-label">Customer</label>
                    <select
                      className="field-select"
                      value={addAccountForm.customerUserId}
                      onChange={(e) => handleAddAccountFormChange('customerUserId', e.target.value)}
                      disabled={addAccountSubmitting || customersLoading || customers.length === 0}
                      required
                    >
                      {customers.length === 0 ? (
                        <option value="">No active customers available</option>
                      ) : (
                        customers.map((customer) => (
                          <option key={customer.id} value={customer.id}>
                            {customer.customerId} - {customer.firstName} {customer.lastName} ({customer.email})
                          </option>
                        ))
                      )}
                    </select>
                  </div>

                  <div className="field-group">
                    <label className="field-label">Account Type</label>
                    <select
                      className="field-select"
                      value={addAccountForm.accountType}
                      onChange={(e) => handleAddAccountFormChange('accountType', e.target.value)}
                      disabled={addAccountSubmitting}
                      required
                    >
                      <option value="SAVINGS">Savings</option>
                      <option value="SIMPLE_ACCESS">Simple Access</option>
                    </select>
                  </div>

                  <div className="field-group">
                    <label className="field-label">Initial Deposit (FJD)</label>
                    <input
                      type="number"
                      min="0"
                      step="0.01"
                      className="field-input"
                      value={addAccountForm.initialDeposit}
                      onChange={(e) => handleAddAccountFormChange('initialDeposit', e.target.value)}
                      disabled={addAccountSubmitting}
                    />
                  </div>

                  <div className="field-group">
                    <label className="field-label">Account Name (optional)</label>
                    <input
                      type="text"
                      maxLength={100}
                      className="field-input"
                      value={addAccountForm.accountName}
                      onChange={(e) => handleAddAccountFormChange('accountName', e.target.value)}
                      disabled={addAccountSubmitting}
                      placeholder="Example: Family Savings"
                    />
                  </div>
                </div>

                <div className="add-account-actions">
                  <button
                    className="btn btn-primary"
                    type="submit"
                    disabled={addAccountSubmitting || customersLoading || customers.length === 0}
                  >
                    {addAccountSubmitting ? 'Creating…' : 'Create Account'}
                  </button>
                  <button
                    className="btn btn-secondary"
                    type="button"
                    disabled={addAccountSubmitting}
                    onClick={() => setShowAddAccountForm(false)}
                  >
                    Cancel
                  </button>
                </div>
              </form>
            </div>
          )}

          {/* ── Accounts table ──────────────────────────────────────────────── */}
          <AccountsTable
            accounts={filteredAccounts}
            onViewDetails={handleViewDetails}
            liveSavingsRate={liveSavingsRate}
          />

          {/* ── Detail modal ────────────────────────────────────────────────── */}
          {selectedAccount && (
            <AccountDetailsModal
              account={selectedAccount}
              onClose={() => setSelectedAccount(null)}
              onDeactivate={handleDeactivate}
              onActivate={handleActivate}
              userRole={role}
            />
          )}

          {accountActionConfirm && (
            <ConfirmDialog
              title={accountActionConfirm.type === 'deactivate' ? 'Deactivate Account' : 'Activate Account'}
              message={
                accountActionConfirm.type === 'deactivate'
                  ? 'Are you sure you want to deactivate this account?'
                  : 'Are you sure you want to activate this account?'
              }
              confirmText={accountActionConfirm.type === 'deactivate' ? 'Deactivate' : 'Activate'}
              cancelText="Cancel"
              confirmVariant="success"
              cancelVariant="cancel-danger"
              variant="warning"
              onConfirm={confirmAccountAction}
              onCancel={() => setAccountActionConfirm(null)}
            />
          )}
        </>
      )}
    </div>
  );
};

// ─────────────────────────────────────────────────────────────────────────────
// Overview Cards
// ─────────────────────────────────────────────────────────────────────────────

const OverviewCards = ({ summary }) => {
  if (!summary) return null;
  const fmtCurrency = (v) =>
    new Intl.NumberFormat('en-FJ', {
      style: 'currency', currency: 'FJD', minimumFractionDigits: 2,
    }).format(v ?? 0);

  const cards = [
    {
      title: 'Total Accounts',
      value: summary.totalAccounts,
      icon: 'AC',
      color: '#3498db'
    },
    {
      title: 'Active Accounts',
      value: summary.activeAccounts,
      icon: 'ON',
      color: '#27ae60'
    },
    {
      title: 'Inactive Accounts',
      value: summary.inactiveAccounts,
      icon: 'OFF',
      color: '#e74c3c'
    },
    {
      title: 'Total System Balance',
        value: fmtCurrency(summary.totalSystemBalance),
      icon: 'BAL',
      color: '#f39c12'
    }
  ];

  return (
    <div className="overview-cards">
      {cards.map((card, index) => (
        <div key={index} className="stat-card" style={{ '--stat-color': card.color }}>
          <div className="stat-icon">{card.icon}</div>
          <div className="stat-content">
            <div className="stat-title">{card.title}</div>
            <div className="stat-value">{card.value}</div>
          </div>
        </div>
      ))}
    </div>
  );
};

// ─────────────────────────────────────────────────────────────────────────────
// Accounts Table
// ─────────────────────────────────────────────────────────────────────────────

const AccountsTable = ({ accounts, onViewDetails, liveSavingsRate }) => {
  const fmtCurrency = (v) =>
    new Intl.NumberFormat('en-FJ', {
      style: 'currency', currency: 'FJD', minimumFractionDigits: 2,
    }).format(v ?? 0);

  const fmtDate = (d) =>
    d ? new Date(d).toLocaleDateString('en-FJ', { year: 'numeric', month: 'short', day: 'numeric' }) : 'N/A';

  const displayRate = (account) => {
    if (account.accountNumber === BANK_INTERNAL_ACCOUNT) return '0.00%';
    if (account.accountType === 'SAVINGS' && liveSavingsRate != null)
      return (liveSavingsRate * 100).toFixed(2) + '%';
    return (parseFloat(account.interestRate ?? 0) * 100).toFixed(2) + '%';
  };

  const showRbfLabel = (account) =>
    account.accountType === 'SAVINGS' &&
    account.accountNumber !== BANK_INTERNAL_ACCOUNT &&
    liveSavingsRate != null;

  const isInternal = (account) => account.accountNumber === BANK_INTERNAL_ACCOUNT;

  if (accounts.length === 0)
    return (
      <div className="empty-state">
        
        <p>No accounts found matching your filters.</p>
      </div>
    );

  return (
    <div className="table-wrapper">
      <table className="accounts-table">
        <thead>
          <tr>
            <th>Account Number</th>
            <th>Account Name</th>
            <th>Type</th>
            <th className="col-right">Balance (FJD)</th>
            <th className="col-right">Interest Rate</th>
            <th className="col-center">Status</th>
            <th>Created</th>
            <th className="col-center">Actions</th>
          </tr>
        </thead>
        <tbody>
          {accounts.map(account => (
            <tr key={account.id} className={isInternal(account) ? 'row-internal' : ''}>
              <td className="cell-mono">{account.accountNumber}</td>
              <td>
                {account.accountName || '—'}
                {isInternal(account) && (
                  <span className="badge badge-internal">INTERNAL</span>
                )}
              </td>
              <td>
                <span className={`badge badge-type badge-type--${account.accountType.toLowerCase()}`}>
                  {account.accountType === 'SIMPLE_ACCESS' ? 'Simple Access' : account.accountType}
                </span>
              </td>
              <td className="col-right cell-balance">{fmtCurrency(account.balance)}</td>
              <td className="col-right">
                <span className={showRbfLabel(account) ? 'rate-live' : ''}>
                  {displayRate(account)}
                </span>
                {showRbfLabel(account) && <span className="rate-label">RBF rate</span>}
              </td>
              <td className="col-center">
                <span className={`badge badge-status badge-status--${account.active ? 'active' : 'inactive'}`}>
                  {account.active ? 'Active' : 'Inactive'}
                </span>
              </td>
              <td className="cell-date">{fmtDate(account.createdAt)}</td>
              <td className="col-center">
                <button className="btn-view" onClick={() => onViewDetails(account)}>View</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

// ─────────────────────────────────────────────────────────────────────────────
// Account Details Modal
// ─────────────────────────────────────────────────────────────────────────────

const AccountDetailsModal = ({ account, onClose, onDeactivate, onActivate, userRole }) => {
  const fmtCurrency = (v) =>
    new Intl.NumberFormat('en-FJ', {
      style: 'currency', currency: 'FJD', minimumFractionDigits: 2,
    }).format(v ?? 0);

  const fmtDate = (d) =>
    d ? new Date(d).toLocaleDateString('en-FJ', { year: 'numeric', month: 'long', day: 'numeric' }) : 'N/A';

  const displayRatePct = () => {
    if (account.accountNumber === BANK_INTERNAL_ACCOUNT) return '0.00%';
    const raw = parseFloat(account.interestRate ?? 0);
    const pct = raw <= 1 ? raw * 100 : raw;
    return pct.toFixed(2) + '%';
  };

  const isInternal       = account.accountNumber === BANK_INTERNAL_ACCOUNT;
  const isCustomerSavings = account.accountType === 'SAVINGS' && !isInternal;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-box" onClick={e => e.stopPropagation()}>

        <div className="modal-head">
          <div>
            <h2 className="modal-title">Account Details</h2>
            <p className="modal-subtitle">{account.accountNumber}</p>
          </div>
          <button className="modal-close" onClick={onClose}>×</button>
        </div>

        <div className="modal-body">
          {isInternal && (
            <div className="notice notice--info" style={{ display: 'flex', alignItems: 'flex-start', gap: 10 }}>
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ color: '#3b82f6', flexShrink: 0, marginTop: 2 }}>
                <path d="M14 21v-2a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v2" />
                <path d="M10 3v4m4 0v6m-8 0h16" />
                <rect x="4" y="9" width="16" height="8" rx="2" />
              </svg>
              <span>This is the Bank of Fiji internal funding account used to credit customer interest. It does not earn interest itself.</span>
            </div>
          )}

          <div className="detail-grid">
            <div className="detail-row">
              <span className="detail-label">Account Number</span>
              <span className="detail-value cell-mono">{account.accountNumber}</span>
            </div>
            <div className="detail-row">
              <span className="detail-label">Account Name</span>
              <span className="detail-value">{account.accountName || '—'}</span>
            </div>
            <div className="detail-row">
              <span className="detail-label">Account Type</span>
              <span className="detail-value">
                <span className={`badge badge-type badge-type--${account.accountType.toLowerCase()}`}>
                  {account.accountType === 'SIMPLE_ACCESS' ? 'Simple Access' : account.accountType}
                </span>
              </span>
            </div>
            <div className="detail-row">
              <span className="detail-label">Balance</span>
              <span className="detail-value detail-balance">{fmtCurrency(account.balance)}</span>
            </div>
            <div className="detail-row">
              <span className="detail-label">
                Interest Rate
                {isCustomerSavings && (
                  <span className="detail-label-sub"> (live RBF rate)</span>
                )}
              </span>
              <span className="detail-value">{displayRatePct()}</span>
            </div>
            <div className="detail-row">
              <span className="detail-label">Interest Earned</span>
              <span className="detail-value">{fmtCurrency(account.interestEarned)}</span>
            </div>
            <div className="detail-row">
              <span className="detail-label">Status</span>
              <span className="detail-value">
                <span className={`badge badge-status badge-status--${account.active ? 'active' : 'inactive'}`}>
                  {account.active ? '● Active' : '○ Inactive'}
                </span>
              </span>
            </div>
            <div className="detail-row">
              <span className="detail-label">Created</span>
              <span className="detail-value">{fmtDate(account.createdAt)}</span>
            </div>
          </div>
        </div>

        <div className="modal-foot">
          <button className="btn btn-secondary" onClick={onClose}>Close</button>
          {isAdminRole(userRole) && account.active && !isInternal && (
            <button className="btn btn-danger" onClick={() => onDeactivate(account.id)}>
              Deactivate Account
            </button>
          )}
          {isAdminRole(userRole) && !account.active && (
            <button className="btn btn-success" onClick={() => onActivate(account.id)}>
              Activate Account
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

export default ManageAccountsPage;