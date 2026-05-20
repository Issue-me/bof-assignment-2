import api from './api';

/**
 * Service for teller biller management APIs.
 */
const billerService = {
  getActiveBillers: () => api.get('/billers'),

  getAllBillers: () => api.get('/billers/management'),

  getBillerById: (id) => api.get(`/billers/${id}`),

  createBiller: (payload) => api.post('/billers', payload),

  updateBiller: (id, payload) => api.put(`/billers/${id}`, payload),

  deactivateBiller: (id) => api.patch(`/billers/${id}/deactivate`),

  activateBiller: (id) => api.patch(`/billers/${id}/activate`),
};

export default billerService;
