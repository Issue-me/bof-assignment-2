import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { normalizeRole } from '../utils/roleUtils';

/**
 * ProtectedRoute — redirects unauthenticated users to /login.
 * Preserves the original destination so the user is redirected back after login.
 */
const ProtectedRoute = ({ children, allowedRoles }) => {
  const { isAuthenticated, user, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div className="loading-screen">
        <div className="spinner"></div>
        <p>Loading...</p>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  const currentRole = normalizeRole(
    user?.role || user?.roles?.[0] || user?.authorities?.[0]
  );

  if (
    allowedRoles &&
    user &&
    !allowedRoles.map(normalizeRole).includes(currentRole)
  ) {
    return (
      <Navigate
        to="/access-denied"
        state={{ from: location.pathname }}
        replace
      />
    );
  }

  return children;
};

export default ProtectedRoute;
