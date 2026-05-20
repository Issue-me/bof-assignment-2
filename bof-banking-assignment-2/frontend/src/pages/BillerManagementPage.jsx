import React, { useCallback, useEffect, useMemo, useState } from 'react';
import billerService from '../services/billerService';
import ConfirmDialog from '../components/ConfirmDialog';
import './BillerManagementPage.css';

const emptyForm = {
  billerName: '',
  billerCode: '',
  category: '',
  settlementAccountNumber: '',
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

function BillerManagementPage() {
  const [billers, setBillers] = useState([]);
  const [selectedBillerId, setSelectedBillerId] = useState(null);
  const [editingId, setEditingId] = useState(null);

  const [form, setForm] = useState(emptyForm);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [showStatusConfirm, setShowStatusConfirm] = useState(false);
  const [billerToToggle, setBillerToToggle] = useState(null);

  const loadBillers = useCallback(async (silent = false) => {
    if (!silent) setLoading(true);
    setError('');
    try {
      const response = await billerService.getAllBillers();
      const rows = (response.data || []).map((b) => ({
        ...b,
        active: b.active ?? b.isActive ?? false,
      }));
      setBillers(rows);

      if (rows.length > 0 && !selectedBillerId) {
        setSelectedBillerId(rows[0].id);
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to load billers.');
      if (!silent) setBillers([]);
    } finally {
      if (!silent) setLoading(false);
    }
  }, [selectedBillerId]);

  useEffect(() => {
    loadBillers();
  }, [loadBillers]);

  const selectedBiller = useMemo(
    () => billers.find((biller) => biller.id === selectedBillerId) || null,
    [billers, selectedBillerId]
  );

  const filteredBillers = useMemo(() => {
    const normalizedSearch = search.trim().toLowerCase();

    return billers.filter((biller) => {
      const matchesStatus =
        statusFilter === 'ALL' ||
        (statusFilter === 'ACTIVE' && biller.active) ||
        (statusFilter === 'INACTIVE' && !biller.active);

      if (!matchesStatus) {
        return false;
      }

      if (!normalizedSearch) {
        return true;
      }

      return (
        biller.billerName?.toLowerCase().includes(normalizedSearch) ||
        biller.billerCode?.toLowerCase().includes(normalizedSearch) ||
        biller.category?.toLowerCase().includes(normalizedSearch) ||
        biller.settlementAccountNumber?.toLowerCase().includes(normalizedSearch)
      );
    });
  }, [billers, search, statusFilter]);

  const onFormChange = (event) => {
    const { name, value } = event.target;
    setForm((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  const resetForm = () => {
    setForm(emptyForm);
    setEditingId(null);
  };

  const startEdit = (biller) => {
    setError('');
    setSuccess('');
    setEditingId(biller.id);
    setForm({
      billerName: biller.billerName || '',
      billerCode: biller.billerCode || '',
      category: biller.category || '',
      settlementAccountNumber: biller.settlementAccountNumber || '',
    });
    setSelectedBillerId(biller.id);
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setSaving(true);
    setError('');
    setSuccess('');

    const payload = {
      billerName: form.billerName.trim(),
      billerCode: form.billerCode.trim(),
      category: form.category.trim(),
      settlementAccountNumber: form.settlementAccountNumber.trim(),
    };

    try {
      if (editingId) {
        await billerService.updateBiller(editingId, payload);
        setSuccess('Biller updated successfully.');
      } else {
        await billerService.createBiller(payload);
        setSuccess('Biller created successfully.');
      }

      resetForm();
      await loadBillers();
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to save biller details.');
    } finally {
      setSaving(false);
    }
  };

  const handleToggleStatus = (biller) => {
    setBillerToToggle(biller);
    setShowStatusConfirm(true);
  };

  const confirmToggleStatus = async () => {
    if (!billerToToggle) return;

    setShowStatusConfirm(false);
    setError('');
    setSuccess('');

    const isCurrentlyActive = billerToToggle.active;

    try {
      const res = isCurrentlyActive
        ? await billerService.deactivateBiller(billerToToggle.id)
        : await billerService.activateBiller(billerToToggle.id);

      // Normalize the returned biller (backend may serialize as 'active' or 'isActive')
      const returned = res?.data || {};
      const updatedBiller = {
        ...billerToToggle,
        ...returned,
        active: returned.active ?? returned.isActive ?? !isCurrentlyActive,
      };

      // Immediately reflect the change in local state — no loading flicker
      setBillers((prev) =>
        prev.map((b) => (b.id === updatedBiller.id ? updatedBiller : b))
      );

      setSuccess(isCurrentlyActive ? 'Biller deactivated successfully.' : 'Biller reactivated successfully.');

      // Silent background refresh to sync with server
      loadBillers(true);
    } catch (err) {
      setError(
        err.response?.data?.message ||
        (isCurrentlyActive ? 'Unable to deactivate biller.' : 'Unable to reactivate biller.')
      );
    } finally {
      setBillerToToggle(null);
    }
  };

  return (
    <div className="biller-management-page">
      <header className="page-header">
        <h1>Bill Management</h1>
        <p>Teller console for biller administration and detail management.</p>
      </header>

      {error && <div className="alert alert-error">{error}</div>}
      {success && <div className="alert alert-success">{success}</div>}

      <section className="biller-grid">
        <article className="card biller-form-card">
          <h2>{editingId ? 'Update Biller' : 'Add New Biller'}</h2>

          <form onSubmit={handleSubmit} className="biller-form">
            <label>
              Biller Name
              <input
                name="billerName"
                value={form.billerName}
                onChange={onFormChange}
                required
                maxLength={100}
              />
            </label>

            <label>
              Biller Code
              <input
                name="billerCode"
                value={form.billerCode}
                onChange={onFormChange}
                required
                maxLength={50}
              />
            </label>

            <label>
              Category
              <input
                name="category"
                value={form.category}
                onChange={onFormChange}
                required
                maxLength={50}
              />
            </label>

            <label>
              Settlement Account Number
              <input
                name="settlementAccountNumber"
                value={form.settlementAccountNumber}
                onChange={onFormChange}
                required
                maxLength={20}
              />
            </label>

            <div className="form-actions">
              <button type="submit" className="btn btn-primary" disabled={saving}>
                {saving ? 'Saving...' : editingId ? 'Update Biller' : 'Create Biller'}
              </button>
              {editingId && (
                <button type="button" className="btn btn-secondary" onClick={resetForm}>
                  Cancel Edit
                </button>
              )}
            </div>
          </form>
        </article>

        <article className="card biller-details-card">
          <h2>Biller Details</h2>
          {!selectedBiller ? (
            <p className="muted">Select a biller from the list to view details.</p>
          ) : (
            <dl className="details-grid">
              <div>
                <dt>Name</dt>
                <dd>{selectedBiller.billerName}</dd>
              </div>
              <div>
                <dt>Code</dt>
                <dd>{selectedBiller.billerCode}</dd>
              </div>
              <div>
                <dt>Category</dt>
                <dd>{selectedBiller.category || '-'}</dd>
              </div>
              <div>
                <dt>Settlement Account</dt>
                <dd>{selectedBiller.settlementAccountNumber || '-'}</dd>
              </div>
              <div>
                <dt>Status</dt>
                <dd>
                  <span className={`status-badge ${selectedBiller.active ? 'active' : 'inactive'}`}>
                    {selectedBiller.active ? 'ACTIVE' : 'INACTIVE'}
                  </span>
                </dd>
              </div>
              <div>
                <dt>Created</dt>
                <dd>{formatDateTime(selectedBiller.createdAt)}</dd>
              </div>
              <div>
                <dt>Last Updated</dt>
                <dd>{formatDateTime(selectedBiller.updatedAt)}</dd>
              </div>
            </dl>
          )}
        </article>
      </section>

      <section className="card biller-table-card">
        <div className="table-toolbar">
          <h2>Registered Billers</h2>
          <div className="toolbar-controls">
            <input
              type="text"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Search by name, code, category, or settlement account"
            />
            <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
              <option value="ALL">All statuses</option>
              <option value="ACTIVE">Active</option>
              <option value="INACTIVE">Inactive</option>
            </select>
          </div>
        </div>

        {loading ? (
          <div className="loading">Loading billers...</div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Code</th>
                  <th>Category</th>
                  <th>Settlement Account</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredBillers.length === 0 ? (
                  <tr>
                    <td colSpan="6" className="muted center">No billers found.</td>
                  </tr>
                ) : (
                  filteredBillers.map((biller) => (
                    <tr
                      key={biller.id}
                      className={selectedBillerId === biller.id ? 'selected' : ''}
                      onClick={() => setSelectedBillerId(biller.id)}
                    >
                      <td>{biller.billerName}</td>
                      <td>{biller.billerCode}</td>
                      <td>{biller.category || '-'}</td>
                      <td>{biller.settlementAccountNumber || '-'}</td>
                      <td>
                        <span className={`status-badge ${biller.active ? 'active' : 'inactive'}`}>
                          {biller.active ? 'ACTIVE' : 'INACTIVE'}
                        </span>
                      </td>
                      <td>
                        <div className="row-actions">
                          <button
                            type="button"
                            className="btn btn-secondary"
                            onClick={(event) => {
                              event.stopPropagation();
                              startEdit(biller);
                            }}
                          >
                            Edit
                          </button>
                          {biller.active ? (
                            <button
                              type="button"
                              className="btn btn-danger"
                              onClick={(event) => {
                                event.stopPropagation();
                                handleToggleStatus(biller);
                              }}
                            >
                              Deactivate
                            </button>
                          ) : (
                            <button
                              type="button"
                              className="btn btn-success"
                              onClick={(event) => {
                                event.stopPropagation();
                                handleToggleStatus(biller);
                              }}
                            >
                              Reactivate
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {showStatusConfirm && billerToToggle && (
        <ConfirmDialog
          title={`${billerToToggle.active ? 'Deactivate' : 'Reactivate'} Biller`}
          message={`Are you sure you want to ${billerToToggle.active ? 'deactivate' : 'reactivate'} biller ${billerToToggle.billerName}?`}
          confirmText={billerToToggle.active ? 'Deactivate' : 'Reactivate'}
          cancelText="Cancel"
          confirmVariant="success"
          cancelVariant="cancel-danger"
          variant="warning"
          onConfirm={confirmToggleStatus}
          onCancel={() => {
            setShowStatusConfirm(false);
            setBillerToToggle(null);
          }}
        />
      )}
    </div>
  );
}

export default BillerManagementPage;
