import api from './api';

/**
 * Transaction service — wraps /api/transactions endpoints.
 */
const transactionService = {
  /**
   * Get transaction history for the authenticated user.
   */
  async getTransactions({ page = 0, size = 10, accountId, entryType } = {}) {
    const params = { page, size, sort: 'transactionDate,desc' };

    if (accountId) {
      params.accountId = accountId;
    }
    if (entryType) {
      params.entryType = entryType;
    }

    const response = await api.get('/transactions', { params });
    return response.data;
  },

  async getMonitoringTransactions({
    page = 0,
    size = 15,
    search,
    transactionType,
    status,
    from,
    to,
    sort = 'transaction_date,desc',
  } = {}) {
    const params = { page, size, sort };

    if (search) {
      params.search = search;
    }
    if (transactionType) {
      params.transactionType = transactionType;
    }
    if (status) {
      params.status = status;
    }
    if (from) {
      params.from = from;
    }
    if (to) {
      params.to = to;
    }

    const response = await api.get('/transactions/monitoring', { params });
    return response.data;
  },

  /**
   * Initiate a funds transfer.
   * If destinationAccountId is provided it will be used; otherwise
   * destinationAccountNumber can be used to transfer to an external account.
   */
  async initiateTransfer({ amount, description, sourceAccountId, destinationAccountId, destinationAccountNumber, idempotencyKey }) {
    const payload = {
      transactionType: 'TRANSFER',
      amount,
      description,
      sourceAccountId,
      destinationAccountId: destinationAccountId || null,
      destinationAccountNumber: destinationAccountNumber || null,
      idempotencyKey,
    };

    const response = await api.post('/transactions/transfer/initiate', payload);
    return response.data;
  },

  /**
   * Verify OTP for a pending high-value transfer and complete it.
   */
  async verifyTransferOtp({ challengeId, otpCode }) {
    const response = await api.post('/transactions/transfer/verify-otp', {
      challengeId,
      otpCode,
    });
    return response.data;
  },

  async getTransferLimits() {
    const response = await api.get('/transactions/transfer-limits');
    return response.data;
  },
};

export default transactionService;
