import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import accountService from '../services/accountService';
import billPaymentService from '../services/billPaymentService';
import api from '../services/api';
import Toast from '../components/Toast';
import './BillPaymentForms.css';

/**
 * ScheduleBillPaymentPage — allows the user to create a scheduled bill payment.
 */
const ScheduleBillPaymentPage = () => {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [accounts, setAccounts] = useState([]);
  const [billers, setBillers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [processing, setProcessing] = useState(false);
  const [toast, setToast] = useState(null);  // ← NEW: Toast notification state

  const [form, setForm] = useState({
    accountId: '',
    billerId: '',
    billReference: '',
    amount: '',
    frequency: 'MONTHLY',
    startDate: '',
    endDate: '',
    autoPayEnabled: true,
    approvalGiven: false,
    description: '',
  });

  useEffect(() => {
    let isMounted = true;

    Promise.all([accountService.getMyAccounts(), api.get('/billers')])
      .then(([accountsData, billersResponse]) => {
        if (isMounted) {
          setAccounts(accountsData || []);
          setBillers(billersResponse.data || []);
        }
      })
      .catch((err) => {
        if (isMounted) {
          setError(err.response?.data?.message || 'Failed to load data. Please try again.');
        }
      })
      .finally(() => {
        if (isMounted) setLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, []);

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setForm((prev) => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value,
    }));
    setError('');
    setSuccess('');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setToast(null);

    if (
      !form.accountId ||
      !form.billerId ||
      !form.billReference ||
      !form.amount ||
      !form.startDate
    ) {
      setToast({
        type: 'error',
        message: 'Please fill in all required fields.'
      });
      return;
    }

    if (Number(form.amount) <= 0) {
      setToast({
        type: 'error',
        message: 'Amount must be greater than 0.'
      });
      return;
    }

    if (form.endDate && new Date(form.endDate) < new Date(form.startDate)) {
      setToast({
        type: 'error',
        message: 'End date cannot be before start date.'
      });
      return;
    }

    if (!form.approvalGiven) {
      setToast({
        type: 'error',
        message: 'You must approve recurring monthly payments to continue.'
      });
      return;
    }

    setProcessing(true);

    try {
      const payload = {
        accountId: Number(form.accountId),
        billerId: Number(form.billerId),
        billReference: form.billReference,
        amount: Number(form.amount),
        frequency: form.frequency,
        startDate: form.startDate,
        endDate: form.endDate || null,
        autoPayEnabled: form.autoPayEnabled,
        approvalGiven: form.approvalGiven,
        description: form.description,
      };

      await billPaymentService.createScheduledBillPayment(payload);
      setToast({
        type: 'success',
        message: 'Scheduled payment created successfully. Redirecting...',
        duration: 3000
      });
      setSuccess('Scheduled bill payment created successfully!');
      setForm({
        accountId: '',
        billerId: '',
        billReference: '',
        amount: '',
        frequency: 'MONTHLY',
        startDate: '',
        endDate: '',
        autoPayEnabled: true,
        approvalGiven: false,
        description: '',
      });

      setTimeout(() => navigate('/bill-payments/scheduled'), 3000);
    } catch (err) {
      const errorMsg = err.response?.data?.message || 'Failed to create scheduled payment. Please try again.';
      setToast({
        type: 'error',
        message: errorMsg
      });
      setError(errorMsg);
    } finally {
      setProcessing(false);
    }
  };

  if (loading) {
    return <div className="dashboard-wrapper"><p>Loading...</p></div>;
  }

  const today = new Date().toISOString().split('T')[0];

  return (
    <div className="dashboard-wrapper">
      <nav className="dashboard-nav">
        <div className="nav-brand">
          <svg className="logo-icon-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m3 10 9-7 9 7"/><path d="M5 10v10h14V10"/><path d="M9 20v-5h6v5"/></svg>
          <span>Bank of Fiji - Schedule Bill Payment</span>
        </div>
        <button onClick={() => navigate('/dashboard')} className="btn btn-back-outline btn-sm">
          Back to Dashboard
        </button>
      </nav>

      <main className="dashboard-main">
        <div className="card">
          <h2>Schedule a Bill Payment</h2>
          <p className="subtitle">Automatically pay your bills on a recurring schedule</p>

          {error && <div className="error-message">{error}</div>}
          {success && <div className="success-message">{success}</div>}

          <form onSubmit={handleSubmit} className="form-group">
            <div>
              <label htmlFor="accountId">Source Account *</label>
              <select
                id="accountId"
                name="accountId"
                value={form.accountId}
                onChange={handleChange}
                required
              >
                <option value="">-- Select an account --</option>
                {accounts.map((account) => (
                  <option key={account.id} value={account.id}>
                    {account.accountNumber} - {account.accountName} (Balance: ${account.balance?.toFixed(2)})
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label htmlFor="billerId">Bill Provider *</label>
              <select
                id="billerId"
                name="billerId"
                value={form.billerId}
                onChange={handleChange}
                required
              >
                <option value="">-- Select a provider --</option>
                {billers.map((biller) => (
                  <option key={biller.id} value={biller.id}>
                    {biller.billerName} ({biller.category})
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label htmlFor="billReference">Bill Account/Reference Number *</label>
              <input
                id="billReference"
                type="text"
                name="billReference"
                placeholder="e.g., FEA-ACC-123456"
                value={form.billReference}
                onChange={handleChange}
                required
              />
            </div>

            <div>
              <label htmlFor="amount">Amount (FJD) *</label>
              <input
                id="amount"
                type="number"
                name="amount"
                placeholder="0.00"
                step="0.01"
                min="0"
                value={form.amount}
                onChange={handleChange}
                required
              />
            </div>

            <div>
              <label htmlFor="frequency">Frequency *</label>
              <select
                id="frequency"
                name="frequency"
                value={form.frequency}
                onChange={handleChange}
                required
              >
                <option value="ONCE">One time</option>
                <option value="WEEKLY">Weekly</option>
                <option value="BIWEEKLY">Bi-weekly</option>
                <option value="MONTHLY">Monthly</option>
                <option value="QUARTERLY">Quarterly</option>
                <option value="ANNUALLY">Annually</option>
              </select>
            </div>

            <div>
              <label htmlFor="startDate">Start Date *</label>
              <input
                id="startDate"
                type="date"
                name="startDate"
                min={today}
                value={form.startDate}
                onChange={handleChange}
                required
              />
            </div>

            <div>
              <label htmlFor="endDate">End Date (Optional)</label>
              <input
                id="endDate"
                type="date"
                name="endDate"
                min={form.startDate || today}
                value={form.endDate}
                onChange={handleChange}
              />
              <small>Leave blank for indefinite recurring payments</small>
            </div>

            <div>
              <label htmlFor="description">Description (Optional)</label>
              <input
                id="description"
                type="text"
                name="description"
                placeholder="e.g., Monthly electricity bill"
                value={form.description}
                onChange={handleChange}
              />
            </div>

            <div>
              <label htmlFor="autoPayEnabled">Enable Auto-Pay</label>
              <input
                id="autoPayEnabled"
                type="checkbox"
                name="autoPayEnabled"
                checked={form.autoPayEnabled}
                onChange={handleChange}
              />
            </div>

            <div>
              <label htmlFor="approvalGiven">I approve recurring monthly payments *</label>
              <input
                id="approvalGiven"
                type="checkbox"
                name="approvalGiven"
                checked={form.approvalGiven}
                onChange={handleChange}
                required
              />
            </div>

            <button
              type="submit"
              className="btn btn-primary"
              disabled={processing}
            >
              {processing ? 'Creating...' : 'Create Scheduled Payment'}
            </button>
          </form>
        </div>
      </main>

      {toast && (
        <Toast
          message={toast.message}
          type={toast.type}
          duration={toast.duration || 5000}
          onClose={() => setToast(null)}
        />
      )}
    </div>
  );
};

export default ScheduleBillPaymentPage;
