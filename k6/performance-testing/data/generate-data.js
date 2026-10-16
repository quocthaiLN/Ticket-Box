const fs = require('fs');
const path = require('path');

const TOTAL_USERS = 10000;
const BATCH_SIZE = 1000;
const PASSWORD = 'Password@123';
// Pre-computed BCrypt hash (cost 10) for 'Password@123'
const BCRYPT_HASH = '$2a$10$1g0K0QuYuLodqHt0ljhRhOeqPvCdujHcGvrd1Rc7.apSnGvvwzDcG';

const USERS_JSON_PATH = path.join(__dirname, 'users.json');
const SEED_SQL_PATH = path.join(__dirname, 'seed-users.sql');

const crypto = require('crypto');
const JWT_SECRET_HEX = process.env.JWT_ACCESS_TOKEN || 'test5709e9900945c4f72186cf92b6ae9ddb30eb3f3906ef92e4e3ffa2dc986f';
const secretKey = Buffer.from(JWT_SECRET_HEX, 'hex');

function generateJwt(userId) {
  const b64 = (obj) => Buffer.from(JSON.stringify(obj)).toString('base64url');
  const header = b64({ alg: 'HS256', typ: 'JWT' });
  const now = Math.floor(Date.now() / 1000);
  const payload = b64({
    jti: crypto.randomUUID(),
    sub: userId,
    role: 'AUDIENCE',
    iat: now,
    exp: now + 86400 * 30, // 30 days validity
  });
  const sig = crypto.createHmac('sha256', secretKey).update(`${header}.${payload}`).digest('base64url');
  return `${header}.${payload}.${sig}`;
}

console.log(`[1/3] Generating ${TOTAL_USERS} user credentials and pre-signed JWT tokens...`);

const users = [];
for (let i = 1; i <= TOTAL_USERS; i++) {
  const indexStr = String(i).padStart(5, '0');
  const userId = `a0000000-0000-0000-0000-${String(i).padStart(12, '0')}`;
  const email = `loadtest_user_${indexStr}@ticketbox.test`;
  users.push({
    id: userId,
    email: email,
    password: PASSWORD,
    token: generateJwt(userId),
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

console.log('✅ File generation completed successfully:');
console.log(` - users.json: ${(fs.statSync(USERS_JSON_PATH).size / (1024 * 1024)).toFixed(2)} MB`);
console.log(` - seed-users.sql: ${(fs.statSync(SEED_SQL_PATH).size / (1024 * 1024)).toFixed(2)} MB`);

// Step 4: Automatically seed into PostgreSQL database
if (process.argv.includes('--skip-db')) {
  console.log('\n⏩ Skipping database seed (--skip-db passed).');
} else {
  const { execSync } = require('child_process');
  console.log('\n[4/4] Seeding 10,000 users into PostgreSQL database...');
  let seeded = false;

  // Attempt 1: Via Docker container 'ticketbox-postgres'
  try {
    execSync(`docker exec -i ticketbox-postgres psql -U postgres -d ticketbox < "${SEED_SQL_PATH}"`, {
      stdio: ['pipe', 'pipe', 'pipe'],
    });
    console.log('✅ Successfully seeded 10,000 users into PostgreSQL via Docker (ticketbox-postgres)!');
    seeded = true;
  } catch (dockerErr) {
    // Attempt 2: Via local psql CLI
    try {
      const psqlEnv = { ...process.env, PGPASSWORD: process.env.DB_PASSWORD || 'password' };
      const host = process.env.DB_HOST || 'localhost';
      const port = process.env.DB_PORT || '5432';
      const user = process.env.DB_USER || 'postgres';
      const db = process.env.DB_NAME || 'ticketbox';
      execSync(`psql -h ${host} -p ${port} -U ${user} -d ${db} -f "${SEED_SQL_PATH}"`, {
        env: psqlEnv,
        stdio: ['pipe', 'pipe', 'pipe'],
      });
      console.log(`✅ Successfully seeded 10,000 users into PostgreSQL via local psql (${host}:${port})!`);
      seeded = true;
    } catch (fallbackErr) {
      console.warn('⚠️ Could not automatically execute seed script into database:');
      console.warn(`   Docker error: ${dockerErr.message.split('\n')[0]}`);
      console.warn(`   psql error: ${fallbackErr.message.split('\n')[0]}`);
      console.warn(`👉 To seed manually, please run:\n   docker exec -i ticketbox-postgres psql -U postgres -d ticketbox < "${SEED_SQL_PATH}"`);
    }
  }

  if (seeded) {
    try {
      const countOutput = execSync(
        `docker exec ticketbox-postgres psql -U postgres -d ticketbox -t -c "SELECT COUNT(*) FROM users WHERE email LIKE 'loadtest_user_%';"`,
        { encoding: 'utf-8' }
      );
      console.log(`📊 Verified in database: ${countOutput.trim()} load test users are ready!`);
    } catch (e) {
      // ignore
    }
  }
}
