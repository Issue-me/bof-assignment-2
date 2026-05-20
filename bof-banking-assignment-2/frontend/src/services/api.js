import axios from 'axios';

const rawApiUrl = process.env.REACT_APP_API_URL || 'http://localhost:8081';
const API_BASE_URL = `${rawApiUrl.replace(/\/$/, '')}/api`;
// const API_BASE_URL = `https://api.kaloka-youth.org/api/`;
//lets front end know where the backend is located and also attach JWT token to every request
const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor — attach JWT token to every request
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('bof_token');
    console.log('[AUTH] API Interceptor:', {
      url: config.url,
      hasToken: !!token,
      tokenLength: token ? token.length : 0,
    });
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
      console.log('[OK] Token attached to request');
    } else {
      console.warn('[WARN] No token found in localStorage for request:', config.url);
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor — handle 401 globally
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    const requestUrl = error.config?.url || '';

    if (status === 401) {
      localStorage.removeItem('bof_token');
      localStorage.removeItem('bof_user');
      window.location.href = '/login';
    }

    if (status === 403) {
      const isLikelyRoleRestricted =
        requestUrl.includes('/admin/') ||
        requestUrl.includes('/monitoring') ||
        requestUrl.endsWith('/trigger');

      if (!isLikelyRoleRestricted) {
        localStorage.removeItem('bof_token');
        localStorage.removeItem('bof_user');
        window.location.href = '/login';
      }
    }

    if (!error.response) {
      console.error('[API] Network error or backend unavailable');
    } else {
      console.error('[API] Request failed', {
        status: error.response.status,
        url: error.config?.url,
        method: error.config?.method,
      });
    }

    return Promise.reject(error);
  }
);

export default api;
