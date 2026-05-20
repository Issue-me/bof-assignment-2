import api from './api';

/**
 * Service for bank statement API calls.
 * Handles fetching statements and downloading PDFs.
 */

export const getAvailableStatements = async (accountId) => {
  try {
    const response = await api.get('/statements', {
      params: { accountId },
    });
    return response.data;
  } catch (error) {
    console.error('Error fetching available statements:', error);
    throw error;
  }
};

export const generateStatement = async (accountId, fromDate, toDate) => {
  try {
    const response = await api.post('/statements', {
      accountId,
      fromDate,
      toDate,
    });
    return response.data;
  } catch (error) {
    console.error('Error generating statement:', error);
    throw error;
  }
};

export const downloadStatementPdf = async (accountId, fromDate, toDate) => {
  try {
    const response = await api.get('/statements/download', {
      params: {
        accountId,
        fromDate,
        toDate,
      },
      responseType: 'blob',
    });

    // Create blob link to download
    const url = window.URL.createObjectURL(new Blob([response.data]));
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `statement_${fromDate}_to_${toDate}.pdf`);
    document.body.appendChild(link);
    link.click();
    link.parentElement.removeChild(link);
    window.URL.revokeObjectURL(url);
  } catch (error) {
    console.error('Error downloading statement PDF:', error);
    throw error;
  }
};

export default {
  getAvailableStatements,
  generateStatement,
  downloadStatementPdf,
};
