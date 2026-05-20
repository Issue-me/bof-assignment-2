import React, { useEffect, useMemo, useState } from 'react';
import accountService from '../services/accountService';
import transactionService from '../services/transactionService';
import './Dashboard.css';
import './TransactionHistory.css';

const PAGE_SIZE = 10;

const formatCurrency = (amount) => {
  const value = Number(amount || 0);
  return new Intl.NumberFormat('en-FJ', {
    style: 'currency',
    currency: 'FJD',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
};

const formatDateTime = (value) => {
  if (!value) {
    return 'Date unavailable';
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return 'Date unavailable';
  }

  return date.toLocaleString('en-FJ', {
    year: 'numeric',
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
};

const toDirection = (transaction) => {
  if (transaction?.entryType === 'CREDIT') {
    return 'credit';
  }
  if (transaction?.entryType === 'DEBIT') {
    return 'debit';
  }

  return transaction?.transactionType === 'DEPOSIT' || transaction?.transactionType === 'INTEREST'
    ? 'credit'
    : 'debit';
};

const toTitle = (transaction) => {
  if (transaction?.description && transaction.description.trim()) {
    return transaction.description;
  }

  const rawType = transaction?.transactionType || 'TRANSACTION';
  return rawType
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
};

function TransactionHistoryPage() {
  const [accounts, setAccounts] = useState([]);
  const [transactions, setTransactions] = useState([]);
  const [filters, setFilters] = useState({
    accountId: 'all',
    entryType: 'all',
  });
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [accountsLoading, setAccountsLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let isMounted = true;

    accountService
      .getMyAccounts()
      .then((data) => {
        if (isMounted) {
          setAccounts(data || []);
        }
      })
      .catch(() => {
        if (isMounted) {
          setAccounts([]);
        }
      })
      .finally(() => {
        if (isMounted) {
          setAccountsLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, []);

  useEffect(() => {
    let isMounted = true;

    const request = {
      page,
      size: PAGE_SIZE,
    };

    if (filters.accountId !== 'all') {
      request.accountId = Number(filters.accountId);
    }

    if (filters.entryType !== 'all') {
      request.entryType = filters.entryType.toUpperCase();
    }

    setLoading(true);
    setError('');

    transactionService
      .getTransactions(request)
      .then((data) => {
        if (!isMounted) {
          return;
        }

        setTransactions(data?.content || []);
        setTotalPages(data?.totalPages || 0);
        setTotalElements(data?.totalElements || 0);
      })
      .catch((err) => {
        if (!isMounted) {
          return;
        }

        setError(
          err.response?.data?.message ||
            'Unable to load transaction history right now. Please try again.'
        );
        setTransactions([]);
        setTotalPages(0);
        setTotalElements(0);
      })
      .finally(() => {
        if (isMounted) {
          setLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, [page, filters.accountId, filters.entryType]);

  const accountLookup = useMemo(() => {
    const map = new Map();
    accounts.forEach((account) => {
      map.set(String(account.id), account);
    });
    return map;
  }, [accounts]);

  const selectedAccountLabel =
    filters.accountId !== 'all' ? accountLookup.get(String(filters.accountId))?.accountNumber : null;

  const handleFilterChange = (event) => {
    const { name, value } = event.target;
    setFilters((previous) => ({
      ...previous,
      [name]: value,
    }));
    setPage(0);
  };

  const goToPreviousPage = () => {
    setPage((current) => Math.max(current - 1, 0));
  };

  const goToNextPage = () => {
    setPage((current) => (totalPages > 0 ? Math.min(current + 1, totalPages - 1) : current));
  };

  return (
    <div className="transaction-history-page">
      <div className="page-header">
        <h1>Transaction History</h1>
        <p>Recent customer transactions from the last 3 months.</p>
      </div>

      <div className="transaction-history-card">
          <div className="transaction-history-toolbar">
            <h3>Recent activity</h3>
          </div>

          <div className="transaction-history-filters">
            <label>
              Account
              <select
                name="accountId"
                value={filters.accountId}
                onChange={handleFilterChange}
                disabled={accountsLoading}
              >
                <option value="all">All accounts</option>
                {accounts.map((account) => (
                  <option key={account.id} value={account.id}>
                    {account.accountNumber} - {account.accountType}
                  </option>
                ))}
              </select>
            </label>

            <label>
              Type
              <select name="entryType" value={filters.entryType} onChange={handleFilterChange}>
                <option value="all">All types</option>
                <option value="credit">Credit</option>
                <option value="debit">Debit</option>
              </select>
            </label>
          </div>

          {loading ? (
            <div className="transaction-loading">Loading transaction history...</div>
          ) : error ? (
            <div className="alert alert-error" role="alert">
              {error}
            </div>
          ) : transactions.length === 0 ? (
            <div className="transaction-empty-state" role="status">
              <div className="empty-icon">[]</div>
              <h4>No transactions found</h4>
              <p>Try changing your filters or check again after a new transaction is made.</p>
            </div>
          ) : (
            <>
              <div className="transaction-count">{totalElements} transactions found</div>
              <div className="transaction-list">
                {transactions.map((transaction) => {
                  const direction = toDirection(transaction);
                  const amountPrefix = direction === 'credit' ? '+' : '-';

                  return (
                    <article
                      key={transaction.id}
                      className={`transaction-item transaction-item--${direction}`}
                    >
                      <div className="transaction-icon" aria-hidden="true">
                        {direction === 'credit' ? '↓' : '↑'}
                      </div>

                      <div className="transaction-content">
                        <div className="transaction-main-row">
                          <h4>{toTitle(transaction)}</h4>
                          <span className={`transaction-pill transaction-pill--${direction}`}>
                            {direction}
                          </span>
                        </div>

                        <div className="transaction-meta">
                          <span>{formatDateTime(transaction.transactionDate)}</span>
                          <span>Type: {transaction.transactionType || 'N/A'}</span>
                        </div>

                        <div className="transaction-accounts">
                          <span>From: {transaction.sourceAccountNumber || 'N/A'}</span>
                          <span>To: {transaction.destinationAccountNumber || 'N/A'}</span>
                        </div>
                      </div>

                      <div className="transaction-financials">
                        <p className={`transaction-amount transaction-amount--${direction}`}>
                          {amountPrefix}
                          {formatCurrency(transaction.amount)}
                        </p>
                        <p className="transaction-balance">
                          Balance: {formatCurrency(transaction.balanceAfter)}
                        </p>
                      </div>
                    </article>
                  );
                })}
              </div>

              {totalPages > 1 && (
                <nav className="transaction-pagination" aria-label="Transaction pagination">
                  <button
                    type="button"
                    className="btn btn-secondary"
                    onClick={goToPreviousPage}
                    disabled={page === 0}
                  >
                    Previous
                  </button>
                  <span className="pagination-info">
                    Page {page + 1} of {totalPages}
                  </span>
                  <button
                    type="button"
                    className="btn btn-secondary"
                    onClick={goToNextPage}
                    disabled={page >= totalPages - 1}
                  >
                    Next
                  </button>
                </nav>
              )}
            </>
          )}
        </div>
    </div>
  );
}

export default TransactionHistoryPage;