-- OAuth (Google/Apple/Microsoft) accounts have no password, and are identified by
-- (provider, provider_id) instead. NULL provider/provider_id means a LOCAL (email/password) user.
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE users ADD COLUMN provider VARCHAR(20);
ALTER TABLE users ADD COLUMN provider_id VARCHAR(255);
CREATE UNIQUE INDEX uk_users_provider_provider_id ON users (provider, provider_id) WHERE provider IS NOT NULL;

-- Idempotent: promotes the designated admin account if it already exists, no-op otherwise. New
-- registrations of this email are promoted at creation time by application code instead
-- (UserService) -- this migration only covers an account that predates this change.
UPDATE users SET role = 'ADMIN' WHERE lower(email) = 'ividi.dev@gmail.com';
