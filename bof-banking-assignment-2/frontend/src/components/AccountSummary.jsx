import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { canManageAccountHolders } from '../utils/roleUtils';

const formatCurrency = (value) => {
  const numericValue = Number(value ?? 0);
  return new Intl.NumberFormat('en-FJ', {
    style: 'currency',
    currency: 'FJD',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(numericValue);
};

/**
 * AccountSummary displays core account details for the accounts module.
 * For TELLER/ADMIN: shows button to manage account holders
 * For CUSTOMER: no management options shown
 */
const AccountSummary = ({ account }) => {
  const navigate = useNavigate();
  const { role } = useAuth();

  return (
    <article className="card" style={{ margin: 0 }}>
      <h3 style={{ marginTop: 0, marginBottom: 14 }}>Account Summary</h3>

      <div style={{ display: 'grid', gap: 8 }}>
        <p style={{ margin: 0 }}>
          <strong>Account Number:</strong> {account.accountNumber}
        </p>
        <p style={{ margin: 0 }}>
          <strong>Account Type:</strong> {account.accountType}
        </p>
        <p style={{ margin: 0 }}>
          <strong>Balance:</strong> {formatCurrency(account.balance)}
        </p>
        <p style={{ margin: 0 }}>
          <strong>Interest Earned:</strong> {formatCurrency(account.interestEarned)}
        </p>
      </div>

      {canManageAccountHolders(role) && (
        <button
          type="button"
          className="btn btn-secondary"
          onClick={() => navigate(`/accounts/${account.id}/holders`)}
          style={{ marginTop: 16, width: '100%' }}
        >
          Manage Account Holders
        </button>
      )}
    </article>
  );
};

export default AccountSummary;
