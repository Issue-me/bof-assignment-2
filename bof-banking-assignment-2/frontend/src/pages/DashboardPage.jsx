import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { isAdmin, isTeller, isCustomer } from '../utils/roleUtils';
import authService from '../services/authService';
import api from '../services/api';
import './Dashboard.css';

/**
 * Dashboard — shown after successful login.
 * Displays role-appropriate cards based on Role Access Matrix:
 * - ADMIN: FRCS reports, user management
 * - TELLER: Account operations, customer service
 * - CUSTOMER: Personal finances, transactions, bill payments
 */
const DashboardPage = () => {
  const { user, role } = useAuth();
  const navigate = useNavigate();

  const [profile, setProfile] = useState(null);
  const [profileLoading, setProfileLoading] = useState(true);
  const [intelLoading, setIntelLoading] = useState(true);
  const [accounts, setAccounts] = useState([]);
  const [systemSummary, setSystemSummary] = useState(null);
  const [customerCount, setCustomerCount] = useState(0);
  const [intelError, setIntelError] = useState('');
  const [now, setNow] = useState(new Date());

  const operationsMode = isTeller(role) || isAdmin(role);
  const tellerMode = isTeller(role);

  const profileCompletion = useMemo(() => {
    if (!profile) {
      return 0;
    }

    const checks = [
      !!profile.firstName,
      !!profile.lastName,
      !!profile.email,
      !!profile.phoneNumber,
      !!profile.tinNumber,
      !!profile.nationalId,
      !!profile.dateOfBirth,
      !!profile.address,
    ];

    const complete = checks.filter(Boolean).length;
    return Math.round((complete / checks.length) * 100);
  }, [profile]);

  const customerStats = useMemo(() => {
    const totalBalance = accounts.reduce((sum, account) => sum + Number(account.balance || 0), 0);
    const activeAccounts = accounts.filter((account) => Boolean(account.active)).length;
    const savingsAccounts = accounts.filter((account) => account.accountType === 'SAVINGS').length;

    return {
      totalBalance,
      activeAccounts,
      savingsAccounts,
      accountCount: accounts.length,
    };
  }, [accounts]);

  const formatCurrency = (value) =>
    new Intl.NumberFormat('en-FJ', {
      style: 'currency',
      currency: 'FJD',
      minimumFractionDigits: 2,
    }).format(Number(value || 0));

  const greeting = (() => {
    const hour = now.getHours();
    if (hour < 12) return 'Good morning';
    if (hour < 18) return 'Good afternoon';
    return 'Good evening';
  })();

  useEffect(() => {
    let mounted = true;
    setProfileLoading(true);

    authService
      .getProfile()
      .then((data) => {
        if (mounted) {
          setProfile(data);
        }
      })
      .catch(() => {
        if (mounted) {
          setProfile(null);
        }
      })
      .finally(() => {
        if (mounted) {
          setProfileLoading(false);
        }
      });

    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), 30000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    let mounted = true;

    const loadDashboardIntel = async () => {
      setIntelLoading(true);
      setIntelError('');

      try {
        if (isCustomer(role)) {
          const accountsResponse = await api.get('/accounts');
          if (mounted) {
            setAccounts(Array.isArray(accountsResponse.data) ? accountsResponse.data : []);
            setSystemSummary(null);
            setCustomerCount(0);
          }
          return;
        }

        if (operationsMode) {
          const [summaryResponse, customersResponse] = await Promise.all([
            api.get('/accounts/summary').catch(() => ({ data: null })),
            api.get('/customers').catch(() => ({ data: [] })),
          ]);

          if (mounted) {
            setSystemSummary(summaryResponse.data || null);
            setCustomerCount(Array.isArray(customersResponse.data) ? customersResponse.data.length : 0);
            setAccounts([]);
          }
        }
      } catch {
        if (mounted) {
          setIntelError('Dashboard insights are temporarily unavailable. Core actions remain available.');
        }
      } finally {
        if (mounted) {
          setIntelLoading(false);
        }
      }
    };

    loadDashboardIntel();

    return () => {
      mounted = false;
    };
  }, [role, operationsMode]);

  const renderCustomerActions = () => (
    <div className="dash-action-grid">
      <button type="button" className="dash-action-card action-primary" onClick={() => navigate('/accounts')}>
        <span className="dash-action-title">Account Studio</span>
        <span className="dash-action-copy">Review balances, account names, and status in one screen.</span>
        <span className="dash-action-link">Open Accounts</span>
      </button>

      <button type="button" className="dash-action-card action-secondary" onClick={() => navigate('/transfer')}>
        <span className="dash-action-title">Transfer Hub</span>
        <span className="dash-action-copy">Move funds between your accounts with transfer-limit guardrails.</span>
        <span className="dash-action-link">Start Transfer</span>
      </button>

      <button type="button" className="dash-action-card action-secondary" onClick={() => navigate('/bill-payments/pay-now')}>
        <span className="dash-action-title">Bill Payments Desk</span>
        <span className="dash-action-copy">Pay now, schedule later, and monitor bill payment outcomes.</span>
        <span className="dash-action-link">Pay or Schedule</span>
      </button>

      <button type="button" className="dash-action-card action-secondary" onClick={() => navigate('/loan-application')}>
        <span className="dash-action-title">Loan Application</span>
        <span className="dash-action-copy">Apply for a new loan and upload supporting documents in one flow.</span>
        <span className="dash-action-link">Start Application</span>
      </button>

      <button type="button" className="dash-action-card action-highlight" onClick={() => navigate('/transaction_history')}>
        <span className="dash-action-title">Transaction Trail</span>
        <span className="dash-action-copy">Track your recent movement and reconcile by activity type.</span>
        <span className="dash-action-link">View History</span>
      </button>

      <button type="button" className="dash-action-card action-secondary" onClick={() => navigate('/loan-advertisements')}>
        <span className="dash-action-title">Loan Advertisements</span>
        <span className="dash-action-copy">Explore available loan products and their terms.</span>
        <span className="dash-action-link">View Advertisements</span>
      </button>
    </div>
  );

  const renderOperationsActions = () => (
    <div className="dash-action-grid">
      <button type="button" className="dash-action-card action-primary" onClick={() => navigate('/manage-accounts')}>
        <span className="dash-action-title">Account Operations</span>
        <span className="dash-action-copy">Create, activate, and maintain account records with live summaries.</span>
        <span className="dash-action-link">Manage Accounts</span>
      </button>

      <button type="button" className="dash-action-card action-secondary" onClick={() => navigate('/manage-account-holders')}>
        <span className="dash-action-title">Customer Onboarding</span>
        <span className="dash-action-copy">Update holder profiles, residency details, and compliance identifiers.</span>
        <span className="dash-action-link">Manage Holders</span>
      </button>

      {tellerMode && (
        <button type="button" className="dash-action-card action-secondary" onClick={() => navigate('/teller/transaction-monitoring')}>
          <span className="dash-action-title">Monitoring Console</span>
          <span className="dash-action-copy">Observe suspicious patterns and high-value account movement.</span>
          {/* <span className="dash-action-link">Open Monitoring</span> */}
        </button>
      )}

      {tellerMode && (
        <button type="button" className="dash-action-card action-secondary" onClick={() => navigate('/teller/loan-approvals')}>
          <span className="dash-action-title">Loan Approval Desk</span>
          <span className="dash-action-copy">Review applications, verify supporting documents, and disburse approved loans.</span>
          <span className="dash-action-link">Open Loan Approvals</span>
        </button>
      )}

      <button type="button" className="dash-action-card action-highlight" onClick={() => navigate('/frcs_report')}>
        <span className="dash-action-title">Compliance Reporting</span>
        <span className="dash-action-copy">Generate FRCS-aligned summaries and year-end reporting outputs.</span>
        <span className="dash-action-link">View FRCS Report</span>
      </button>
    </div>
  );

  return (
    <div className="dashboard-command-center">
      <section className={`dash-hero ${operationsMode ? 'dash-hero-ops' : 'dash-hero-customer'}`}>
        <div className="dash-hero-main">
          <p className="dash-kicker">
            {operationsMode ? 'Operations Command Center' : 'Personal Finance Command Center'}
          </p>
          <h1>{greeting}, {user?.firstName || 'there'}.</h1>
          <p>
            {operationsMode
              ? 'Manage customer operations, compliance workflows, and daily service throughput from one pane.'
              : 'Track money movement, pay bills, and stay ahead of your monthly balance goals.'}
          </p>

          <div className="dash-hero-actions">
            {operationsMode ? (
              <>
                <button type="button" className="btn btn-primary" onClick={() => navigate('/manage-accounts')}>
                  Open Account Operations
                </button>
                {tellerMode && (
                  <button type="button" className="btn btn-secondary" onClick={() => navigate('/teller/transaction-monitoring')}>
                    Open Monitoring
                  </button>
                )}
              </>
            ) : (
              <>
                <button type="button" className="btn btn-primary" onClick={() => navigate('/transfer')}>
                  Start Transfer
                </button>
                <button type="button" className="btn btn-secondary" onClick={() => navigate('/bill-payments/pay-now')}>
                  Pay a Bill
                </button>
                <button type="button" className="btn btn-secondary" onClick={() => navigate('/loan-adverstisements')}>
                  View Loan Information
                </button>
              </>
            )}
          </div>
        </div>

        <aside className="dash-hero-rail">
          <div className="dash-rail-item">
            <span>Role</span>
            <strong>{operationsMode ? 'Teller / Admin' : 'Customer'}</strong>
          </div>
          <div className="dash-rail-item">
            <span>Session Time</span>
            <strong>{now.toLocaleTimeString('en-FJ', { hour: '2-digit', minute: '2-digit' })}</strong>
          </div>
          <div className="dash-rail-item">
            <span>Identity</span>
            <strong>{user?.customerId || 'N/A'}</strong>
          </div>
          <div className="dash-rail-item">
            <span>Profile Completion</span>
            <strong>{profileLoading ? '...' : `${profileCompletion}%`}</strong>
          </div>
        </aside>
      </section>

      {intelError && <div className="dash-warning">{intelError}</div>}

      <section className="dash-metric-strip">
        {operationsMode ? (
          <>
            <div className="dash-metric-card">
              <span className="metric-label">Total Accounts</span>
              <strong>{intelLoading ? '...' : (systemSummary?.totalAccounts ?? 0)}</strong>
            </div>
            <div className="dash-metric-card">
              <span className="metric-label">Active Accounts</span>
              <strong>{intelLoading ? '...' : (systemSummary?.activeAccounts ?? 0)}</strong>
            </div>
            <div className="dash-metric-card">
              <span className="metric-label">Inactive Accounts</span>
              <strong>{intelLoading ? '...' : (systemSummary?.inactiveAccounts ?? 0)}</strong>
            </div>
            <div className="dash-metric-card">
              <span className="metric-label">Customers In Scope</span>
              <strong>{intelLoading ? '...' : customerCount}</strong>
            </div>
          </>
        ) : (
          <>
            <div className="dash-metric-card">
              <span className="metric-label">Total Balance</span>
              <strong>{intelLoading ? '...' : formatCurrency(customerStats.totalBalance)}</strong>
            </div>
            <div className="dash-metric-card">
              <span className="metric-label">Active Accounts</span>
              <strong>{intelLoading ? '...' : customerStats.activeAccounts}</strong>
            </div>
            <div className="dash-metric-card">
              <span className="metric-label">Savings Accounts</span>
              <strong>{intelLoading ? '...' : customerStats.savingsAccounts}</strong>
            </div>
            <div className="dash-metric-card">
              <span className="metric-label">Total Accounts</span>
              <strong>{intelLoading ? '...' : customerStats.accountCount}</strong>
            </div>
          </>
        )}
      </section>

      <section className="dash-layout-grid">
        <div className="dash-wide-column">
          <div className="dash-surface">
            <div className="dash-section-head">
              <h2>Action Deck</h2>
              <p>{operationsMode ? 'Prioritized for teller/admin workflows' : 'Curated for daily customer banking tasks'}</p>
            </div>
            {operationsMode ? renderOperationsActions() : renderCustomerActions()}
          </div>

          <div className="dash-surface">
            <div className="dash-section-head">
              <h2>{operationsMode ? 'Operations Pulse' : 'Account Snapshot'}</h2>
              <p>{operationsMode ? 'Navigate your high-frequency workflows quickly' : 'Fast visibility into your top accounts'}</p>
            </div>

            {operationsMode ? (
              <div className="dash-list">
                <button type="button" className="dash-list-item" onClick={() => navigate('/manage-account-holders')}>
                  <span>Customer profile maintenance and onboarding updates</span>
                  <strong>Open</strong>
                </button>
                {/* <button type="button" className="dash-list-item" onClick={() => navigate('/teller/billers')}>
                  <span>Biller catalog administration and service mapping</span>
                  <strong>Open</strong>
                </button> */}
                {/* <button type="button" className="dash-list-item" onClick={() => navigate('/teller/bill-payments/monitoring')}>
                  <span>Bill payment monitoring and scheduled execution audit</span>
                  <strong>Open</strong>
                </button> */}
                {tellerMode && (
                  <button type="button" className="dash-list-item" onClick={() => navigate('/teller/loan-approvals')}>
                    <span>Loan approval queue and disbursement review</span>
                    <strong>Open</strong>
                  </button>
                )}
                {tellerMode && (
                  <button type="button" className="dash-list-item" onClick={() => navigate('/manage-loan-rates')}>
                    <span>Loan rate configuration and policy updates</span>
                    <strong>Open</strong>
                  </button>
                )}
                <button type="button" className="dash-list-item" onClick={() => navigate('/manage-interest-rates')}>
                  <span>Savings interest rate configuration and RBF updates</span>
                  <strong>Open</strong>
                </button>
              </div>
            ) : (
              <div className="dash-list">
                {(accounts.slice(0, 4)).map((account) => (
                  <button
                    key={account.id}
                    type="button"
                    className="dash-list-item"
                    onClick={() => navigate('/accounts')}
                  >
                    <span>
                      {(account.accountName || account.accountType || 'Account')} •••• {String(account.accountNumber || '').slice(-4)}
                    </span>
                    <strong>{formatCurrency(account.balance)}</strong>
                  </button>
                ))}
                {!intelLoading && accounts.length === 0 && (
                  <div className="dash-empty">No customer accounts are currently available.</div>
                )}
              </div>
            )}
          </div>
        </div>

        <aside className="dash-rail-column">
          <div className="dash-surface">
            <div className="dash-section-head compact">
              <h2>Profile Console</h2>
            </div>
            {profileLoading ? (
              <p className="dash-muted">Loading profile...</p>
            ) : profile ? (
              <dl className="dash-profile-grid">
                <div><dt>Full Name</dt><dd>{profile.firstName} {profile.lastName}</dd></div>
                <div><dt>Email</dt><dd>{profile.email || 'N/A'}</dd></div>
                <div><dt>Phone</dt><dd>{profile.phoneNumber || 'N/A'}</dd></div>
                <div><dt>TIN</dt><dd>{profile.tinNumber || 'Not set'}</dd></div>
                <div><dt>Passport Number</dt><dd>{profile.nationalId || 'Not set'}</dd></div>
                <div><dt>Residency</dt><dd>{profile.resident ? 'Resident' : 'Non-Resident'}</dd></div>
              </dl>
            ) : (
              <p className="dash-muted">Profile details are unavailable right now.</p>
            )}
          </div>

          <div className="dash-surface spotlight">
            <div className="dash-section-head compact">
              <h2>{operationsMode ? 'Governance Spotlight' : 'Financial Spotlight'}</h2>
            </div>
            {operationsMode ? (
              <ul className="dash-note-list">
                <li>Keep account-holder records complete before account creation.</li>
                <li>Review FRCS report monthly to reduce end-of-year corrections.</li>
                <li>Use transaction monitoring before high-risk manual actions.</li>
              </ul>
            ) : (
              <ul className="dash-note-list">
                <li>Use scheduled payments to reduce missed due dates.</li>
                <li>Check transaction history before monthly reconciliation.</li>
                <li>Keep profile data current for faster teller-assisted support.</li>
              </ul>
            )}
          </div>
        </aside>
      </section>
    </div>
  );
};

export default DashboardPage;