import api from './api';

/**
 * Service for customer profile management (Admin/Teller).
 */
const customerService = {
  /**
   * Get all customers
   */
  getAllCustomers: async () => {
    const response = await api.get('/customers');
    return response.data;
  },

  /**
   * Get detailed customer profile with linked accounts
   */
  getCustomerDetail: async (customerId) => {
    const response = await api.get(`/customers/${customerId}`);
    return response.data;
  },

  /**
   * Create a new customer profile
   */
  createCustomer: async (customerData) => {
    const response = await api.post('/customers', customerData);
    return response.data;
  },

  /**
   * Update customer profile information
   */
  updateCustomer: async (customerId, customerData) => {
    const response = await api.put(`/customers/${customerId}`, customerData);
    return response.data;
  },

  /**
   * Deactivate a customer
   */
  deactivateCustomer: async (customerId) => {
    const response = await api.post(`/customers/${customerId}/deactivate`);
    return response.data;
  },

  /**
   * Reactivate a customer
   */
  activateCustomer: async (customerId) => {
    const response = await api.post(`/customers/${customerId}/activate`);
    return response.data;
  },
};

export default customerService;
