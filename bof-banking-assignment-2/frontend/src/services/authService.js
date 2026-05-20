import api from './api';

/**
 * Authentication service — wraps all /api/auth endpoints.
 */
const authService = {

  /**
   * Login with email and password.
   * Stores JWT token and user info in localStorage.
   */
  async login(email, password) {
    const response = await api.post('/auth/login', { email, password });
    const { token, ...user } = response.data;

    localStorage.setItem('bof_token', token);
    localStorage.setItem('bof_user', JSON.stringify(user));

    return response.data;
  },

  /**
   * Register a new customer account.
   */
  async register(data) {
    const response = await api.post('/auth/register', data);
    const { token, ...user } = response.data;

    localStorage.setItem('bof_token', token);
    localStorage.setItem('bof_user', JSON.stringify(user));

    return response.data;
  },

  /**
   * Logout — clear local storage and notify backend.
   */
  async logout() {
    try {
      await api.post('/auth/logout');
    } catch (e) {
      // Best effort
    } finally {
      localStorage.removeItem('bof_token');
      localStorage.removeItem('bof_user');
    }
  },

  /**
   * Fetch current user profile from the backend.
   */
  async getProfile() {
    const response = await api.get('/auth/profile');
    return response.data;
  },

  /**
   * Get cached user from localStorage.
   */
  getCurrentUser() {
    const user = localStorage.getItem('bof_user');
    return user ? JSON.parse(user) : null;
  },

  /**
   * Check if there is a stored JWT token.
   */
  isAuthenticated() {
    return !!localStorage.getItem('bof_token');
  },
};

export default authService;
