-- TicketBox Database Migration V4: Add user_account_status to user_accounts table
-- Enables lifecycle status tracking per authentication provider/account

ALTER TABLE user_accounts 
    ADD COLUMN user_account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE user_accounts 
    ADD CONSTRAINT chk_user_accounts_status 
    CHECK (user_account_status IN ('ACTIVE', 'SUSPENDED', 'DELETED'));
