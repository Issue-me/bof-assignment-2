import React from 'react';
import './ConfirmDialog.css';

/**
 * Confirmation dialog component 
 * @param {string} message - The confirmation message
 * @param {string} title - Dialog title (optional)
 * @param {string} confirmText - Text for confirm button (default: "OK")
 * @param {string} cancelText - Text for cancel button (default: "Cancel")
 * @param {function} onConfirm - Callback when confirmed
 * @param {function} onCancel - Callback when cancelled
 * @param {string} variant - 'danger' | 'warning' | 'info' (default: 'warning')
 */
const ConfirmDialog = ({
  message,
  title,
  confirmText = 'OK',
  cancelText = 'Cancel',
  onConfirm,
  onCancel,
  variant = 'warning',
  confirmVariant,
  cancelVariant = 'secondary'
}) => {
  const resolvedConfirmVariant = confirmVariant || variant;

  return (
    <div className="confirm-overlay" onClick={onCancel}>
      <div className="confirm-dialog" onClick={(e) => e.stopPropagation()}>
        {title && <div className="confirm-title">{title}</div>}
        <div className="confirm-message">{message}</div>
        <div className="confirm-actions">
          <button
            className={`confirm-btn confirm-btn-primary confirm-btn-${resolvedConfirmVariant}`}
            onClick={onConfirm}
            autoFocus
          >
            {confirmText}
          </button>
          <button
            className={`confirm-btn confirm-btn-${cancelVariant}`}
            onClick={onCancel}
          >
            {cancelText}
          </button>
        </div>
      </div>
    </div>
  );
};

export default ConfirmDialog;
