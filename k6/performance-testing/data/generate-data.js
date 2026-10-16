const fs = require('fs');
const path = require('path');

const TOTAL_USERS = 10000;
const BATCH_SIZE = 1000;
const PASSWORD = 'Password@123';
// Pre-computed BCrypt hash (cost 10) for 'Password@123'
const BCRYPT_HASH = '$2a$10$1g0K0QuYuLodqHt0ljhRhOeqPvCdujHcGvrd1Rc7.apSnGvvwzDcG';

const USERS_JSON_PATH = path.join(__dirname, 'users.json');
const SEED_SQL_PATH = path.join(__dirname, 'seed-users.sql');

console.log(`[1/3] Generating ${TOTAL_USERS} user credentials...`);

const users = [];
for (let i = 1; i <= TOTAL_USERS; i++) {
  const indexStr = String(i).padStart(5, '0');
  const email = `loadtest_user_${indexStr}@ticketbox.test`;
  users.push({
    email: email,
    password: PASSWORD
  });
}

console.log(`[2/3] Writing users list to ${USERS_JSON_PATH}...`);
fs.writeFileSync(USERS_JSON_PATH, JSON.stringify(users, null, 2), 'utf-8');

console.log(`[3/3] Generating SQL seed script to ${SEED_SQL_PATH}...`);
const sqlLines = [];
sqlLines.push('-- TicketBox Load Testing: 10,000 Users Seed Script');
sqlLines.push('-- Generates active AUDIENCE users with LOCAL account credentials');
sqlLines.push('BEGIN;');
sqlLines.push('');

for (let batchStart = 0; batchStart < TOTAL_USERS; batchStart += BATCH_SIZE) {
  const batchEnd = Math.min(batchStart + BATCH_SIZE, TOTAL_USERS);
  
  // Insert Users
  sqlLines.push(`-- Batch Users: ${batchStart + 1} to ${batchEnd}`);
  sqlLines.push('INSERT INTO users (id, email, full_name, phone, role, status, created_at, updated_at)');
  sqlLines.push('VALUES');
  const userRows = [];
  for (let i = batchStart + 1; i <= batchEnd; i++) {
    const idx = String(i).padStart(5, '0');
    const userId = `a0000000-0000-0000-0000-${String(i).padStart(12, '0')}`;
    const email = `loadtest_user_${idx}@ticketbox.test`;
    const fullName = `Load Test User ${i}`;
    const phone = `+848${String(i).padStart(8, '0')}`;
    userRows.push(`  ('${userId}', '${email}', '${fullName}', '${phone}', 'AUDIENCE', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)`);
  }
  sqlLines.push(userRows.join(',\n'));
  sqlLines.push('ON CONFLICT (email) DO UPDATE SET status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP;');
  sqlLines.push('');

  // Insert User Accounts
  sqlLines.push(`-- Batch User Accounts: ${batchStart + 1} to ${batchEnd}`);
  sqlLines.push('INSERT INTO user_accounts (id, user_id, password_hash, user_account_status, provider, provider_user_id, created_at, updated_at)');
  sqlLines.push('VALUES');
  const accountRows = [];
  for (let i = batchStart + 1; i <= batchEnd; i++) {
    const idx = String(i).padStart(5, '0');
    const accountId = `b0000000-0000-0000-0000-${String(i).padStart(12, '0')}`;
    const userId = `a0000000-0000-0000-0000-${String(i).padStart(12, '0')}`;
    const email = `loadtest_user_${idx}@ticketbox.test`;
    accountRows.push(`  ('${accountId}', '${userId}', '${BCRYPT_HASH}', 'ACTIVE', 'LOCAL', '${email}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)`);
  }
  sqlLines.push(accountRows.join(',\n'));
  sqlLines.push('ON CONFLICT (id) DO UPDATE SET password_hash = EXCLUDED.password_hash, user_account_status = EXCLUDED.user_account_status, updated_at = CURRENT_TIMESTAMP;');
  sqlLines.push('');
}

sqlLines.push('COMMIT;');
sqlLines.push('');

fs.writeFileSync(SEED_SQL_PATH, sqlLines.join('\n'), 'utf-8');

console.log('✅ Generation completed successfully:');
console.log(` - users.json: ${(fs.statSync(USERS_JSON_PATH).size / (1024 * 1024)).toFixed(2)} MB`);
console.log(` - seed-users.sql: ${(fs.statSync(SEED_SQL_PATH).size / (1024 * 1024)).toFixed(2)} MB`);
