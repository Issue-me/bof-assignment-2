import React, { useEffect, useState } from 'react';
import customerService from '../services/customerService';
import { useAuth } from '../context/AuthContext';
import { isAdmin } from '../utils/roleUtils';
import ConfirmDialog from '../components/ConfirmDialog';
import './ManageAccountHoldersHub.css';

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE_REGEX = /^\+?[0-9 ()-]{7,20}$/;
const PASSPORT_REGEX = /^[A-Za-z0-9/-]{4,20}$/;

const trimValue = (value) => (typeof value === 'string' ? value.trim() : value);

const isPastDate = (value) => {
  if (!value) return false;
  const candidate = new Date(value);
  if (Number.isNaN(candidate.getTime())) return false;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return candidate < today;
};

const validateCustomerForm = (formData, options = {}) => {
  const { requirePassword = false } = options;
  const errors = {};

  if (!trimValue(formData.firstName)) errors.firstName = 'First name is required';
  if (!trimValue(formData.lastName)) errors.lastName = 'Last name is required';

  const email = trimValue(formData.email);
  if (!email) errors.email = 'Email is required';
  else if (!EMAIL_REGEX.test(email)) errors.email = 'Enter a valid email address';

  const phoneNumber = trimValue(formData.phoneNumber);
  if (!phoneNumber) errors.phoneNumber = 'Phone number is required';
  else if (!PHONE_REGEX.test(phoneNumber)) errors.phoneNumber = 'Enter a valid phone number';

  const passportNumber = trimValue(formData.nationalId);
  if (!passportNumber) errors.nationalId = 'Passport number is required';
  else if (!PASSPORT_REGEX.test(passportNumber)) errors.nationalId = 'Passport format is invalid';

  if (!formData.dateOfBirth) errors.dateOfBirth = 'Date of birth is required';
  else if (!isPastDate(formData.dateOfBirth)) errors.dateOfBirth = 'Date of birth must be a valid past date';

  if (requirePassword && !trimValue(formData.password)) {
    errors.password = 'Initial password is required';
  }

  return errors;
};

const toCustomerPayload = (formData, options = {}) => {
  const { includePassword = false } = options;
  const tin = trimValue(formData.tinNumber);
  const payload = {
    firstName: trimValue(formData.firstName),
    lastName: trimValue(formData.lastName),
    email: trimValue(formData.email),
    phoneNumber: trimValue(formData.phoneNumber),
    tinNumber: tin ? tin : null,
    nationalId: trimValue(formData.nationalId),
    dateOfBirth: formData.dateOfBirth,
    address: trimValue(formData.address),
    isResident: Boolean(formData.isResident),
  };

  if (includePassword) {
    payload.password = trimValue(formData.password);
  }

  return payload;
};

/**
 * Admin/Teller page for managing customer profiles (Account Holders).
 * Provides CRUD operations for customer management.
 */
