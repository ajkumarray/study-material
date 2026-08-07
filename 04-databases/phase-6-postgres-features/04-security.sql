-- ============================================================================
-- Lesson 6.4 — Roles, privileges, and row-level security
-- (Run as a superuser / DB owner. These are cluster-wide objects.)
-- ============================================================================

-- Postgres access control is built on ROLES (a role is a user and/or a group).
-- Grant the LEAST PRIVILEGE needed (security principle): apps shouldn't connect
-- as a superuser.

-- 1. Create roles:
CREATE ROLE app_readonly;                              -- a group role (no login)
CREATE ROLE app_service LOGIN PASSWORD 'secret';       -- a login role (an app user)

-- 2. GRANT privileges — what a role may do to which objects:
GRANT CONNECT ON DATABASE current_database() TO app_service;   -- (use your db name)
GRANT USAGE ON SCHEMA public TO app_readonly, app_service;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO app_readonly;   -- read-only group
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO app_service;
-- Make it apply to FUTURE tables too:
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO app_readonly;

-- 3. Role membership — grant a group's privileges to a user:
GRANT app_readonly TO app_service;     -- app_service now also has read-only's grants

-- REVOKE takes privileges away:
-- REVOKE DELETE ON book FROM app_service;

-- ============================================================
-- ROW-LEVEL SECURITY (RLS) — restrict WHICH ROWS a role can see/modify, not just
-- which tables. Essential for multi-tenant apps (each tenant sees only its rows).
-- ============================================================
DROP TABLE IF EXISTS document CASCADE;
CREATE TABLE document (
    id       SERIAL PRIMARY KEY,
    owner    TEXT NOT NULL,      -- the app user who owns the row
    content  TEXT
);
INSERT INTO document (owner, content) VALUES ('ajay','A'), ('meera','B'), ('ajay','C');

-- Enable RLS and add a POLICY: a role can only see rows where owner = the
-- current app user (set via a session variable the app configures per request).
ALTER TABLE document ENABLE ROW LEVEL SECURITY;

CREATE POLICY owner_can_see ON document
    USING (owner = current_setting('app.current_user', true));   -- read filter
CREATE POLICY owner_can_write ON document
    FOR ALL
    USING (owner = current_setting('app.current_user', true))
    WITH CHECK (owner = current_setting('app.current_user', true)); -- write filter

-- The app sets who the current user is per session/transaction:
--   SET app.current_user = 'ajay';
--   SELECT * FROM document;   -- now returns ONLY ajay's rows, enforced by the DB
-- Even a buggy query can't leak another tenant's data — the DB filters it.

-- ----------------------------------------------------------------------------
-- SECURITY LAYERS: connect apps as a limited role (not superuser); grant least
-- privilege; use RLS for per-row/tenant isolation; keep secrets out of code
-- (env vars); and remember the app-side defenses too (parameterized queries
-- against SQL injection, Phase 1.4). Defense in depth.
-- ----------------------------------------------------------------------------
