import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import accountService from '../services/accountService';
import transactionService from '../services/transactionService';
import './Dashboard.css';
import './TransferPage.css';

/**
 * TransferPage - transfer flow with confirmation step and OTP modal.
 */
const TransferPage = () => {
  const navigate = useNavigate();

  const HIGH_VALUE_THRESHOLD = 4000;

  const [accounts, setAccounts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [processing, setProcessing] = useState(false);
  const [step, setStep] = useState('entry');
  const [recipientName, setRecipientName] = useState('');
  const [recipientAccountNumber, setRecipientAccountNumber] = useState('');
  const [idempotencyKey, setIdempotencyKey] = useState('');

  const [otpModalOpen, setOtpModalOpen] = useState(false);
  const [otpCode, setOtpCode] = useState('');
  const [challengeId, setChallengeId] = useState('');
  const [otpExpiresAt, setOtpExpiresAt] = useState('');

  const [form, setForm] = useState({
    sourceAccountId: '',
    destinationType: 'mine', // 'mine' or 'external'
    destinationAccountId: '',
    destinationAccountNumber: '',
    amount: '',
    description: '',
  });

  useEffect(() => {
    let isMounted = true;

    accountService
      .getMyAccounts()
      .then((data) => {
        if (isMounted) {
          setAccounts(data || []);
        }
      })
      .catch((err) => {
        if (isMounted) {
          setError(
            err.response?.data?.message || 'Failed to load accounts. Please try again.'
          );
        }
      })
      .finally(() => {
        if (isMounted) setLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, []);

  useEffect(() => {
    if (!otpModalOpen) {
      return undefined;
    }

    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    return () => {
      document.body.style.overflow = originalOverflow;
    };
  }, [otpModalOpen]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setForm((prev) => ({ ...prev, [name]: value }));
    setError('');
    setSuccess('');
  };

  const resetOtpState = () => {
    setOtpModalOpen(false);
    setOtpCode('');
    setChallengeId('');
    setOtpExpiresAt('');
  };

  const resetTransferState = () => {
    setStep('entry');
    setRecipientName('');
    setRecipientAccountNumber('');
    setIdempotencyKey('');
    resetOtpState();
    setForm({
      sourceAccountId: '',
      destinationType: 'mine',
      destinationAccountId: '',
      destinationAccountNumber: '',
      amount: '',
      description: '',
    });
  };

  const validateTransferForm = () => {
    if (!form.sourceAccountId) {
      setError('Please select a source account.');
      return false;
    }

    if (!form.amount || Number(form.amount) <= 0) {
      setError('Please enter a valid amount greater than 0.');
      return false;
    }

    if (
      form.destinationType === 'mine' &&
      !form.destinationAccountId
    ) {
      setError('Please select or enter a destination account.');
      return false;
    }

    if (
      form.destinationType === 'external' &&
      !form.destinationAccountNumber?.trim()
    ) {
      setError('Please select or enter a destination account.');
      return false;
    }

    return true;
  };

  const buildTransferPayload = () => ({
    sourceAccountId: Number(form.sourceAccountId),
    amount: Number(form.amount),
    description: form.description || undefined,
    destinationAccountId:
      form.destinationType === 'mine' && form.destinationAccountId
        ? Number(form.destinationAccountId)
        : undefined,
    destinationAccountNumber:
      form.destinationType === 'external' && form.destinationAccountNumber
        ? form.destinationAccountNumber.trim()
        : undefined,
    idempotencyKey,
  });

  const lookupRecipientForConfirmation = async () => {
    if (form.destinationType === 'mine') {
      const destination = destinationAccounts.find(
        (acc) => String(acc.id) === String(form.destinationAccountId)
      );

      if (!destination) {
        throw new Error('Destination account not found.');
      }

      const localName = destination.ownerName || destination.accountName || destination.accountType || 'Account holder';
      setRecipientName(localName);
      setRecipientAccountNumber(destination.accountNumber || '');
      return;
    }

    const accountNumber = form.destinationAccountNumber.trim();
    const response = await accountService.getAccountByNumber(accountNumber);

    setRecipientName(response?.ownerName || response?.accountName || 'Account holder');
    setRecipientAccountNumber(response?.accountNumber || accountNumber);
  };

  const handleContinueToConfirmation = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    if (!validateTransferForm()) {
      return;
    }

    const generatedKey = idempotencyKey || `TXN-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
    if (!idempotencyKey) {
      setIdempotencyKey(generatedKey);
    }

    setProcessing(true);
    try {
      await lookupRecipientForConfirmation();
      setStep('confirm');
    } catch (err) {
      setError(
        err.response?.data?.message ||
          err.response?.data?.error ||
          err.message ||
          'Unable to validate destination account. Please check details and try again.'
      );
    } finally {
      setProcessing(false);
    }
  };

  const handleConfirmTransfer = async () => {
    setError('');
    setSuccess('');
    setProcessing(true);

    try {
      const response = await transactionService.initiateTransfer(buildTransferPayload());

      if (response?.otpRequired) {
        setOtpModalOpen(true);
        setChallengeId(response.challengeId || '');
        setOtpExpiresAt(response.expiresAt || '');
        setSuccess(response.message || 'OTP sent to your email. Enter it to complete transfer.');
        return;
      }

      const reference = response?.transaction?.referenceNumber || response?.referenceNumber || idempotencyKey;
      setSuccess(`Transfer completed successfully. Reference: ${reference}`);
      resetTransferState();
    } catch (err) {
      setError(
        err.response?.data?.message ||
          err.response?.data?.error ||
          'Transfer failed. Please check details and try again.'
      );
    } finally {
      setProcessing(false);
    }
  };

  const handleResendOtp = async () => {
    setError('');
    setSuccess('');
    setProcessing(true);

    try {
      const response = await transactionService.initiateTransfer(buildTransferPayload());

      if (!response?.otpRequired) {
        setError('This transfer no longer requires OTP. Please confirm transfer again.');
        setOtpModalOpen(false);
        return;
      }

      setChallengeId(response.challengeId || challengeId);
      setOtpExpiresAt(response.expiresAt || '');
      setOtpCode('');
      setSuccess(response.message || 'A new OTP has been sent to your email.');
    } catch (err) {
      setError(
        err.response?.data?.message ||
          err.response?.data?.error ||
          'Unable to resend OTP right now. Please try again.'
      );
    } finally {
      setProcessing(false);
    }
  };

  const handleVerifyOtp = async () => {
    setError('');
    setSuccess('');

    if (!challengeId) {
      setError('OTP challenge not found. Please restart your transfer.');
      return;
    }

    if (!otpCode || otpCode.trim().length < 6) {
      setError('Please enter the 6-digit OTP from your email.');
      return;
    }

    setProcessing(true);
    try {
      const response = await transactionService.verifyTransferOtp({
        challengeId,
        otpCode: otpCode.trim(),
      });
      const reference = response?.referenceNumber || idempotencyKey || 'N/A';
      setSuccess(`Transfer completed successfully. Reference: ${reference}`);
      resetTransferState();
    } catch (err) {
      setError(
        err.response?.data?.message ||
          err.response?.data?.error ||
          'OTP verification failed. Please try again.'
      );
    } finally {
      setProcessing(false);
    }
  };

  const handleBack = () => {
    if (step === 'confirm') {
      setStep('entry');
      return;
    }
    navigate('/dashboard');
  };

  const handleCancelConfirmation = () => {
    resetOtpState();
    navigate('/dashboard');
  };

  const sourceAccounts = accounts;
  const destinationAccounts = accounts.filter(
    (acc) => String(acc.id) !== String(form.sourceAccountId)
  );
  const selectedSourceAccount = sourceAccounts.find(
    (acc) => String(acc.id) === String(form.sourceAccountId)
  );
  const selectedDestinationAccount = destinationAccounts.find(
    (acc) => String(acc.id) === String(form.destinationAccountId)
  );
  const isHighValueTransfer = Number(form.amount || 0) > HIGH_VALUE_THRESHOLD;

  return (
    <div className="dashboard-wrapper transfer-page">
      <main className="dashboard-main transfer-page-main">
        <div className="welcome-banner">
          <h2>Transfer Funds</h2>
          <p>Transfer money between your own accounts or to another account.</p>
        </div>

        <div className="card">
          {loading ? (
            <p>Loading accounts...</p>
          ) : (
            <>
              {error && (
                <div className="alert alert-error" role="alert">
                  {error}
                </div>
              )}
              {success && (
                <div className="alert alert-success" role="status">
                  {success}
                </div>
              )}

              {step === 'entry' ? (
                <form onSubmit={handleContinueToConfirmation} className="auth-form" noValidate>
                  <div className="form-group">
                    <label htmlFor="sourceAccountId">From account</label>
                    <select
                      id="sourceAccountId"
                      name="sourceAccountId"
                      value={form.sourceAccountId}
                      onChange={handleChange}
                      required
                    >
                      <option value="">Select source account</option>
                      {sourceAccounts.map((acc) => (
                        <option key={acc.id} value={acc.id}>
                          {acc.accountNumber} - {acc.accountName || acc.accountType} (FJD{' '}
                          {Number(acc.balance).toFixed(2)})
                        </option>
                      ))}
                    </select>
                  </div>

                  <div className="form-group">
                    <label>To account</label>
                    <div className="transfer-destination-type">
                      <label>
                        <input
                          type="radio"
                          name="destinationType"
                          value="mine"
                          checked={form.destinationType === 'mine'}
                          onChange={handleChange}
                        />{' '}
                        My other account
                      </label>
                      <label>
                        <input
                          type="radio"
                          name="destinationType"
                          value="external"
                          checked={form.destinationType === 'external'}
                          onChange={handleChange}
                        />{' '}
                        Another BoF account
                      </label>
                    </div>

                    {form.destinationType === 'mine' ? (
                      <select
                        name="destinationAccountId"
                        value={form.destinationAccountId}
                        onChange={handleChange}
                      >
                        <option value="">Select destination account</option>
                        {destinationAccounts.map((acc) => (
                          <option key={acc.id} value={acc.id}>
                            {acc.accountNumber} - {acc.accountName || acc.accountType}
                          </option>
                        ))}
                      </select>
                    ) : (
                      <input
                        type="text"
                        name="destinationAccountNumber"
                        value={form.destinationAccountNumber}
                        onChange={handleChange}
                        placeholder="Enter destination account number (e.g., BOFXXXXXX)"
                      />
                    )}
                  </div>

                  <div className="form-group">
                    <label htmlFor="amount">Amount (FJD)</label>
                    {selectedSourceAccount && (
                      <div className="balance-display">
                        Available Balance: FJD {Number(selectedSourceAccount.balance).toFixed(2)}
                      </div>
                    )}
                    <input
                      id="amount"
                      type="number"
                      step="0.01"
                      min="0.01"
                      name="amount"
                      value={form.amount}
                      onChange={handleChange}
                      required
                    />
                  </div>

                  <div className="form-group">
                    <label htmlFor="description">Description (optional)</label>
                    <input
                      id="description"
                      type="text"
                      name="description"
                      value={form.description}
                      onChange={handleChange}
                      placeholder="e.g., Rent payment, savings transfer"
                    />
                  </div>

                  <button
                    type="submit"
                    className="btn btn-primary btn-full"
                    disabled={processing}
                  >
                    {processing ? 'Loading confirmation...' : 'Continue'}
                  </button>
                </form>
              ) : (
                <div className="auth-form transfer-confirmation">
                  <div className="transfer-confirmation-header">
                    <h3>Confirm Transfer Details</h3>
                    <p>Please review and confirm before submitting.</p>
                  </div>

                  <div className="transfer-confirmation-grid">
                    <div className="transfer-confirmation-row">
                      <span className="transfer-confirmation-label">From</span>
                      <span className="transfer-confirmation-value">
                        {selectedSourceAccount
                          ? `${selectedSourceAccount.accountNumber} (${selectedSourceAccount.accountName || selectedSourceAccount.accountType})`
                          : 'N/A'}
                      </span>
                    </div>

                    <div className="transfer-confirmation-row">
                      <span className="transfer-confirmation-label">Recipient</span>
                      <span className="transfer-confirmation-value">{recipientName || 'N/A'}</span>
                    </div>

                    <div className="transfer-confirmation-row">
                      <span className="transfer-confirmation-label">Destination Account</span>
                      <span className="transfer-confirmation-value">
                        {form.destinationType === 'mine'
                          ? (selectedDestinationAccount?.accountNumber || recipientAccountNumber || 'N/A')
                          : (recipientAccountNumber || form.destinationAccountNumber || 'N/A')}
                      </span>
                    </div>

                    <div className="transfer-confirmation-row">
                      <span className="transfer-confirmation-label">Amount</span>
                      <span className="transfer-confirmation-value transfer-confirmation-amount">
                        FJD {Number(form.amount || 0).toFixed(2)}
                      </span>
                    </div>

                    <div className="transfer-confirmation-row">
                      <span className="transfer-confirmation-label">Description</span>
                      <span className="transfer-confirmation-value">{form.description || 'N/A'}</span>
                    </div>
                  </div>

                  {isHighValueTransfer && (
                    <p className="transfer-confirmation-warning">
                      This transaction will be confirmed with the reception of a unique code.
                    </p>
                  )}

                  <div className="transfer-confirmation-actions">
                    <button
                      type="button"
                      className="btn"
                      onClick={handleBack}
                      disabled={processing}
                    >
                      Edit
                    </button>
                    <button
                      type="button"
                      className="btn"
                      onClick={handleCancelConfirmation}
                      disabled={processing}
                    >
                      Cancel
                    </button>
                    <button
                      type="button"
                      className="btn btn-primary"
                      onClick={handleConfirmTransfer}
                      disabled={processing}
                    >
                      {processing ? 'Processing...' : 'Confirm'}
                    </button>
                  </div>
                </div>
              )}

              {otpModalOpen && (
                <div
                  role="dialog"
                  aria-modal="true"
                  aria-label="OTP verification"
                  className="transfer-otp-backdrop"
                >
                  <div className="transfer-otp-modal" onClick={(e) => e.stopPropagation()}>
                    <h3>Enter OTP</h3>
                    <p className="transfer-otp-subtext">
                      Enter the 6-digit code sent to your email to complete this transfer.
                    </p>

                    <div className="form-group">
                      <label htmlFor="otpCode">One-Time Password</label>
                      <input
                        id="otpCode"
                        type="text"
                        inputMode="numeric"
                        pattern="[0-9]*"
                        maxLength={6}
                        value={otpCode}
                        onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                        placeholder="Enter 6-digit OTP"
                        className="transfer-otp-input"
                      />
                      {otpExpiresAt && (
                        <small className="transfer-otp-expiry">
                          OTP expires at: {new Date(otpExpiresAt).toLocaleString()}
                        </small>
                      )}
                    </div>

                    <div className="transfer-otp-actions">
                      <button
                        type="button"
                        className="btn transfer-otp-btn"
                        onClick={() => setOtpModalOpen(false)}
                        disabled={processing}
                      >
                        Cancel
                      </button>
                      <button
                        type="button"
                        className="btn transfer-otp-btn"
                        onClick={handleResendOtp}
                        disabled={processing}
                      >
                        {processing ? 'Sending...' : 'Resend OTP'}
                      </button>
                      <button
                        type="button"
                        className="btn btn-primary transfer-otp-btn"
                        onClick={handleVerifyOtp}
                        disabled={processing}
                      >
                        {processing ? 'Verifying...' : 'Confirm OTP'}
                      </button>
                    </div>
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      </main>
    </div>
  );
};

export default TransferPage;
