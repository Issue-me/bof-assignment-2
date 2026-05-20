import api from './api';

/**
 * Service for interacting with bill payment APIs.
 */
const billPaymentService = {
  // Manual Bill Payments
  createBillPayment: (data) => {
    return api.post('/bill-payments', data);
  },

  getBillPayment: (id) => {
    return api.get(`/bill-payments/${id}`);
  },

  getMyBillPayments: () => {
    return api.get('/bill-payments');
  },

  getMonitoringBillPayments: ({
    page = 0,
    size = 15,
    sort = 'createdAt,desc',
    search,
    billerId,
    status,
    fromDate,
    toDate,
    minAmount,
    maxAmount,
  } = {}) => {
    const params = { page, size, sort };

    if (search) {
      params.search = search;
    }
    if (billerId) {
      params.billerId = billerId;
    }
    if (status) {
      params.status = status;
    }
    if (fromDate) {
      params.fromDate = fromDate;
    }
    if (toDate) {
      params.toDate = toDate;
    }
    if (minAmount !== undefined) {
      params.minAmount = minAmount;
    }
    if (maxAmount !== undefined) {
      params.maxAmount = maxAmount;
    }

    return api.get('/bill-payments/monitoring', { params });
  },

  cancelBillPayment: (id) => {
    return api.post(`/bill-payments/${id}/cancel`);
  },

  // Scheduled Bill Payments
  createScheduledBillPayment: (data) => {
    return api.post('/bill-payments/scheduled', data);
  },

  getScheduledBillPayment: (id) => {
    return api.get(`/bill-payments/scheduled/${id}`);
  },

  getScheduledBillPayments: () => {
    return api.get('/bill-payments/scheduled');
  },

  getScheduledBillPaymentsPaged: (page = 0, size = 10) => {
    return api.get(`/bill-payments/scheduled/page?page=${page}&size=${size}`);
  },

  updateScheduledBillPayment: (id, data) => {
    return api.put(`/bill-payments/scheduled/${id}`, data);
  },

  cancelScheduledBillPayment: (id) => {
    return api.patch(`/bill-payments/scheduled/${id}/cancel`);
  },

  pauseScheduledBillPayment: (id) => {
    return api.patch(`/bill-payments/scheduled/${id}/pause`);
  },

  resumeScheduledBillPayment: (id) => {
    return api.patch(`/bill-payments/scheduled/${id}/resume`);
  },

  setScheduledAutoPay: (id, enabled, payPendingBills = false) => {
    return api.patch(`/bill-payments/scheduled/${id}/auto-pay`, {
      enabled,
      payPendingBills,
    });
  },

  getScheduledInvoices: (id) => {
    return api.get(`/bill-payments/scheduled/${id}/invoices`);
  },

  payScheduledInvoice: (scheduledId, invoiceId, idempotencyKey) => {
    return api.post(
      `/bill-payments/scheduled/${scheduledId}/invoices/${invoiceId}/pay`,
      {},
      {
        headers: idempotencyKey
          ? { 'X-Idempotency-Key': idempotencyKey }
          : undefined,
      }
    );
  },

  getScheduledHistory: (id) => {
    return api.get(`/bill-payments/scheduled/${id}/history`);
  },

  triggerAutoPayNow: () => {
    return api.post('/bill-payments/scheduled/trigger');
  },

  seedDummyFeaInvoices: (data) => {
    return api.post('/bill-payments/invoices/dummy/fea', data);
  },
};

export default billPaymentService;
