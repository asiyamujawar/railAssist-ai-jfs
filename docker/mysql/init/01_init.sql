-- ─────────────────────────────────────────────────────────────────────────────
-- docker/mysql/init/01_init.sql
-- Runs automatically the FIRST TIME the MySQL container starts (volume is empty).
-- The database and user are already created by Docker Compose environment vars;
-- this script just ensures character set and collation are correct.
-- ─────────────────────────────────────────────────────────────────────────────

-- Re-create with correct charset in case MySQL defaults differ
ALTER DATABASE train_concierge
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
