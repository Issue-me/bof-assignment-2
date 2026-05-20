import React from 'react';
import './Toast.css';

/**
 * Toast notification component for displaying temporary messages
 * @param {string} message - The message to display
 * @param {string} type - 'success', 'error', 'info', 'warning'
 * @param {number} duration - Duration in ms to show (default 5000)
 * @param {function} onClose - Callback when toast closes
 */
const Toast = ({ message, type = 'info', duration = 5000, onClose }) => {
  React.useEffect(() => {
    const timer = setTimeout(() => {
      onClose?.();
    }, duration);

    return () => clearTimeout(timer);
  }, [duration, onClose]);

  return (
    <div className={`toast toast-${type}`}>
      <div className="toast-icon">
        {type === 'success'}
        {type === 'error' }
        {type === 'warning' }
        {type === 'info' }
      </div>
      <div className="toast-message">{message}</div>
      <button
        className="toast-close"
        onClick={() => onClose?.()}
        aria-label="Close notification"
      >
        ×
      </button>
    </div>
  );
};

export default Toast;
