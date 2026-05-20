import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import authService from '../services/authService';

const AuthContext = createContext(null);

/**
 * AuthProvider — wraps the app and provides auth state + actions globally.
 * Usage:
 *   const { user, login, logout, isAuthenticated } = useAuth();
 */
export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  // On mount, rehydrate user from localStorage
  useEffect(() => {
    const storedUser = authService.getCurrentUser();
    if (storedUser && authService.isAuthenticated()) {
      setUser(storedUser);
    }
    setLoading(false);
  }, []);

  const login = useCallback(async (email, password) => {
    const data = await authService.login(email, password);
    setUser({
      customerId: data.customerId,
      firstName: data.firstName,
      lastName: data.lastName,
      email: data.email,
      role: data.role,
    });
    return data;
  }, []);

  const register = useCallback(async (formData) => {
    const data = await authService.register(formData);
    setUser({
      customerId: data.customerId,
      firstName: data.firstName,
      lastName: data.lastName,
      email: data.email,
      role: data.role,
    });
    return data;
  }, []);

  const logout = useCallback(async () => {
    await authService.logout();
    setUser(null);
  }, []);

  const value = {
    user,
    role: user?.role || null,
    loading,
    login,
    register,
    logout,
    isAuthenticated: !!user,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

/**
 * Hook to consume auth context.
 */
export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

export default AuthContext;
