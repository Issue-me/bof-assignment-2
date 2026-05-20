/**
 * Role-Based Access Control utilities for Bank of Fiji
 * Implements the Role Access Matrix:
 * 
 * ADMIN: User Management, Account Administration, Account Holder Management (NO financial transactions)
 * TELLER: Account inquiries, Account Holder Management, Transaction Monitoring (NO user management, NO transactions)
 * CUSTOMER: Financial transactions, view own accounts only
 */

export const ROLES = {
  CUSTOMER: 'CUSTOMER',
  TELLER: 'TELLER',
  ADMIN: 'ADMIN',
};

export const normalizeRole = (role) => {
  const source = Array.isArray(role) ? role[0] : role;
  if (!source) return '';

  const value = typeof source === 'string'
    ? source
    : source.authority || source.role || '';

  return String(value)
    .trim()
    .toUpperCase()
    .replace(/^ROLE_/, '');
};

export const hasAnyRole = (role, allowedRoles = []) => {
  const normalizedRole = normalizeRole(role);
  return allowedRoles.map(normalizeRole).includes(normalizedRole);
};

export const isCustomer = (role) => normalizeRole(role) === ROLES.CUSTOMER;
export const isTeller = (role) => normalizeRole(role) === ROLES.TELLER;
export const isAdmin = (role) => normalizeRole(role) === ROLES.ADMIN;

// Permission helpers per Role Access Matrix
export const canManageAccounts = (role) => hasAnyRole(role, [ROLES.TELLER, ROLES.ADMIN]);
export const canManageAccountHolders = (role) => hasAnyRole(role, [ROLES.TELLER, ROLES.ADMIN]);
export const canManageUsers = (role) => hasAnyRole(role, [ROLES.ADMIN]);
export const canPerformTransactions = (role) => hasAnyRole(role, [ROLES.CUSTOMER]);
export const canCloseAccounts = (role) => hasAnyRole(role, [ROLES.ADMIN]);

/**
 * Returns sidebar menu items based on user role following Role Access Matrix.
 * 
 * ADMIN sees: Manage Accounts, Manage Account Holders, User Management, FRCS Report
 * TELLER sees: Dashboard, Manage Accounts, Manage Account Holders, Transaction Monitoring, Tax Report, Loan Management
 * CUSTOMER sees: Dashboard, Accounts, Transfers, Transaction History, Loans, Bill Payments, Tax Report
 */
export const getSidebarItemsForRole = (role) => {
  if (isAdmin(role)) {
    return [
      { label: 'Dashboard', path: '/dashboard' },
      { label: 'Manage Accounts', path: '/manage-accounts' },
      { label: 'Manage Account Holders', path: '/manage-account-holders' },
      { label: 'User Management', path: '/user-management' },
      { label: 'FRCS Report', path: '/frcs_report' },
      { label: 'Manage Interest Rates', path: '/manage-interest-rates' },
    ];
  }

  if (isTeller(role)) {
    return [
      { label: 'Dashboard', path: '/dashboard' },
      { label: 'Manage Accounts', path: '/manage-accounts' },
      { label: 'Manage Account Holders', path: '/manage-account-holders' },
      { label: 'Transaction Monitoring', path: '/teller/transaction-monitoring' },
      { label: 'Tax Report', path: '/teller/tax-report' },
      { label: 'Manage Interest Rates', path: '/manage-interest-rates' },
      {
        label: 'Loan Management',
        path: '/teller/loan-approvals',
        children: [
          { label: 'Loan Approval', path: '/teller/loan-approvals' },
          { label: 'Manage Loan Rates', path: '/manage-loan-rates' },
        ],
      },
      {
        label: 'Bill Management',
        path: '/teller/billers',
        children: [
          { label: 'Biller Management', path: '/teller/billers' },
          { label: 'Bill Payment Monitoring', path: '/teller/bill-payments/monitoring' },
        ],
      },
    ];
  }

  // CUSTOMER - default role
  return [
    { label: 'Dashboard', path: '/dashboard' },
    { label: 'Accounts', path: '/accounts' },
    { 
      label: 'My Transfers', 
      path: '/transfer',
      children: [
        { label: 'BoF Transfer', path: '/transfer' },
        { label: 'Transfer Limits', path: '/transfer-limits' },
      ]
    },
    { label: 'Transaction History', path: '/transaction_history' },
    {
      label: 'Loans',
      path: '/loans',
      children: [
        { label: 'My Loans', path: '/loans' },
        { label: 'Loan Application', path: '/loan-application' },
      ]
    },
    { 
      label: 'Bill Payments', 
      path: '/bill-payments/scheduled',
      children: [
        { label: 'Pay a Bill Now', path: '/bill-payments/pay-now' },
        { label: 'Scheduled Bill Payments', path: '/bill-payments/scheduled' },
        { label: 'Payment History', path: '/bill-payments/history' },
      ]
    },
    { label: 'Bank Statements', path: '/statements' },
    { label: 'Tax Report', path: '/tax_report' },
  ];
};