const ManageAccountHoldersHubPage = () => {
  const { role } = useAuth();
  const [customers, setCustomers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [selectedCustomer, setSelectedCustomer] = useState(null);
  const [showConfirm, setShowConfirm] = useState(false);
  const [customerToToggle, setCustomerToToggle] = useState(null);

  const fetchCustomers = async () => {
    setLoading(true);
    setError('');
    try {
      const data = await customerService.getAllCustomers();
      setCustomers(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to load customers');
      console.error('Error fetching customers:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchCustomers();
  }, []);

  const handleToggleActive = (customer) => {
    if (!isAdmin(role)) {
      setError('Only ADMIN can activate/deactivate customers');
      return;
    }

    setCustomerToToggle(customer);
    setShowConfirm(true);
  };

  const confirmToggleActive = async () => {
    if (!customerToToggle) return;

    setShowConfirm(false);

    try {
      if (customerToToggle.active) {
        await customerService.deactivateCustomer(customerToToggle.id);
      } else {
        await customerService.activateCustomer(customerToToggle.id);
      }
      await fetchCustomers();
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to update customer status');
    } finally {
      setCustomerToToggle(null);
    }
  };

  const handleViewDetails = (customer) => {
    setSelectedCustomer(customer);
  };

  const handleCloseDetails = () => {
    setSelectedCustomer(null);
  };

  return (
    <div className="manage-holders-hub-page">
      <div className="page-header">
        <h1>Manage Account Holders</h1>
        <p>View and manage customer profiles and linked accounts.</p>
      </div>

      <div className="card">
        <div className="admin-toolbar">
          <h3>Customer Profiles</h3>
          <button
            className="btn btn-primary"
            onClick={() => setShowCreateModal(true)}
          >
            + Create New Customer
          </button>
        </div>

        {loading ? (
          <p className="admin-state-text">Loading customers...</p>
        ) : error ? (
          <div className="alert alert-error">{error}</div>
        ) : customers.length === 0 ? (
          <p className="admin-state-text muted">No customers found.</p>
        ) : (
          <div className="admin-table-wrap">
            <table className="accounts-table admin-customers-table">
              <thead>
                <tr>
                  <th>Customer ID</th>
                  <th>Name</th>
                  <th>Email</th>
                  <th>Phone</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {customers.map((customer) => (
                  <tr key={customer.id}>
                    <td>{customer.customerId}</td>
                    <td>{customer.firstName} {customer.lastName}</td>
                    <td>{customer.email}</td>
                    <td>{customer.phoneNumber || 'N/A'}</td>
                    <td>
                      <span className={`status-chip ${customer.active ? 'active' : 'inactive'}`}>
                        {customer.active ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td>
                      <div className="admin-actions-row">
                        <button
                          className="btn btn-secondary"
                          onClick={() => handleViewDetails(customer)}
                        >
                          View Details
                        </button>
                        {isAdmin(role) && (
                          <button
                            className="btn btn-secondary"
                            onClick={() => handleToggleActive(customer)}
                          >
                            {customer.active ? 'Deactivate' : 'Activate'}
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Create Customer Modal */}
      {showCreateModal && (
        <CreateCustomerModal
          onClose={() => setShowCreateModal(false)}
          onSuccess={() => {
            setShowCreateModal(false);
            fetchCustomers();
          }}
          setError={setError}
        />
      )}

      {/* Customer Details Modal */}
      {selectedCustomer && (
        <CustomerDetailsModal
          customer={selectedCustomer}
          onClose={handleCloseDetails}
          onUpdate={fetchCustomers}
          setError={setError}
        />
      )}

      {showConfirm && customerToToggle && (
        <ConfirmDialog
          title={`${customerToToggle.active ? 'Deactivate' : 'Activate'} Customer`}
          message={`Are you sure you want to ${customerToToggle.active ? 'deactivate' : 'activate'} ${customerToToggle.firstName} ${customerToToggle.lastName}?`}
          confirmText={customerToToggle.active ? 'Deactivate' : 'Activate'}
          cancelText="Cancel"
          confirmVariant="success"
          cancelVariant="cancel-danger"
          variant="warning"
          onConfirm={confirmToggleActive}
          onCancel={() => {
            setShowConfirm(false);
            setCustomerToToggle(null);
          }}
        />
      )}
    </div>
  );
};

/**
 * Modal for creating a new customer
 */
const CreateCustomerModal = ({ onClose, onSuccess, setError }) => {
  const [formData, setFormData] = useState({
    firstName: '',
    lastName: '',
    email: '',
    phoneNumber: '',
    password: '',
    tinNumber: '',
    nationalId: '',
    dateOfBirth: '',
    address: '',
    isResident: true,
  });
  const [formErrors, setFormErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const todayIso = new Date().toISOString().split('T')[0];

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value
    }));
    setFormErrors(prev => {
      if (!prev[name]) return prev;
      const next = { ...prev };
      delete next[name];
      return next;
    });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    const validationErrors = validateCustomerForm(formData, { requirePassword: true });
    if (Object.keys(validationErrors).length > 0) {
      setFormErrors(validationErrors);
      return;
    }

    setSubmitting(true);
    setError('');
    try {
      await customerService.createCustomer(toCustomerPayload(formData, { includePassword: true }));
      onSuccess();
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to create customer');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()} style={{ maxWidth: 600 }}>
        <h2>Create New Customer</h2>
        <form onSubmit={handleSubmit}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            <div>
              <label>First Name *</label>
              <input
                type="text"
                name="firstName"
                value={formData.firstName}
                onChange={handleChange}
                required
                className="input-field"
              />
              {formErrors.firstName && <p className="field-error">{formErrors.firstName}</p>}
            </div>
            <div>
              <label>Last Name *</label>
              <input
                type="text"
                name="lastName"
                value={formData.lastName}
                onChange={handleChange}
                required
                className="input-field"
              />
              {formErrors.lastName && <p className="field-error">{formErrors.lastName}</p>}
            </div>
          </div>

          <div style={{ marginTop: 12 }}>
            <label>Email *</label>
            <input
              type="email"
              name="email"
              value={formData.email}
              onChange={handleChange}
              required
              className="input-field"
            />
            {formErrors.email && <p className="field-error">{formErrors.email}</p>}
          </div>

          <div style={{ marginTop: 12 }}>
            <label>Phone Number *</label>
            <input
              type="tel"
              name="phoneNumber"
              value={formData.phoneNumber}
              onChange={handleChange}
              required
              className="input-field"
              placeholder="+679 999 0000"
            />
            {formErrors.phoneNumber && <p className="field-error">{formErrors.phoneNumber}</p>}
          </div>

          <div style={{ marginTop: 12 }}>
            <label>Initial Password *</label>
            <input
              type="password"
              name="password"
              value={formData.password}
              onChange={handleChange}
              required
              className="input-field"
              placeholder="Minimum 8 characters"
            />
            {formErrors.password && <p className="field-error">{formErrors.password}</p>}
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginTop: 12 }}>
            <div>
              <label>TIN Number</label>
              <input
                type="text"
                name="tinNumber"
                value={formData.tinNumber}
                onChange={handleChange}
                className="input-field"
              />
            </div>
            <div>
              <label>Passport Number *</label>
              <input
                type="text"
                name="nationalId"
                value={formData.nationalId}
                onChange={handleChange}
                className="input-field"
                placeholder="Passport number"
                required
              />
              {formErrors.nationalId && <p className="field-error">{formErrors.nationalId}</p>}
            </div>
          </div>

          <div style={{ marginTop: 12 }}>
            <label>Date of Birth *</label>
            <input
              type="date"
              name="dateOfBirth"
              value={formData.dateOfBirth}
              onChange={handleChange}
              className="input-field"
              max={todayIso}
              required
            />
            {formErrors.dateOfBirth && <p className="field-error">{formErrors.dateOfBirth}</p>}
          </div>

          <div style={{ marginTop: 12 }}>
            <label>Address</label>
            <textarea
              name="address"
              value={formData.address}
              onChange={handleChange}
              className="input-field"
              rows={3}
            />
          </div>

          <div style={{ marginTop: 12 }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <input
                type="checkbox"
                name="isResident"
                checked={formData.isResident}
                onChange={handleChange}
              />
              Fiji Resident
            </label>
          </div>

          <div style={{ display: 'flex', gap: 12, marginTop: 24 }}>
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? 'Creating...' : 'Create Customer'}
            </button>
            <button type="button" className="btn btn-secondary" onClick={onClose}>
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

/**
 * Modal to view and edit customer details
 */
const CustomerDetailsModal = ({ customer, onClose, onUpdate, setError }) => {
  const [details, setDetails] = useState(null);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [formData, setFormData] = useState({});
  const [formErrors, setFormErrors] = useState({});
  const todayIso = new Date().toISOString().split('T')[0];

  useEffect(() => {
    const fetchDetails = async () => {
      try {
        const data = await customerService.getCustomerDetail(customer.id);
        setDetails(data);
        setFormData({
          firstName: data.firstName || '',
          lastName: data.lastName || '',
          email: data.email || '',
          phoneNumber: data.phoneNumber || '',
          tinNumber: data.tinNumber || '',
          nationalId: data.nationalId || '',
          dateOfBirth: data.dateOfBirth || '',
          address: data.address || '',
          isResident: data.resident,
        });
      } catch (err) {
        setError(err.response?.data?.message || 'Unable to load customer details');
      } finally {
        setLoading(false);
      }
    };
    fetchDetails();
  }, [customer.id, setError]);

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value
    }));
    setFormErrors(prev => {
      if (!prev[name]) return prev;
      const next = { ...prev };
      delete next[name];
      return next;
    });
  };

  const handleUpdate = async () => {
    const validationErrors = validateCustomerForm(formData);
    if (Object.keys(validationErrors).length > 0) {
      setFormErrors(validationErrors);
      return;
    }

    try {
      await customerService.updateCustomer(customer.id, toCustomerPayload(formData));
      setEditing(false);
      setFormErrors({});
      onUpdate();
      const updated = await customerService.getCustomerDetail(customer.id);
      setDetails(updated);
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to update customer');
    }
  };

  if (loading) {
    return (
      <div className="modal-overlay" onClick={onClose}>
        <div className="modal-content" onClick={(e) => e.stopPropagation()}>
          <p>Loading customer details...</p>
        </div>
      </div>
    );
  }

  if (!details) return null;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()} style={{ maxWidth: 700, maxHeight: '90vh', overflowY: 'auto' }}>
        <h2>Customer Profile Details</h2>
        
        {!editing ? (
          <div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 24 }}>
              <div>
                <strong>Customer ID:</strong>
                <p>{details.customerId}</p>
              </div>
              <div>
                <strong>Status:</strong>
                <p>
                  <span style={{
                    padding: '4px 8px',
                    borderRadius: '4px',
                    backgroundColor: details.active ? '#27ae60' : '#c0392b',
                    color: 'white',
                    fontSize: '0.85em',
                  }}>
                    {details.active ? 'Active' : 'Inactive'}
                  </span>
                </p>
              </div>
              <div>
                <strong>First Name:</strong>
                <p>{details.firstName}</p>
              </div>
              <div>
                <strong>Last Name:</strong>
                <p>{details.lastName}</p>
              </div>
              <div>
                <strong>Email:</strong>
                <p>{details.email}</p>
              </div>
              <div>
                <strong>Phone:</strong>
                <p>{details.phoneNumber || 'N/A'}</p>
              </div>
              <div>
                <strong>TIN Number:</strong>
                <p>{details.tinNumber || 'N/A'}</p>
              </div>
              <div>
                <strong>Passport Number:</strong>
                <p>{details.nationalId || 'N/A'}</p>
              </div>
              <div>
                <strong>Date of Birth:</strong>
                <p>{details.dateOfBirth || 'N/A'}</p>
              </div>
              <div>
                <strong>Resident:</strong>
                <p>{details.resident ? 'Yes' : 'No'}</p>
              </div>
            </div>

            {details.address && (
              <div style={{ marginBottom: 16 }}>
                <strong>Address:</strong>
                <p>{details.address}</p>
              </div>
            )}

            <hr style={{ margin: '24px 0' }} />

            <h3>Linked Bank Accounts ({details.totalAccounts})</h3>
            {details.accounts && details.accounts.length > 0 ? (
              <table className="accounts-table" style={{ width: '100%', marginTop: 12 }}>
                <thead>
                  <tr>
                    <th>Account Number</th>
                    <th>Type</th>
                    <th>Balance</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {details.accounts.map((account) => (
                    <tr key={account.id}>
                      <td>{account.accountNumber}</td>
                      <td>{account.accountType}</td>
                      <td>FJ$ {Number(account.balance).toFixed(2)}</td>
                      <td>{account.active ? 'Active' : 'Closed'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <p>No accounts linked to this customer.</p>
            )}

            <div style={{ display: 'flex', gap: 12, marginTop: 24 }}>
              <button className="btn btn-primary" onClick={() => setEditing(true)}>
                Edit Profile
              </button>
              <button className="btn btn-secondary" onClick={onClose}>
                Close
              </button>
            </div>
          </div>
        ) : (
          <div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
              <div>
                <label>First Name *</label>
                <input
                  type="text"
                  name="firstName"
                  value={formData.firstName}
                  onChange={handleChange}
                  className="input-field"
                  required
                />
                {formErrors.firstName && <p className="field-error">{formErrors.firstName}</p>}
              </div>
              <div>
                <label>Last Name *</label>
                <input
                  type="text"
                  name="lastName"
                  value={formData.lastName}
                  onChange={handleChange}
                  className="input-field"
                  required
                />
                {formErrors.lastName && <p className="field-error">{formErrors.lastName}</p>}
              </div>
            </div>

            <div style={{ marginTop: 12 }}>
              <label>Email *</label>
              <input
                type="email"
                name="email"
                value={formData.email}
                onChange={handleChange}
                className="input-field"
                required
              />
              {formErrors.email && <p className="field-error">{formErrors.email}</p>}
            </div>

            <div style={{ marginTop: 12 }}>
              <label>Phone Number *</label>
              <input
                type="tel"
                name="phoneNumber"
                value={formData.phoneNumber}
                onChange={handleChange}
                className="input-field"
                required
              />
              {formErrors.phoneNumber && <p className="field-error">{formErrors.phoneNumber}</p>}
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginTop: 12 }}>
              <div>
                <label>TIN Number</label>
                <input
                  type="text"
                  name="tinNumber"
                  value={formData.tinNumber}
                  onChange={handleChange}
                  className="input-field"
                />
                {!trimValue(details.tinNumber) && formData.isResident && !trimValue(formData.tinNumber) && (
                  <p className="field-error" style={{ color: '#b45309', marginTop: 6 }}>
                    No TIN registered. Resident customers are currently charged NRWHT (10%) on interest.
                  </p>
                )}
                {!trimValue(details.tinNumber) && trimValue(formData.tinNumber) && formData.isResident && (
                  <p style={{ color: '#1d4ed8', marginTop: 6, fontSize: 12 }}>
                    Saving this TIN will trigger automatic NRWHT refund processing for this customer.
                  </p>
                )}
              </div>
              <div>
                <label>Passport Number *</label>
                <input
                  type="text"
                  name="nationalId"
                  value={formData.nationalId}
                  onChange={handleChange}
                  className="input-field"
                  required
                />
                {formErrors.nationalId && <p className="field-error">{formErrors.nationalId}</p>}
              </div>
            </div>

            <div style={{ marginTop: 12 }}>
              <label>Date of Birth *</label>
              <input
                type="date"
                name="dateOfBirth"
                value={formData.dateOfBirth}
                onChange={handleChange}
                className="input-field"
                max={todayIso}
                required
              />
              {formErrors.dateOfBirth && <p className="field-error">{formErrors.dateOfBirth}</p>}
            </div>

            <div style={{ marginTop: 12 }}>
              <label>Address</label>
              <textarea
                name="address"
                value={formData.address}
                onChange={handleChange}
                className="input-field"
                rows={3}
              />
            </div>

            <div style={{ marginTop: 12 }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <input
                  type="checkbox"
                  name="isResident"
                  checked={formData.isResident}
                  onChange={handleChange}
                />
                Fiji Resident
              </label>
            </div>

            <div style={{ display: 'flex', gap: 12, marginTop: 24 }}>
              <button className="btn btn-primary" onClick={handleUpdate}>
                Save Changes
              </button>
              <button className="btn btn-secondary" onClick={() => setEditing(false)}>
                Cancel
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default ManageAccountHoldersHubPage;
