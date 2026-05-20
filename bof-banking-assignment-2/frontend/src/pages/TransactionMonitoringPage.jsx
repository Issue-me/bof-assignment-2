import React, { useEffect, useMemo, useState } from 'react';
import transactionService from '../services/transactionService';
import './TransactionMonitoring.css';

const PAGE_SIZE = 15;

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
    return '-';
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '-';
  }

  return date.toLocaleString('en-FJ', {
    year: 'numeric',
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
};

const toParamDateTime = (value) => {
  if (!value) {
    return undefined;
  }
  return value.length === 16 ? `${value}:00` : value;
};

const getDirection = (transaction) => {
  if (transaction.sourceAccountNumber && !transaction.destinationAccountNumber) {
    return 'DEBIT';
  }
  if (!transaction.sourceAccountNumber && transaction.destinationAccountNumber) {
    return 'CREDIT';
  }
  if (transaction.transactionType === 'DEPOSIT' || transaction.transactionType === 'INTEREST') {
    return 'CREDIT';
  }
  return 'DEBIT';
};

function TransactionMonitoringPage() {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const [sortField, setSortField] = useState('transaction_date');
  const [sortDir, setSortDir] = useState('desc');

  const [draftFilters, setDraftFilters] = useState({
    search: '',
    transactionType: 'ALL',
    status: 'ALL',
    from: '',
    to: '',
  });

  const [filters, setFilters] = useState({
    search: '',
    transactionType: 'ALL',
    status: 'ALL',
    from: '',
    to: '',
  });

  useEffect(() => {
    let mounted = true;

    const request = {
      page,
      size: PAGE_SIZE,
      sort: `${sortField},${sortDir}`,
      search: filters.search || undefined,
      transactionType: filters.transactionType === 'ALL' ? undefined : filters.transactionType,
      status: filters.status === 'ALL' ? undefined : filters.status,
      from: toParamDateTime(filters.from),
      to: toParamDateTime(filters.to),
    };

    setLoading(true);
    setError('');

    transactionService
      .getMonitoringTransactions(request)
      .then((data) => {
        if (!mounted) {
          return;
        }
        setRows(data?.content || []);
        setTotalPages(data?.totalPages || 0);
        setTotalElements(data?.totalElements || 0);
      })
      .catch((err) => {
        if (!mounted) {
          return;
        }
        setError(err.response?.data?.message || 'Unable to load transactions for monitoring.');
        setRows([]);
        setTotalPages(0);
        setTotalElements(0);
      })
      .finally(() => {
        if (mounted) {
          setLoading(false);
        }
      });

    return () => {
      mounted = false;
    };
  }, [page, sortField, sortDir, filters]);

  const pageLabel = useMemo(() => {
    if (!totalElements) {
      return '0 results';
    }
    const start = page * PAGE_SIZE + 1;
    const end = Math.min((page + 1) * PAGE_SIZE, totalElements);
    return `${start}-${end} of ${totalElements}`;
  }, [page, totalElements]);

  const onDraftChange = (event) => {
    const { name, value } = event.target;
    setDraftFilters((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  const applyFilters = (event) => {
    event.preventDefault();
    setFilters({ ...draftFilters });
    setPage(0);
  };

  const resetFilters = () => {
    const reset = {
      search: '',
      transactionType: 'ALL',
      status: 'ALL',
      from: '',
      to: '',
    };
    setDraftFilters(reset);
    setFilters(reset);
    setPage(0);
  };

  const onSort = (field) => {
    if (sortField === field) {
      setSortDir((prev) => (prev === 'asc' ? 'desc' : 'asc'));
      return;
    }
    setSortField(field);
    setSortDir('asc');
  };

  const sortMarker = (field) => {
    if (sortField !== field) {
      return '';
    }
    return sortDir === 'asc' ? ' ▲' : ' ▼';
  };

  return (
    <div className="transaction-monitoring-page">
      <header className="page-header">
        <h1>Transaction Monitoring</h1>
        <p>Teller module for system-wide transaction oversight and search.</p>
      </header>

      <section className="card monitoring-card">
          <form className="monitoring-filters" onSubmit={applyFilters}>
            <div className="monitoring-grid">
              <label>
                Search
                <input
                  type="text"
                  name="search"
                  value={draftFilters.search}
                  onChange={onDraftChange}
                  placeholder="Reference, account number, or description"
                />
              </label>

              <label>
                Transaction Type
                <select
                  name="transactionType"
                  value={draftFilters.transactionType}
                  onChange={onDraftChange}
                >
                  <option value="ALL">All types</option>
                  <option value="DEPOSIT">DEPOSIT</option>
                  <option value="WITHDRAWAL">WITHDRAWAL</option>
                  <option value="TRANSFER">TRANSFER</option>
                  <option value="BILL_PAYMENT">BILL_PAYMENT</option>
                  <option value="LOAN_PAYMENT">LOAN_PAYMENT</option>
                  <option value="INTEREST">INTEREST</option>
                  <option value="FEE">FEE</option>
                </select>
              </label>

              <label>
                Status
                <select name="status" value={draftFilters.status} onChange={onDraftChange}>
                  <option value="ALL">All statuses</option>
                  <option value="PENDING">PENDING</option>
                  <option value="COMPLETED">COMPLETED</option>
                  <option value="FAILED">FAILED</option>
                  <option value="CANCELLED">CANCELLED</option>
                  <option value="ACTIVE">ACTIVE</option>
                  <option value="PAUSED">PAUSED</option>
                </select>
              </label>

              <label>
                From
                <input type="datetime-local" name="from" value={draftFilters.from} onChange={onDraftChange} />
              </label>

              <label>
                To
                <input type="datetime-local" name="to" value={draftFilters.to} onChange={onDraftChange} />
              </label>
            </div>

            <div className="monitoring-actions">
              <button type="submit" className="btn btn-primary">Apply Filters</button>
              <button type="button" className="btn btn-secondary" onClick={resetFilters}>Reset</button>
            </div>
          </form>

          {loading ? (
            <div className="transaction-loading">Loading transactions...</div>
          ) : error ? (
            <div className="alert alert-error" role="alert">
              {error}
            </div>
          ) : (
            <>
              <div className="monitoring-meta">
                <span>{pageLabel}</span>
                <span>Sorted by {sortField} ({sortDir.toUpperCase()})</span>
              </div>

              <div className="monitoring-table-wrap">
                <table className="monitoring-table">
                  <thead>
                    <tr>
                      <th>
                        <button type="button" onClick={() => onSort('transaction_date')}>
                          Date{sortMarker('transaction_date')}
                        </button>
                      </th>
                      <th>
                        <button type="button" onClick={() => onSort('reference_number')}>
                          Reference{sortMarker('reference_number')}
                        </button>
                      </th>
                      <th>
                        <button type="button" onClick={() => onSort('transaction_type')}>
                          Type{sortMarker('transaction_type')}
                        </button>
                      </th>
                      <th>
                        <button type="button" onClick={() => onSort('status')}>
                          Status{sortMarker('status')}
                        </button>
                      </th>
                      <th>Direction</th>
                      <th>From Account</th>
                      <th>To Account</th>
                      <th>Description</th>
                      <th className="amount-col">
                        <button type="button" onClick={() => onSort('amount')}>
                          Amount{sortMarker('amount')}
                        </button>
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {rows.length === 0 ? (
                      <tr>
                        <td colSpan="9" className="no-results">No transactions found for the selected criteria.</td>
                      </tr>
                    ) : (
                      rows.map((row) => (
                        <tr key={row.id}>
                          <td>{formatDateTime(row.transactionDate)}</td>
                          <td>{row.referenceNumber || '-'}</td>
                          <td>{row.transactionType || '-'}</td>
                          <td>{row.status || '-'}</td>
                          <td>
                            <span className={`direction-pill direction-pill-${getDirection(row).toLowerCase()}`}>
                              {getDirection(row)}
                            </span>
                          </td>
                          <td>{row.sourceAccountNumber || '-'}</td>
                          <td>{row.destinationAccountNumber || '-'}</td>
                          <td>{row.description || '-'}</td>
                          <td className="amount-col">{formatCurrency(row.amount)}</td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>

              <nav className="transaction-pagination" aria-label="Transaction monitoring pagination">
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setPage((current) => Math.max(current - 1, 0))}
                  disabled={page === 0}
                >
                  Previous
                </button>
                <span>
                  Page {totalPages === 0 ? 0 : page + 1} of {totalPages}
                </span>
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setPage((current) => (totalPages > 0 ? Math.min(current + 1, totalPages - 1) : current))}
                  disabled={totalPages === 0 || page >= totalPages - 1}
                >
                  Next
                </button>
              </nav>
            </>
          )}
      </section>
    </div>
  );
}

export default TransactionMonitoringPage;
