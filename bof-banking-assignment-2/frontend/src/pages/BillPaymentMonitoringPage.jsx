import React, { useEffect, useMemo, useState } from 'react';
import billPaymentService from '../services/billPaymentService';
import billerService from '../services/billerService';
import './BillPaymentMonitoringPage.css';

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

function BillPaymentMonitoringPage() {
  const [rows, setRows] = useState([]);
  const [billers, setBillers] = useState([]);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const [draftFilters, setDraftFilters] = useState({
    search: '',
    billerId: 'ALL',
    status: 'ALL',
    fromDate: '',
    toDate: '',
    minAmount: '',
    maxAmount: '',
  });

  const [filters, setFilters] = useState({
    search: '',
    billerId: 'ALL',
    status: 'ALL',
    fromDate: '',
    toDate: '',
    minAmount: '',
    maxAmount: '',
  });

  useEffect(() => {
    billerService
      .getAllBillers()
      .then((response) => setBillers(response.data || []))
      .catch(() => setBillers([]));
  }, []);

  useEffect(() => {
    let mounted = true;

    const request = {
      page,
      size: PAGE_SIZE,
      sort: 'createdAt,desc',
      search: filters.search || undefined,
      billerId: filters.billerId === 'ALL' ? undefined : Number(filters.billerId),
      status: filters.status === 'ALL' ? undefined : filters.status,
      fromDate: toParamDateTime(filters.fromDate),
      toDate: toParamDateTime(filters.toDate),
      minAmount: filters.minAmount === '' ? undefined : Number(filters.minAmount),
      maxAmount: filters.maxAmount === '' ? undefined : Number(filters.maxAmount),
    };

    setLoading(true);
    setError('');

    billPaymentService
      .getMonitoringBillPayments(request)
      .then((response) => {
        if (!mounted) {
          return;
        }

        const data = response.data;
        setRows(data?.content || []);
        setTotalPages(data?.totalPages || 0);
        setTotalElements(data?.totalElements || 0);
      })
      .catch((err) => {
        if (!mounted) {
          return;
        }
        setError(err.response?.data?.message || 'Unable to load bill payments.');
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
  }, [page, filters]);

  const billerNameMap = useMemo(() => {
    return new Map((billers || []).map((biller) => [biller.id, biller.billerName]));
  }, [billers]);

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
      billerId: 'ALL',
      status: 'ALL',
      fromDate: '',
      toDate: '',
      minAmount: '',
      maxAmount: '',
    };

    setDraftFilters(reset);
    setFilters(reset);
    setPage(0);
  };

  return (
    <div className="bill-monitoring-page">
      <header className="page-header">
        <h1>Bill Payment Monitoring</h1>
        <p>Teller module for reviewing bill payment transactions and risk indicators.</p>
      </header>

      <section className="card">
        <form className="monitor-filters" onSubmit={applyFilters}>
          <div className="monitor-grid">
            <label>
              Search
              <input
                type="text"
                name="search"
                value={draftFilters.search}
                onChange={onDraftChange}
                placeholder="Reference, description, source account"
              />
            </label>

            <label>
              Biller
              <select name="billerId" value={draftFilters.billerId} onChange={onDraftChange}>
                <option value="ALL">All billers</option>
                {billers.map((biller) => (
                  <option key={biller.id} value={biller.id}>
                    {biller.billerName}
                  </option>
                ))}
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
              </select>
            </label>

            <label>
              From Date
              <input type="datetime-local" name="fromDate" value={draftFilters.fromDate} onChange={onDraftChange} />
            </label>

            <label>
              To Date
              <input type="datetime-local" name="toDate" value={draftFilters.toDate} onChange={onDraftChange} />
            </label>

            <label>
              Min Amount (FJD)
              <input type="number" min="0" step="0.01" name="minAmount" value={draftFilters.minAmount} onChange={onDraftChange} />
            </label>

            <label>
              Max Amount (FJD)
              <input type="number" min="0" step="0.01" name="maxAmount" value={draftFilters.maxAmount} onChange={onDraftChange} />
            </label>
          </div>

          <div className="monitor-actions">
            <button type="submit" className="btn btn-primary">Apply Filters</button>
            <button type="button" className="btn btn-secondary" onClick={resetFilters}>Reset</button>
          </div>
        </form>

        {loading ? (
          <div className="loading">Loading bill payments...</div>
        ) : error ? (
          <div className="alert alert-error">{error}</div>
        ) : (
          <>
            <div className="monitor-meta">{pageLabel}</div>
            <div className="table-wrap">
              <table className="monitor-table">
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Reference</th>
                    <th>Biller</th>
                    <th>Customer Bill Account</th>
                    <th>Source Account</th>
                    <th>Status</th>
                    <th>Amount</th>
                    <th>Description</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.length === 0 ? (
                    <tr>
                      <td colSpan="8" className="center muted">No bill payments found for the selected criteria.</td>
                    </tr>
                  ) : (
                    rows.map((row) => (
                      <tr key={row.id}>
                        <td>{formatDateTime(row.createdAt)}</td>
                        <td>{row.paymentReference || '-'}</td>
                        <td>{billerNameMap.get(row.billerId) || '-'}</td>
                        <td>{row.accountNumber || '-'}</td>
                        <td>{row.sourceAccountNumber || '-'}</td>
                        <td>{row.status || '-'}</td>
                        <td>{formatCurrency(row.amount)}</td>
                        <td>{row.description || '-'}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>

            <nav className="pagination" aria-label="Bill payment monitoring pagination">
              <button
                type="button"
                className="btn btn-secondary"
                onClick={() => setPage((current) => Math.max(current - 1, 0))}
                disabled={page === 0}
              >
                Previous
              </button>
              <span>Page {totalPages === 0 ? 0 : page + 1} of {totalPages}</span>
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

export default BillPaymentMonitoringPage;
