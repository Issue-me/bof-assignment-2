import api from './api';

/**
 * Account service — wraps /api/accounts endpoints.
 */
const accountService = {
  /**
   * Get all accounts for the currently authenticated user.
   */
  async getMyAccounts() {
    try {
      const response = await api.get('/accounts');
      return Array.isArray(response.data) ? response.data : [];
    } catch (error) {
      throw error;
    }
  },

  /**
   * Get a specific account by ID.
   */
  async getAccountById(accountId) {
    try {
      const response = await api.get(`/accounts/${accountId}`);
      return response.data;
    } catch (error) {
      throw error;
    }
  },

  /**
   * Lookup an account by account number.
   */
  async getAccountByNumber(accountNumber) {
    const response = await api.get(`/accounts/number/${encodeURIComponent(accountNumber)}`);
    return response.data;
  },

  /**
   * Create a new account for an existing customer.
   */
  async createAccount(payload) {
    const response = await api.post('/accounts', payload);
    return response.data;
  },
};

export default accountService;
