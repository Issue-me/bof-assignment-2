import React, { useEffect, useState } from 'react';
import userService from '../services/userService';
import ConfirmDialog from '../components/ConfirmDialog';
import './UserManagementPage.css';

const UserManagementPage = () => {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showConfirm, setShowConfirm] = useState(false);
  const [userToToggle, setUserToToggle] = useState(null);

  const fetchUsers = async () => {
    setLoading(true);
    setError('');
    try {
      const data = await userService.getAllUsers();
      setUsers(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to load users');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers();
  }, []);

  const toggleActive = (user) => {
    setUserToToggle(user);
    setShowConfirm(true);
  };

  const confirmToggleActive = async () => {
    if (!userToToggle) return;

    setShowConfirm(false);
    try {
      if (userToToggle.active) {
        await userService.deactivateUser(userToToggle.id);
      } else {
        await userService.activateUser(userToToggle.id);
      }
      await fetchUsers();
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to update user status');
    } finally {
      setUserToToggle(null);
    }
  };

  return (
    <div className="user-management-page">
      <header className="page-header">
        <h1>User Management</h1>
        <p>Admin-only user lifecycle actions.</p>
      </header>

      <section className="card">
        {loading ? (
          <div className="loading">Loading users...</div>
        ) : error ? (
          <div className="alert alert-error">{error}</div>
        ) : (
          <div className="table-wrap">
            <table className="monitor-table user-table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Email</th>
                  <th>Role</th>
                  <th>Status</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {users.length === 0 ? (
                  <tr>
                    <td colSpan="5" className="center muted">No users found.</td>
                  </tr>
                ) : (
                  users.map((user) => (
                    <tr key={user.id}>
                      <td>{user.firstName} {user.lastName}</td>
                      <td>{user.email}</td>
                      <td><span className="role-chip">{user.role}</span></td>
                      <td>
                        <span className={`status-chip ${user.active ? 'active' : 'inactive'}`}>
                          {user.active ? 'Active' : 'Inactive'}
                        </span>
                      </td>
                      <td>
                        <button
                          type="button"
                          className="btn btn-secondary"
                          onClick={() => toggleActive(user)}
                        >
                          {user.active ? 'Deactivate' : 'Activate'}
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {showConfirm && userToToggle && (
        <ConfirmDialog
          title={`${userToToggle.active ? 'Deactivate' : 'Activate'} User`}
          message={`Are you sure you want to ${userToToggle.active ? 'deactivate' : 'activate'} ${userToToggle.firstName} ${userToToggle.lastName}?`}
          confirmText={userToToggle.active ? 'Deactivate' : 'Activate'}
          cancelText="Cancel"
          confirmVariant="success"
          cancelVariant="cancel-danger"
          variant="warning"
          onConfirm={confirmToggleActive}
          onCancel={() => {
            setShowConfirm(false);
            setUserToToggle(null);
          }}
        />
      )}
    </div>
  );
};

export default UserManagementPage;
