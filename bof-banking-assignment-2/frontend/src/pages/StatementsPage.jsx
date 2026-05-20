import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import statementService from '../services/statementService';
import api from '../services/api';
import Toast from '../components/Toast';
import './StatementsPage.css';

/**
 * StatementsPage - allows customers to view and download bank statements.
 * Displays available statements and allows filtering by date range.
 */
const StatementsPage = () => {
  useAuth();
  const [accounts, setAccounts] = useState([]);
  const [selectedAccount, setSelectedAccount] = useState(null);
  const [statements, setStatements] = useState([]);
  const [loading, setLoading] = useState(false);
  const [generatingStatements, setGeneratingStatements] = useState(false);
  const [downloadingPdf, setDownloadingPdf] = useState(false);
  const [toast, setToast] = useState(null);
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');

  // Fetch user accounts on component mount
  useEffect(() => {
    fetchUserAccounts();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Fetch statements when account selection changes
  useEffect(() => {
    if (selectedAccount) {
      fetchStatements();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedAccount]);

  const fetchUserAccounts = async () => {
    try {
      setLoading(true);
      const response = await api.get('/accounts');
      const data = response.data;
      if (Array.isArray(data)) {
        setAccounts(data);
        if (data.length > 0) {
          setSelectedAccount(data[0]);
        }
      } else {
        showToast('Failed to load accounts', 'error');
      }
    } catch (error) {
      console.error('Error fetching accounts:', error);
      showToast('Error loading accounts', 'error');
    } finally {
      setLoading(false);
    }
  };

  const fetchStatements = async () => {
    if (!selectedAccount) return;

    try {
      setLoading(true);
      const data = await statementService.getAvailableStatements(selectedAccount.id);
      setStatements(data.map((statement) => ({ ...statement, isGeneratedResult: false })));
      
      if (data.length === 0) {
        showToast('No statements available for this account', 'info');
      }
    } catch (error) {
      console.error('Error fetching statements:', error);
      showToast('Failed to load statements', 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleGenerateCustomStatement = async () => {
    if (!selectedAccount) {
      showToast('Please select an account', 'error');
      return;
    }

    if (!fromDate || !toDate) {
      showToast('Please select both start and end dates', 'error');
      return;
    }

    if (new Date(fromDate) > new Date(toDate)) {
      showToast('Start date must be before end date', 'error');
      return;
    }

    try {
      setGeneratingStatements(true);

      // Refresh statements first, then show only those within the chosen date range.
      const data = await statementService.getAvailableStatements(selectedAccount.id);
      const selectedFromDate = new Date(fromDate);
      const selectedToDate = new Date(toDate);

      const filteredStatements = data.filter((statement) => {
        const statementStartDate = new Date(statement.periodStartDate);
        const statementEndDate = new Date(statement.periodEndDate);
        return statementStartDate >= selectedFromDate && statementEndDate <= selectedToDate;
      });

      setStatements(filteredStatements.map((statement) => ({ ...statement, isGeneratedResult: true })));

      if (filteredStatements.length === 0) {
        showToast('No statements found in the selected date range', 'info');
      } else {
        showToast(`Found ${filteredStatements.length} statement(s) in selected range`, 'success');
      }
    } catch (error) {
      console.error('Error generating custom statement:', error);
      showToast('Failed to generate statement', 'error');
    } finally {
      setGeneratingStatements(false);
    }
  };

  const handleDownloadStatement = async (statement) => {
    if (!selectedAccount) {
      showToast('Please select an account', 'error');
      return;
    }

    try {
      setDownloadingPdf(true);
      await statementService.downloadStatementPdf(
        selectedAccount.id,
        statement.periodStartDate,
        statement.periodEndDate
      );
      showToast('Statement downloaded successfully', 'success');
    } catch (error) {
      console.error('Error downloading statement:', error);
      showToast('Failed to download statement', 'error');
    } finally {
      setDownloadingPdf(false);
    }
  };

  const handleClearFilter = async () => {
    setFromDate('');
    setToDate('');

    if (!selectedAccount) return;

    try {
      setLoading(true);
      const data = await statementService.getAvailableStatements(selectedAccount.id);
      setStatements(data.map((statement) => ({ ...statement, isGeneratedResult: false })));
      showToast('Date filter cleared. Showing all available statements.', 'success');
    } catch (error) {
      console.error('Error clearing statement filter:', error);
      showToast('Failed to reload statements', 'error');
    } finally {
      setLoading(false);
    }
  };

  const showToast = (message, type = 'info') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 4000);
  };

  const formatDate = (dateString) => {
    return new Date(dateString).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    });
  };

  return (
    <div className="statements-page">
      {toast && <Toast message={toast.message} type={toast.type} />}

      <div className="statements-container">
        <h1>Bank Statements</h1>
        <p className="page-subtitle">View your bank statements</p>

        {/* Account Selection */}
        <div className="account-selection-section">
          <label htmlFor="account-select">Select Account:</label>
          <select
            id="account-select"
            value={selectedAccount?.id || ''}
            onChange={(e) => {
              const selected = accounts.find(acc => acc.id === parseInt(e.target.value));
              setSelectedAccount(selected);
            }}
            className="account-select"
            disabled={loading}
          >
            <option value="">-- Select an account --</option>
            {accounts.map((account) => (
              <option key={account.id} value={account.id}>
                {account.accountNumber} - {account.accountType} (Balance: FJD {account.balance.toFixed(2)})
              </option>
            ))}
          </select>
        </div>

        {selectedAccount && (
          <>
            {/* Custom Date Range Section */}
            <div className="custom-statement-section">
              <h2>Find Statements by Date Range</h2>
              <div className="date-range-inputs">
                <div className="input-group">
                  <label htmlFor="from-date">From Date:</label>
                  <input
                    type="date"
                    id="from-date"
                    value={fromDate}
                    onChange={(e) => setFromDate(e.target.value)}
                    disabled={generatingStatements}
                  />
                </div>
                <div className="input-group">
                  <label htmlFor="to-date">To Date:</label>
                  <input
                    type="date"
                    id="to-date"
                    value={toDate}
                    onChange={(e) => setToDate(e.target.value)}
                    disabled={generatingStatements}
                  />
                </div>
              </div>
              <div className="statement-actions">
                <button
                  onClick={handleGenerateCustomStatement}
                  disabled={generatingStatements || loading}
                  className="btn btn-primary"
                >
                  {generatingStatements ? 'Generating...' : 'Generate Statements'}
                </button>
                <button
                  onClick={handleClearFilter}
                  disabled={loading || generatingStatements}
                  className="btn btn-clear-filter"
                >
                  Clear Filter
                </button>
              </div>
              <p className="date-range-note">
                <strong>Note:</strong> Statement history available for the last 5 years.
              </p>
            </div>

            {/* Available Statements Section */}
            <div className="statements-list-section">
              <h2>Available Statements</h2>

              {loading ? (
                <div className="loading-state">
                  <p>Loading statements...</p>
                </div>
              ) : statements.length === 0 ? (
                <div className="empty-state">
                  <p>No statements available for this account in the selected date range.</p>
                  <p>Select a date range above and click Generate Statements.</p>
                </div>
              ) : (
                <div className="statements-grid">
                  {statements.map((statement) => (
                    <div key={statement.statementId} className="statement-card">
                      <div className="statement-header">
                        <h3>
                          {statement.statementMonth} {statement.statementYear}
                        </h3>
                        <span className="statement-id">{statement.statementId}</span>
                      </div>

                      <div className="statement-details">
                        <div className="detail-row">
                          <span className="detail-label">Account:</span>
                          <span className="detail-value">{statement.accountNumber}</span>
                        </div>
                        <div className="detail-row">
                          <span className="detail-label">Period:</span>
                          <span className="detail-value">
                            {formatDate(statement.periodStartDate)} to{' '}
                            {formatDate(statement.periodEndDate)}
                          </span>
                        </div>
                        <div className="detail-row">
                          <span className="detail-label">Generated:</span>
                          <span className="detail-value">
                            {new Date(statement.generatedDate).toLocaleDateString()}
                          </span>
                        </div>
                      </div>

                      {statement.isGeneratedResult && (
                        <button
                          onClick={() => handleDownloadStatement(statement)}
                          disabled={downloadingPdf || generatingStatements || loading}
                          className="btn btn-download"
                        >
                          {downloadingPdf ? 'Downloading...' : 'Download PDF'}
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  );
};

export default StatementsPage;
