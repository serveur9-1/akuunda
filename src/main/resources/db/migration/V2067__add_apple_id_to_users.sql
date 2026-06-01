ALTER TABLE users ADD COLUMN IF NOT EXISTS apple_id VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_users_apple_id ON users(apple_id);
