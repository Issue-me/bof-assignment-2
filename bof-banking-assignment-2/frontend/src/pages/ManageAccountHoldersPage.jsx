import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import accountHolderService from '../services/accountHolderService';
import accountService from '../services/accountService';
import ConfirmDialog from '../components/ConfirmDialog';
import './Dashboard.css';
import './ManageAccountHolders.css';

/**
 * ManageAccountHoldersPage - manage account holders for a specific account
 */
const ManageAccountHoldersPage = () => {
  const { accountId } = useParams();
  const navigate = useNavigate();
  const [account, setAccount] = useState(null);
  const [holders, setHolders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  
  // Form state for adding holder
  const [showAddForm, setShowAddForm] = useState(false);
  const [userEmail, setUserEmail] = useState('');
  const [role, setRole] = useState('JOINT');
  const [submitting, setSubmitting] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [holderToRemove, setHolderToRemove] = useState(null);

  const fetchData = async () => {
    setLoading(true);
    setError('');
    try {
      const [accountData, holdersData] = await Promise.all([
        accountService.getAccountById(accountId),
        accountHolderService.getAccountHolders(accountId),
      ]);
      setAccount(accountData);
      setHolders(holdersData);
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load account holder data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accountId]);

  const handleAddHolder = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setError('');
    setSuccessMessage('');

    try {
      await accountHolderService.addAccountHolder(accountId, {
        userEmail,
        role,
      });
      setSuccessMessage('Account holder added successfully');
      setUserEmail('');
      setRole('JOINT');
      setShowAddForm(false);
      fetchData();
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to add account holder');
    } finally {
      setSubmitting(false);
    }
  };

  const handleRemoveHolder = (userId, holderName) => {
    setHolderToRemove({ userId, holderName });
    setShowConfirm(true);
  };

  const confirmRemoveHolder = async () => {
    if (!holderToRemove) return;

    setShowConfirm(false);

    setError('');
    setSuccessMessage('');
    try {
      await accountHolderService.removeAccountHolder(accountId, holderToRemove.userId);
      setSuccessMessage('Account holder removed successfully');
      fetchData();
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to remove account holder');
    } finally {
      setHolderToRemove(null);
    }
  };

  const getRoleBadgeColor = (role) => {
    switch (role) {
      case 'PRIMARY':
        return '#3498db';
      case 'JOINT':
        return '#2ecc71';
      case 'AUTHORIZED':
        return '#95a5a6';
      default:
        return '#7f8c8d';
    }
  };

  if (loading) {
    return (
      <div className="dashboard-wrapper">
        <main className="dashboard-main">
          <p>Loading account holders...</p>
        </main>
      </div>
    );
  }

  return (
    <div className="manage-account-holders-page">
      <div className="page-header">
        <h1>Manage Account Holders</h1>
        {account && <p>{account.accountName || 'Account'} - {account.accountNumber}</p>}
      </div>

      <button
        type="button"
        className="btn btn-secondary holders-back-btn"
        onClick={() => navigate('/manage-accounts')}
      >
        ← Back to Manage Accounts
      </button>

      {error && (
        <div className="card holders-alert holders-alert-error">
          <p>{error}</p>
        </div>
      )}

      {successMessage && (
        <div className="card holders-alert holders-alert-success">
          <p>{successMessage}</p>
        </div>
      )}

      <div className="card">
          <div className="holders-toolbar">
            <h3>Account Holders ({holders.length})</h3>
            {!showAddForm && (
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => setShowAddForm(true)}
              >
                + Add Account Holder
              </button>
            )}
          </div>

          {showAddForm && (
            <div className="holders-form-panel">
              <h4>Add Account Holder</h4>
              <form onSubmit={handleAddHolder}>
                <div className="holders-form-group">
                  <label>
                    User Email
                  </label>
                  <input
                    type="email"
                    value={userEmail}
                    onChange={(e) => setUserEmail(e.target.value)}
                    required
                    placeholder="Enter user's email address"
                    className="holders-input"
                  />
                </div>
                <div className="holders-form-group">
                  <label>
                    Role
                  </label>
                  <select
                    value={role}
                    onChange={(e) => setRole(e.target.value)}
                    className="holders-input"
                  >
                    <option value="PRIMARY">Primary - Full permissions</option>
                    <option value="JOINT">Joint - Full access</option>
                    <option value="AUTHORIZED">Authorized - View and transactions only</option>
                  </select>
                </div>
                <div className="holders-form-actions">
                  <button
                    type="submit"
                    className="btn btn-primary"
                    disabled={submitting}
                  >
                    {submitting ? 'Adding...' : 'Add Holder'}
                  </button>
                  <button
                    type="button"
                    className="btn btn-secondary"
                    onClick={() => {
                      setShowAddForm(false);
                      setUserEmail('');
                      setRole('JOINT');
                    }}
                    disabled={submitting}
                  >
                    Cancel
                  </button>
                </div>
              </form>
            </div>
          )}

          {holders.length === 0 ? (
            <p>No account holders found.</p>
          ) : (
            <div className="holders-list">
              {holders.map((holder) => (
                <div
                  key={holder.id}
                  className="holder-item"
                >
                  <div>
                    <div className="holder-item-head">
                      <strong>{holder.userFullName}</strong>
                      <span
                        className="holder-role-badge"
                        style={{ backgroundColor: getRoleBadgeColor(holder.role) }}
                      >
                        {holder.role}
                      </span>
                    </div>
                    <p className="holder-meta">
                      {holder.userEmail}
                    </p>
                    <p className="holder-date-meta">
                      Added: {new Date(holder.addedAt).toLocaleDateString()}
                    </p>
                  </div>
                  <button
                    type="button"
                    className="btn btn-danger"
                    onClick={() => handleRemoveHolder(holder.userId, holder.userFullName)}
                    disabled={holder.role === 'PRIMARY' && holders.filter(h => h.role === 'PRIMARY').length === 1}
                    title={
                      holder.role === 'PRIMARY' && holders.filter(h => h.role === 'PRIMARY').length === 1
                        ? 'Cannot remove the last primary account holder'
                        : 'Remove account holder'
                    }
                  >
                    Remove
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>

        {showConfirm && holderToRemove && (
          <ConfirmDialog
            title="Remove Account Holder"
            message={`Are you sure you want to remove ${holderToRemove.holderName} as an account holder?`}
            confirmText="Remove"
            cancelText="Cancel"
            confirmVariant="success"
            cancelVariant="cancel-danger"
            variant="warning"
            onConfirm={confirmRemoveHolder}
            onCancel={() => {
              setShowConfirm(false);
              setHolderToRemove(null);
            }}
          />
        )}
    </div>
  );
};

export default ManageAccountHoldersPage;
