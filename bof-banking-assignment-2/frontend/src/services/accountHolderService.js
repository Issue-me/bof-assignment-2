import api from './api';

/**
 * Account Holder Service - handles API calls for managing account holders
 */
const accountHolderService = {
  /**
   * Get all account holders for a specific account
   */
  getAccountHolders: async (accountId) => {
    const response = await api.get(`/accounts/${accountId}/holders`);
    return response.data;
  },

  /**
   * Add an account holder to an account
   */
  addAccountHolder: async (accountId, data) => {
    const response = await api.post(`/accounts/${accountId}/holders`, data);
    return response.data;
  },

  /**
   * Remove an account holder from an account
   */
  removeAccountHolder: async (accountId, userId) => {
    const response = await api.delete(`/accounts/${accountId}/holders/${userId}`);
    return response.data;
  },
};

export default accountHolderService;
