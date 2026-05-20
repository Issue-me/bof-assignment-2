import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import accountService from '../services/accountService';
import billPaymentService from '../services/billPaymentService';
import api from '../services/api';
import Toast from '../components/Toast';
import './BillPaymentForms.css';

/**
 * PayBillPage — allows the user to pay a bill immediately.
 */
const PayBillPage = () => {
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
    sourceAccountId: '',
    billerId: '',
    accountNumber: '',
    amount: '',
    description: '',
  });

  useEffect(() => {
    let isMounted = true;

    Promise.all([accountService.getMyAccounts(), api.get('/billers')])
      .then(([accountsData, billersResponse]) => {
        if (isMounted) {
          console.log('Accounts Data:', accountsData);
          console.log('Billers Data:', billersResponse.data);
          setAccounts(accountsData || []);
          setBillers(billersResponse.data || []);
        }
      })
      .catch((err) => {
        if (isMounted) {
          console.error('Error loading data:', err);
          console.error('Response:', err.response);
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
    const { name, value } = e.target;
    setForm((prev) => ({ ...prev, [name]: value }));
    setError('');
    setSuccess('');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setToast(null);

    if (!form.sourceAccountId || !form.billerId || !form.accountNumber || !form.amount) {
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

    setProcessing(true);

    try {
      const payload = {
        sourceAccountId: Number(form.sourceAccountId),
        billerId: Number(form.billerId),
        accountNumber: form.accountNumber,
        amount: Number(form.amount),
        description: form.description,
      };

      await billPaymentService.createBillPayment(payload);
      setToast({
        type: 'success',
        message: 'Bill payment completed successfully. Redirecting...',
        duration: 3000
      });
      setSuccess('Bill payment completed successfully!');
      setForm({ sourceAccountId: '', billerId: '', accountNumber: '', amount: '', description: '' });

      setTimeout(() => navigate('/dashboard'), 3000);
    } catch (err) {
      const errorMsg = err.response?.data?.message || 'Payment failed. Please try again.';
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

  return (
    <div className="dashboard-wrapper">
      <nav className="dashboard-nav">
        <div className="nav-brand">
          <svg className="logo-icon-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m3 10 9-7 9 7"/><path d="M5 10v10h14V10"/><path d="M9 20v-5h6v5"/></svg>
          <span>Bank of Fiji - Pay Bill</span>
        </div>
        <button onClick={() => navigate('/dashboard')} className="btn btn-back-outline btn-sm">
          Back to Dashboard
        </button>
      </nav>

      <main className="dashboard-main">
        <div className="card">
          <h2>Pay a Bill Now</h2>
          <p className="subtitle">Immediately pay any utility or service bill</p>

          {error && <div className="error-message">{error}</div>}
          {success && <div className="success-message">{success}</div>}

          <form onSubmit={handleSubmit} className="form-group">
            <div>
              <label htmlFor="sourceAccountId">Source Account *</label>
              <select
                id="sourceAccountId"
                name="sourceAccountId"
                value={form.sourceAccountId}
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
              <label htmlFor="accountNumber">Bill Account/Reference Number *</label>
              <input
                id="accountNumber"
                type="text"
                name="accountNumber"
                placeholder="e.g., FEA-ACC-123456"
                value={form.accountNumber}
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

            <button
              type="submit"
              className="btn btn-primary"
              disabled={processing}
            >
              {processing ? 'Processing...' : 'Pay Now'}
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

export default PayBillPage;
