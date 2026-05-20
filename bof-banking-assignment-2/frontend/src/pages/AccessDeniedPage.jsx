import React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

const AccessDeniedPage = () => {
  const location = useLocation();
  const navigate = useNavigate();

  return (
    <div className="dashboard-wrapper">
      <main className="dashboard-main" style={{ marginTop: 24 }}>
        <div className="card" style={{ maxWidth: 620 }}>
          <h2 style={{ color: '#c0392b', marginTop: 0 }}>Access Denied</h2>
          <p>
            You do not have permission to access this feature.
          </p>
          {location.state?.from && (
            <p style={{ color: '#666' }}>Requested path: {location.state.from}</p>
          )}
          <button className="btn btn-primary" onClick={() => navigate('/dashboard')}>
            Back to Dashboard
          </button>
        </div>
      </main>
    </div>
  );
};

export default AccessDeniedPage;
