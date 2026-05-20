-- =====================================================
-- Bank of Fiji - Database Schema
-- =====================================================

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    customer_id VARCHAR(20) UNIQUE NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    phone_number VARCHAR(20),
    role VARCHAR(20) NOT NULL CHECK (role IN ('CUSTOMER', 'ADMIN', 'TELLER')),
    is_active BOOLEAN DEFAULT TRUE,
    tin_number VARCHAR(50),
    is_resident BOOLEAN DEFAULT TRUE,
    is_senior_citizen BOOLEAN DEFAULT FALSE,
    date_of_birth DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    last_login TIMESTAMP
);

-- Accounts table
CREATE TABLE IF NOT EXISTS accounts (
    id BIGSERIAL PRIMARY KEY,
    account_number VARCHAR(20) UNIQUE NOT NULL,
    account_name VARCHAR(100),
    account_type VARCHAR(20) NOT NULL,
    balance DECIMAL(19, 2) DEFAULT 0.00,
    interest_rate DECIMAL(5, 4) DEFAULT 0.0000,
    interest_earned DECIMAL(19, 2) DEFAULT 0.00,
    is_active BOOLEAN DEFAULT TRUE,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Account Holders table (for joint accounts and multiple account holders)
CREATE TABLE IF NOT EXISTS account_holders (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    added_by_user_id BIGINT,
    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (added_by_user_id) REFERENCES users(id),
    UNIQUE(account_id, user_id)
);

-- Transactions table
CREATE TABLE IF NOT EXISTS transactions (
    id BIGSERIAL PRIMARY KEY,
    reference_number VARCHAR(50) UNIQUE NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    description VARCHAR(500),
    idempotency_key VARCHAR(255),
    source_account_id BIGINT,
    destination_account_id BIGINT,
    status VARCHAR(20) DEFAULT 'PENDING',
    balance_after DECIMAL(19, 2),
    transaction_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (source_account_id) REFERENCES accounts(id),
    FOREIGN KEY (destination_account_id) REFERENCES accounts(id)
);

-- Billers table
CREATE TABLE IF NOT EXISTS billers (
    id BIGSERIAL PRIMARY KEY,
    biller_name VARCHAR(100) NOT NULL,
    biller_code VARCHAR(50) UNIQUE NOT NULL,
    category VARCHAR(50),
    settlement_account_number VARCHAR(20) UNIQUE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Idempotency records table
CREATE TABLE IF NOT EXISTS idempotency_records (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    transaction_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (transaction_id) REFERENCES transactions(id)
);

-- Bill Payments table
CREATE TABLE IF NOT EXISTS bill_payments (
    id BIGSERIAL PRIMARY KEY,
    payment_reference VARCHAR(50) UNIQUE NOT NULL,
    biller_id BIGINT,
    account_number VARCHAR(50) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    description VARCHAR(500),
    source_account_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    scheduled_date TIMESTAMP,
    processed_date TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (source_account_id) REFERENCES accounts(id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (biller_id) REFERENCES billers(id)
);

-- Scheduled Bill Payments table
CREATE TABLE IF NOT EXISTS scheduled_bill_payments (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    biller_id BIGINT NOT NULL,
    bill_reference VARCHAR(50) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    frequency VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    next_execution_date DATE NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    description VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (account_id) REFERENCES accounts(id),
    FOREIGN KEY (biller_id) REFERENCES billers(id)
);

-- Biller invoices table (used by automated monthly invoice-driven payments)
CREATE TABLE IF NOT EXISTS biller_invoices (
    id BIGSERIAL PRIMARY KEY,
    biller_id BIGINT NOT NULL,
    customer_reference VARCHAR(80) NOT NULL,
    invoice_month INT NOT NULL,
    invoice_year INT NOT NULL,
    invoice_amount DECIMAL(19, 2) NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(20) DEFAULT 'UNPAID',
    bill_payment_id BIGINT,
    paid_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (biller_id) REFERENCES billers(id),
    FOREIGN KEY (bill_payment_id) REFERENCES bill_payments(id),
    UNIQUE (biller_id, customer_reference, invoice_month, invoice_year)
);

-- Notifications table
CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    message VARCHAR(500) NOT NULL,
    type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Transfer limits table
CREATE TABLE IF NOT EXISTS transfer_limits (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(30) UNIQUE NOT NULL,
    daily_limit DECIMAL(19, 2) NOT NULL,
    weekly_limit DECIMAL(19, 2) NOT NULL,
    monthly_limit DECIMAL(19, 2) NOT NULL,
    yearly_limit DECIMAL(19, 2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Forward-only schema adjustments for existing local databases
ALTER TABLE users ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_senior_citizen BOOLEAN;
UPDATE users SET is_senior_citizen = FALSE WHERE is_senior_citizen IS NULL;
ALTER TABLE users ALTER COLUMN is_senior_citizen SET DEFAULT FALSE;
ALTER TABLE users ALTER COLUMN is_senior_citizen SET NOT NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS date_of_birth DATE;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(255);
ALTER TABLE bill_payments ADD COLUMN IF NOT EXISTS biller_id BIGINT;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS interest_earned DECIMAL(19, 2) DEFAULT 0.00;
UPDATE accounts SET interest_earned = 0.00 WHERE interest_earned IS NULL;
ALTER TABLE bill_payments DROP COLUMN IF EXISTS biller_name;
ALTER TABLE bill_payments DROP COLUMN IF EXISTS biller_code;
ALTER TABLE billers ADD COLUMN IF NOT EXISTS settlement_account_number VARCHAR(20);
ALTER TABLE scheduled_bill_payments ADD COLUMN IF NOT EXISTS auto_pay_enabled BOOLEAN DEFAULT TRUE;
UPDATE scheduled_bill_payments SET auto_pay_enabled = TRUE WHERE auto_pay_enabled IS NULL;
ALTER TABLE scheduled_bill_payments ALTER COLUMN auto_pay_enabled SET DEFAULT TRUE;
ALTER TABLE scheduled_bill_payments ALTER COLUMN auto_pay_enabled SET NOT NULL;

ALTER TABLE scheduled_bill_payments ADD COLUMN IF NOT EXISTS approval_given BOOLEAN DEFAULT TRUE;
UPDATE scheduled_bill_payments SET approval_given = TRUE WHERE approval_given IS NULL;
ALTER TABLE scheduled_bill_payments ALTER COLUMN approval_given SET DEFAULT TRUE;
ALTER TABLE scheduled_bill_payments ALTER COLUMN approval_given SET NOT NULL;

ALTER TABLE scheduled_bill_payments ADD COLUMN IF NOT EXISTS last_processed_month INT;
ALTER TABLE scheduled_bill_payments ADD COLUMN IF NOT EXISTS last_processed_year INT;
ALTER TABLE scheduled_bill_payments ADD COLUMN IF NOT EXISTS last_attempt_at TIMESTAMP;
ALTER TABLE scheduled_bill_payments ADD COLUMN IF NOT EXISTS last_failure_reason VARCHAR(500);

ALTER TABLE user_interest_summaries
    ADD COLUMN IF NOT EXISTS nrwht_refunded BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS nrwht_refund_reference VARCHAR(100) NULL;

-- Add customer profile management fields
ALTER TABLE users ADD COLUMN IF NOT EXISTS national_id VARCHAR(50);
ALTER TABLE users ADD COLUMN IF NOT EXISTS date_of_birth DATE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS address VARCHAR(255);
-- Ensure the role check constraint allows TELLER (drop old constraint then recreate)
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
UPDATE users SET role = 'TELLER' WHERE role = 'STAFF';
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('CUSTOMER', 'ADMIN', 'TELLER'));

-- Loans table
CREATE TABLE IF NOT EXISTS loans (
    id BIGSERIAL PRIMARY KEY,
    loan_number VARCHAR(20) UNIQUE NOT NULL,
    loan_type VARCHAR(50) NOT NULL,
    principal_amount DECIMAL(19, 2) NOT NULL,
    interest_rate DECIMAL(5, 4) NOT NULL,
    term_months INT NOT NULL,
    monthly_payment DECIMAL(19, 2),
    outstanding_balance DECIMAL(19, 2),
    status VARCHAR(20) DEFAULT 'PENDING',
    user_id BIGINT NOT NULL,
    disbursement_account_id BIGINT,
    application_date DATE NOT NULL,
    approval_date DATE,
    start_date DATE,
    end_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (disbursement_account_id) REFERENCES accounts(id)
);

-- Investments table
CREATE TABLE IF NOT EXISTS investments (
    id BIGSERIAL PRIMARY KEY,
    investment_number VARCHAR(20) UNIQUE NOT NULL,
    investment_type VARCHAR(50) NOT NULL,
    principal_amount DECIMAL(19, 2) NOT NULL,
    interest_rate DECIMAL(5, 4) NOT NULL,
    current_value DECIMAL(19, 2),
    term_months INT,
    is_active BOOLEAN DEFAULT TRUE,
    user_id BIGINT NOT NULL,
    linked_account_id BIGINT,
    start_date DATE NOT NULL,
    maturity_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (linked_account_id) REFERENCES accounts(id)
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_accounts_user ON accounts(user_id);
CREATE INDEX IF NOT EXISTS idx_accounts_number ON accounts(account_number);
CREATE INDEX IF NOT EXISTS idx_transactions_source ON transactions(source_account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_dest ON transactions(destination_account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_date ON transactions(transaction_date);
CREATE INDEX IF NOT EXISTS idx_transactions_idempotency_key ON transactions(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_idempotency_expires ON idempotency_records(expires_at);
CREATE INDEX IF NOT EXISTS idx_billers_code ON billers(biller_code);
CREATE INDEX IF NOT EXISTS idx_billers_settlement_account_number ON billers(settlement_account_number);
CREATE INDEX IF NOT EXISTS idx_bill_payments_biller_id ON bill_payments(biller_id);
CREATE INDEX IF NOT EXISTS idx_biller_invoices_biller_reference ON biller_invoices(biller_id, customer_reference);
CREATE INDEX IF NOT EXISTS idx_biller_invoices_status_due_date ON biller_invoices(status, due_date);
CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_transfer_limits_category ON transfer_limits(category);
CREATE INDEX IF NOT EXISTS idx_loans_user ON loans(user_id);
CREATE INDEX IF NOT EXISTS idx_investments_user ON investments(user_id);