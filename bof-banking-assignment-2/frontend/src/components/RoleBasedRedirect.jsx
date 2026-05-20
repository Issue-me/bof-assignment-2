import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { normalizeRole } from '../utils/roleUtils';

/**
 * RoleBasedRedirect - Redirects users to their role-appropriate landing page
 */
const RoleBasedRedirect = () => {
  const { role } = useAuth();
  const normalizedRole = normalizeRole(role);
  
  if (normalizedRole === 'ADMIN') {
    return <Navigate to="/manage-accounts" replace />;
  }
  
  return <Navigate to="/dashboard" replace />;
};

export default RoleBasedRedirect;
