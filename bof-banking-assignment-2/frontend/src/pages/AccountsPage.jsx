import React, { useEffect, useState, useCallback } from 'react';
import api from '../services/api';
import './Dashboard.css';

const INTERNAL_ACCOUNT_NUMBER = 'BOF90000001';
const INTERNAL_ACCOUNT_NAME   = 'BANK_INTERNAL_OPERATIONS';
const AUTO_REFRESH_MS          = 60_000;
const LATE_REFRESH_MS          = 3_000;

const AccountsPage = () => {
  const [accounts, setAccounts]               = useState([]);
  const [liveRatePct, setLiveRatePct]         = useState(null);
  const [liveRateDate, setLiveRateDate]        = useState(null);
  const [loading, setLoading]                 = useState(true);
  const [refreshing, setRefreshing]           = useState(false);
  const [error, setError]                     = useState('');
  const [lastUpdated, setLastUpdated]         = useState(null);
  const [nrwhtRefundInfo, setNrwhtRefundInfo] = useState(null); // ← NEW

  const fetchAll = useCallback(async (isSoftRefresh = false) => {
    if (isSoftRefresh) setRefreshing(true);
    else               setLoading(true);
    setError('');

    try {
      const [accountsRes, rateRes] = await Promise.all([
        api.get('/accounts'),
        api.get('/interest-rates/current').catch(() => null),
      ]);

      const customerAccounts = (accountsRes.data ?? []).filter(acc =>
        acc.accountNumber !== INTERNAL_ACCOUNT_NUMBER &&
        acc.accountName   !== INTERNAL_ACCOUNT_NAME
      );
      setAccounts(customerAccounts);

      if (rateRes?.data?.hasRate && rateRes.data.currentAnnualRatePct != null) {
        setLiveRatePct(parseFloat(rateRes.data.currentAnnualRatePct));
        setLiveRateDate(rateRes.data.effectiveSince ?? null);
      }

      setLastUpdated(new Date());
    } catch (err) {
      setError(err?.response?.data?.message || 'Failed to load accounts. Please try again.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }

    // ── NRWHT refund detection — non-fatal, runs after main fetch ────────────
    // Piggybacks on the existing tax report endpoint to check whether an NRWHT
    // refund was credited this year (server sets nrwhtRefunded=true when a
    // matching NRWHT Refund transaction exists in the DB).
    try {
      const taxRes = await api.get(`/tax/report?year=${new Date().getFullYear()}`);
      if (taxRes.data?.nrwhtRefunded === true
          && parseFloat(taxRes.data?.nrwhtWithheld ?? 0) > 0) {
        setNrwhtRefundInfo({
          amount: taxRes.data.nrwhtWithheld,
          ref:    taxRes.data.nrwhtRefundReference,
          tin:    taxRes.data.tinNumber,
        });
      } else {
        setNrwhtRefundInfo(null);
      }
    } catch {
      // non-fatal — accounts still show even if tax report fails
    }
  }, []);

  // Initial load
  useEffect(() => { fetchAll(false); }, [fetchAll]);

  // Late refresh — picks up refund credits committed after mount
  useEffect(() => {
    const timer = setTimeout(() => fetchAll(true), LATE_REFRESH_MS);
    return () => clearTimeout(timer);
  }, [fetchAll]);

  // Auto-refresh every 60 seconds
  useEffect(() => {
    const timer = setInterval(() => fetchAll(true), AUTO_REFRESH_MS);
    return () => clearInterval(timer);
  }, [fetchAll]);

  const displayRate = (account) => {
    if (account.accountType === 'SAVINGS' && liveRatePct != null)
      return liveRatePct.toFixed(2) + '%';
    const raw = parseFloat(account.interestRate ?? 0);
    const pct = raw < 1 ? raw * 100 : raw;
    return pct.toFixed(2) + '%';
  };

  const isActive = (account) =>
    account.active === true || account.active === 'true';

  const fmtTime = (date) => date
    ? date.toLocaleTimeString('en-FJ', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
    : '';

  const thStyle = {
    padding: '12px 16px',
    textAlign: 'left',
    fontWeight: 600,
    color: '#2c3e50',
    borderBottom: '2px solid #e8ecef',
    fontSize: 13,
  };

  return (
    <div className="dashboard-wrapper">
      <main className="dashboard-main">
        <div className="dashboard-content-inner">
          <div className="page-header">
            <h1>My Accounts</h1>
            <p>View your Bank of Fiji accounts and balances.</p>
          </div>

          {/* Last updated + manual refresh */}
          {lastUpdated && !loading && (
            <div style={{
              display: 'flex', justifyContent: 'space-between', alignItems: 'center',
              marginBottom: 12, fontSize: 12, color: '#6b7280',
            }}>
              <span>Last updated: {fmtTime(lastUpdated)}</span>
              <button
                onClick={() => fetchAll(true)}
                disabled={refreshing}
                style={{
                  background: 'none', border: '1px solid #e5e7eb', borderRadius: 6,
                  padding: '4px 10px', fontSize: 12, color: '#374151', cursor: 'pointer',
                }}
              >
                {refreshing ? '↻ Refreshing…' : '↻ Refresh'}
              </button>
            </div>
          )}

          {/* NRWHT refund notice — shown when server confirms refund was credited */}
          {nrwhtRefundInfo && (
            <div style={{
              background: 'rgba(16,185,129,0.08)', border: '1px solid rgba(16,185,129,0.28)',
              borderRadius: 8, padding: '14px 18px', marginBottom: 16,
              display: 'flex', alignItems: 'flex-start', gap: 12,
            }}>
              <span style={{ fontSize: 22, flexShrink: 0 }}>💸</span>
              <div>
                <strong style={{ fontSize: 14 }}>NRWHT refund credited to your account</strong>
                <div style={{ fontSize: 13, color: '#374151', marginTop: 4, lineHeight: 1.6 }}>
                  Your TIN (<strong>{nrwhtRefundInfo.tin}</strong>) was registered.
                  NRWHT of{' '}
                  <strong style={{ color: '#059669' }}>
                    FJD {parseFloat(nrwhtRefundInfo.amount).toLocaleString('en-FJ', {
                      minimumFractionDigits: 2, maximumFractionDigits: 2,
                    })}
                  </strong>{' '}
                  withheld this year has been refunded to your savings account.
                  Future interest will use RIWT (10%).
                  {nrwhtRefundInfo.ref && (
                    <span style={{ display: 'block', marginTop: 4, fontSize: 12, color: '#6b7280' }}>
                      Reference: <code>{nrwhtRefundInfo.ref}</code>
                    </span>
                  )}
                </div>
              </div>
            </div>
          )}

          {error && (
            <div style={{
              background: '#fef2f2', border: '1px solid #fecaca',
              borderRadius: 8, padding: '12px 16px', marginBottom: 16,
              fontSize: 13, color: '#991b1b',
            }}>
              ⚠️ {error}
            </div>
          )}

          <div className="card">
            {loading ? (
              <p style={{ padding: 24, color: '#6b7280' }}>Loading accounts…</p>
            ) : accounts.length === 0 ? (
              <p style={{ padding: 24, color: '#6b7280' }}>You don't have any accounts yet.</p>
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', backgroundColor: '#fff' }}>
                  <thead>
                    <tr style={{ backgroundColor: '#f5f7fa' }}>
                      <th style={thStyle}>Account Number</th>
                      <th style={thStyle}>Name</th>
                      <th style={thStyle}>Type</th>
                      <th style={{ ...thStyle, textAlign: 'right' }}>Balance (FJD)</th>
                      <th style={{ ...thStyle, textAlign: 'right' }}>
                        Interest Rate
                        <span
                          title="Savings rate reflects the current RBF-mandated rate set by your bank."
                          style={{ marginLeft: 4, cursor: 'help', opacity: 0.5, fontSize: 11 }}
                        >ⓘ</span>
                      </th>
                      <th style={{ ...thStyle, textAlign: 'center' }}>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {accounts.map((account) => (
                      <tr
                        key={account.id}
                        style={{
                          borderBottom: '1px solid #e8ecef',
                          background: account.accountType === 'SAVINGS'
                            ? 'rgba(16,185,129,0.02)' : '#fff',
                        }}
                      >
                        <td style={{ padding: '14px 16px', color: '#2c3e50', fontWeight: 600, fontSize: 13 }}>
                          {account.accountNumber}
                        </td>
                        <td style={{ padding: '14px 16px', color: '#2c3e50', fontSize: 14 }}>
                          {account.accountName ||
                            (account.accountType === 'SAVINGS' ? 'Primary Savings' : 'Simple Access')}
                        </td>
                        <td style={{ padding: '14px 16px' }}>
                          <span style={{
                            display: 'inline-block', padding: '3px 10px', borderRadius: 20,
                            fontSize: 12, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.03em',
                            background: account.accountType === 'SAVINGS'
                              ? 'rgba(59,130,246,0.1)' : 'rgba(107,114,128,0.1)',
                            color: account.accountType === 'SAVINGS' ? '#1d4ed8' : '#374151',
                          }}>
                            {account.accountType === 'SIMPLE_ACCESS' ? 'Simple Access' : account.accountType}
                          </span>
                        </td>
                        <td style={{ padding: '14px 16px', textAlign: 'right', color: '#111827', fontWeight: 600, fontSize: 15 }}>
                          {parseFloat(account.balance).toLocaleString('en-FJ', {
                            minimumFractionDigits: 2, maximumFractionDigits: 2,
                          })}
                        </td>
                        <td style={{ padding: '14px 16px', textAlign: 'right', color: '#374151' }}>
                          <span style={{ fontWeight: 500 }}>{displayRate(account)}</span>
                          {account.accountType === 'SAVINGS' && liveRatePct != null && (
                            <span style={{ display: 'block', fontSize: 11, color: '#6b7280', marginTop: 2 }}>
                              RBF rate
                            </span>
                          )}
                        </td>
                        <td style={{ padding: '14px 16px', textAlign: 'center' }}>
                          <span style={{
                            display: 'inline-block', padding: '3px 12px', borderRadius: 20,
                            fontSize: 12, fontWeight: 600,
                            background: isActive(account)
                              ? 'rgba(16,185,129,0.12)' : 'rgba(156,163,175,0.15)',
                            color: isActive(account) ? '#065f46' : '#6b7280',
                          }}>
                            {isActive(account) ? 'Active' : 'Inactive'}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>

        <p style={{ fontSize: 12, color: '#9ca3af', marginTop: 12, textAlign: 'center' }}>
          Balances update automatically every 60 seconds. Interest is credited monthly per RBF directive.
        </p>
      </main>
    </div>
  );
};

export default AccountsPage;