-- ====================================================================
-- Database & Table Character Set Migration to utf8mb4
-- Ensures Unicode safety for all scripts (Indian scripts, Arabic, CJK, etc.)
-- ====================================================================

-- 1. Database level default charset and collation
ALTER DATABASE financedb CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 2. Transactions table
CREATE TABLE IF NOT EXISTS transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    amount DECIMAL(19, 8) NOT NULL,
    description VARCHAR(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    payment_method VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    category VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    matched_rule VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    confidence_source VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    is_manually_categorized BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    username VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL
) CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

ALTER TABLE transactions CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE transactions MODIFY description VARCHAR(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL;
ALTER TABLE transactions MODIFY category VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL;
ALTER TABLE transactions MODIFY payment_method VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL;
ALTER TABLE transactions MODIFY matched_rule VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 3. Incomes table
CREATE TABLE IF NOT EXISTS incomes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    amount DECIMAL(19, 8) NOT NULL,
    description VARCHAR(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    category VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    matched_rule VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    confidence_source VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    is_manually_categorized BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    username VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL
) CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

ALTER TABLE incomes CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE incomes MODIFY description VARCHAR(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL;
ALTER TABLE incomes MODIFY category VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL;

-- 4. Savings Goals table
CREATE TABLE IF NOT EXISTS savings_goals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    target_amount DECIMAL(19, 8) NOT NULL,
    current_amount DECIMAL(19, 8) NOT NULL,
    target_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    username VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL
) CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

ALTER TABLE savings_goals CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE savings_goals MODIFY name VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL;

-- 5. Subscriptions table
CREATE TABLE IF NOT EXISTS subscriptions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    amount DECIMAL(19, 8) NOT NULL,
    category VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    billing_cycle VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    next_billing_date DATE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    username VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL
) CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

ALTER TABLE subscriptions CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE subscriptions MODIFY name VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL;
ALTER TABLE subscriptions MODIFY category VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL;

-- 6. Currency Config table
CREATE TABLE IF NOT EXISTS currency_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    currency_code VARCHAR(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL UNIQUE,
    currency_symbol VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    exchange_rate_to_usd DECIMAL(19, 8) NOT NULL,
    currency_name VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

ALTER TABLE currency_configs CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
