import React, { useEffect, useState } from 'react';
import transactionService from '../services/transactionService';
import './Dashboard.css';
import './TransferLimits.css';

const amount = (value) => Number(value || 0).toLocaleString('en-FJ', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const labelFor = (category) => {
  if (category === 'OWN_ACCOUNT') {
    return 'Own Account Transfers';
  }
  if (category === 'EXTERNAL') {
    return 'All Other Transfers';
  }
  return category;
};

const TransferLimitsPage = () => {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let mounted = true;

    transactionService
      .getTransferLimits()
      .then((response) => {
        if (mounted) {
          setData(response);
        }
      })
      .catch((err) => {
        if (mounted) {
          setError(
            err.response?.data?.message ||
              err.response?.data?.error ||
              'Unable to load transfer limits right now.'
          );
        }
      })
      .finally(() => {
        if (mounted) {
          setLoading(false);
        }
      });

    return () => {
      mounted = false;
    };
  }, []);

  return (
    <div className="dashboard-wrapper transfer-limits-page">
      <main className="dashboard-main transfer-limits-main">
        <div className="welcome-banner">
          <h2>Transfer Limits</h2>
          <p>Configured limits and your used amounts by rolling period.</p>
        </div>

        <div className="card transfer-limits-card">
          {loading && <p>Loading transfer limits...</p>}

          {!loading && error && (
            <div className="alert alert-error" role="alert">
              {error}
            </div>
          )}

          {!loading && !error && data?.categories?.map((category) => (
            <div key={category.category} className="transfer-limits-group">
              <h3>{labelFor(category.category)}</h3>
              <div className="transfer-limits-table-wrap">
                <table className="transfer-limits-table">
                  <thead>
                    <tr>
                      <th>Period</th>
                      <th className="ta-right">Limit (FJD)</th>
                      <th className="ta-right">Used (FJD)</th>
                      <th className="ta-right">Remaining (FJD)</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      <td>Daily (last 24h)</td>
                      <td className="ta-right">{amount(category.dailyLimit)}</td>
                      <td className="ta-right">{amount(category.dailyUsed)}</td>
                      <td className="ta-right">{amount(category.dailyRemaining)}</td>
                    </tr>
                    <tr>
                      <td>Weekly (last 7d)</td>
                      <td className="ta-right">{amount(category.weeklyLimit)}</td>
                      <td className="ta-right">{amount(category.weeklyUsed)}</td>
                      <td className="ta-right">{amount(category.weeklyRemaining)}</td>
                    </tr>
                    <tr>
                      <td>Monthly (last 1m)</td>
                      <td className="ta-right">{amount(category.monthlyLimit)}</td>
                      <td className="ta-right">{amount(category.monthlyUsed)}</td>
                      <td className="ta-right">{amount(category.monthlyRemaining)}</td>
                    </tr>
                    <tr>
                      <td>Yearly (last 12m)</td>
                      <td className="ta-right">{amount(category.yearlyLimit)}</td>
                      <td className="ta-right">{amount(category.yearlyUsed)}</td>
                      <td className="ta-right">{amount(category.yearlyRemaining)}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          ))}

          {!loading && !error && (
            <small className="transfer-limits-timezone">
              Timezone: {data?.timezone || 'Pacific/Fiji'}
            </small>
          )}
        </div>
      </main>
    </div>
  );
};

export default TransferLimitsPage;
