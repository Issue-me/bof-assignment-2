import React, { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import './Auth.css';

/**
 * Login page — POST /api/auth/login
 * Elegant Modern Banking Interface
 */
const LoginPage = () => {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const from = location.state?.from?.pathname || '/';

  const [formData, setFormData] = useState({ email: '', password: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(true);

  const togglePasswordVisibility = () => {
    setShowPassword(!showPassword);
    const toggleBtn = document.querySelector('.bred-password-toggle');
    if (toggleBtn) {
      toggleBtn.classList.add('rotating');
      setTimeout(() => toggleBtn.classList.remove('rotating'), 300);
    }
  };

  const handleChange = (e) => {
    setFormData((prev) => ({ ...prev, [e.target.name]: e.target.value }));
    setError('');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      await login(formData.email, formData.password);
      navigate(from, { replace: true });
    } catch (err) {
      setError(
        err.response?.data?.error || 'Login failed. Please check your credentials.'
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="bred-auth-wrapper">
      {/* Left Panel - Welcome Section */}
      <div className="bred-left-panel">
        {/* Animated blobs */}
        <div className="blob blob-1"></div>
        <div className="blob blob-2"></div>
        <div className="blob blob-3"></div>

        <div className="bred-logo-section">
          <div className="bred-logo">
            <div className="bred-logo-image">
              <img src="/assets/logo.png" alt="Bank of Fiji Logo" className="bred-logo-img" />
            </div>
            <div className="bred-bank-text">
              <span className="bred-bank-name">BANK OF FIJI</span>
              <span className="bred-country">Trusted Since 1974</span>
            </div>
          </div>
        </div>

        <div className="bred-welcome-content">
          <h1 className="bred-welcome-title">
            Your finances,<br /><em>beautifully</em><br />connected.
          </h1>
          <p className="bred-welcome-subtitle">
            Secure online banking built for Fiji. Manage accounts, transfer funds, 
            and track every transaction — anytime, anywhere.
          </p>
        </div>

        <div className="trust-row">
          <div className="trust-item">
            <span className="num">250K+</span>
            <span className="lbl">Customers</span>
          </div>
          <div className="trust-divider"></div>
          <div className="trust-item">
            <span className="num">30+</span>
            <span className="lbl">Branches</span>
          </div>
          <div className="trust-divider"></div>
          <div className="trust-item">
            <span className="num">99.9%</span>
            <span className="lbl">Uptime</span>
          </div>
        </div>
      </div>

      {/* Right Panel - Login Form */}
      <div className="bred-right-panel">
        <div className="bred-top-nav">
          <div className="bred-language">
            <span className="active">EN</span>
            <span className="divider">|</span>
            <span>FR</span>
          </div>
          <Link to="/contact" className="bred-nav-link">Contact Us</Link>
        </div>

        <div className="bred-login-content">
          <div className="bred-login-header">
            <div className="bred-login-eyebrow">Secure Access</div>
            <h2 className="bred-login-title">Welcome back.</h2>
            <p className="bred-login-subtitle">Sign in to Bank of Fiji Connect</p>
          </div>

          {/* Error message */}
          {error && (
            <div className="bred-alert bred-alert-error" role="alert">
              {error}
            </div>
          )}

          {/* Login form */}
          <form onSubmit={handleSubmit} className="bred-login-form" noValidate>
            <div className="bred-form-group">
              <label htmlFor="email">Your Login</label>
              <div className="bred-input-wrapper">
                <svg className="bred-input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="2" y="4" width="20" height="16" rx="2"/><path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/></svg>
                <input
                  id="email"
                  type="email"
                  name="email"
                  value={formData.email}
                  onChange={handleChange}
                  className="bred-input"
                  placeholder="you@example.com"
                  required
                  autoComplete="email"
                  autoFocus
                />
              </div>
            </div>

            <div className="bred-form-group">
              <label htmlFor="password">Your Password</label>
              <div className="bred-input-wrapper">
                <div className="bred-password-wrapper">
                  <svg className="bred-input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
                  <input
                    id="password"
                    type={showPassword ? "text" : "password"}
                    name="password"
                    value={formData.password}
                    onChange={handleChange}
                    className="bred-input"
                    placeholder="••••••••••"
                    required
                    autoComplete="current-password"
                  />
                  <button
                    type="button"
                    className="bred-password-toggle"
                    onClick={togglePasswordVisibility}
                    aria-label={showPassword ? "Hide password" : "Show password"}
                  >
                    {showPassword ? (
                      <svg key="eye-open" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                    ) : (
                      <svg key="eye-closed" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
                    )}
                  </button>
                </div>
              </div>
              <div className="field-footer">
                <div className="bred-forgot-password">
                  <Link to="/forgot-password">Forgot your password?</Link>
                </div>
              </div>
            </div>

            <div className="bred-remember">
              <input 
                type="checkbox" 
                id="remember" 
                checked={rememberMe}
                onChange={(e) => setRememberMe(e.target.checked)}
              />
              <label htmlFor="remember" className="bred-check-box"></label>
              <label htmlFor="remember">Remember me on this device</label>
            </div>

            <button
              type="submit"
              className="bred-connection-btn"
              disabled={loading}
            >
              {loading ? 'Authenticating…' : 'Sign In to My Account →'}
            </button>
          </form>

          {/* <div className="bred-register-section">
            <div className="bred-divider-with-text">
              <span className="bred-divider-line"></span>
              <span className="bred-divider-text">OR</span>
              <span className="bred-divider-line"></span>
            </div>

            <p className="bred-register-text">
              Don't have an account? <Link to="/register">Create one</Link>
            </p>
          </div> */}

          <p className="bred-branch-note">
            Need help? Visit your <Link to="/contact">nearest branch</Link> or contact support.
          </p>

          <div className="bred-safe-banking">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
            </svg>
            <span>Safe Banking Guaranteed</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
