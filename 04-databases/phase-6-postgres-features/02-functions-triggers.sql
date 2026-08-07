-- ============================================================================
-- Lesson 6.2 — Functions, stored procedures (PL/pgSQL), and triggers
-- Uses the Phase 2 bookstore schema.
-- ============================================================================

-- PL/pgSQL is Postgres's procedural language — logic that runs INSIDE the DB.
-- Use for reusable calculations, data-integrity enforcement beyond constraints,
-- and triggers. (Trade-off: logic in the DB is fast and central but harder to
-- version/test than app code — use judiciously.)

-- 1. A FUNCTION that returns a value:
CREATE OR REPLACE FUNCTION book_revenue(p_book_id INT)
RETURNS NUMERIC AS $$
DECLARE
    total NUMERIC;
BEGIN
    SELECT COALESCE(SUM(s.quantity * b.price), 0)
    INTO total
    FROM sale s JOIN book b ON b.id = s.book_id
    WHERE b.id = p_book_id;
    RETURN total;
END;
$$ LANGUAGE plpgsql;

SELECT title, book_revenue(id) AS revenue FROM book ORDER BY revenue DESC;

-- 2. A table-returning function (parameterized like a view):
CREATE OR REPLACE FUNCTION books_over_price(p_min NUMERIC)
RETURNS TABLE(title TEXT, price NUMERIC) AS $$
BEGIN
    RETURN QUERY SELECT b.title, b.price FROM book b WHERE b.price > p_min ORDER BY b.price DESC;
END;
$$ LANGUAGE plpgsql;

SELECT * FROM books_over_price(40);

-- 3. A PROCEDURE (CALL, can manage its own transactions; no return value):
CREATE OR REPLACE PROCEDURE give_raise_to_prices(p_pct NUMERIC)
LANGUAGE plpgsql AS $$
BEGIN
    UPDATE book SET price = price * (1 + p_pct / 100.0);
    -- procedures can COMMIT/ROLLBACK (functions cannot);
END;
$$;
-- CALL give_raise_to_prices(10);   -- +10% on all books

-- ============================================================
-- TRIGGERS — run a function automatically on INSERT/UPDATE/DELETE. Use for
-- audit logs, maintaining derived columns, enforcing complex rules, or keeping
-- a denormalized copy in sync.
-- ============================================================

-- Example: an audit table + a trigger that logs every price change.
CREATE TABLE IF NOT EXISTS price_audit (
    id         SERIAL PRIMARY KEY,
    book_id    INT,
    old_price  NUMERIC,
    new_price  NUMERIC,
    changed_at TIMESTAMPTZ DEFAULT now()
);

-- The trigger FUNCTION: NEW/OLD are the row after/before the change.
CREATE OR REPLACE FUNCTION log_price_change()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.price <> OLD.price THEN
        INSERT INTO price_audit (book_id, old_price, new_price)
        VALUES (OLD.id, OLD.price, NEW.price);
    END IF;
    RETURN NEW;      -- for BEFORE triggers, the returned row is what gets written
END;
$$ LANGUAGE plpgsql;

-- Attach it: fire BEFORE each UPDATE of a row.
DROP TRIGGER IF EXISTS trg_price_audit ON book;
CREATE TRIGGER trg_price_audit
    BEFORE UPDATE ON book
    FOR EACH ROW
    EXECUTE FUNCTION log_price_change();

-- Now any price update is logged automatically:
UPDATE book SET price = price + 1 WHERE title = 'Clean Code';
SELECT * FROM price_audit;

-- ----------------------------------------------------------------------------
-- FUNCTION vs PROCEDURE: functions RETURN a value and run inside the caller's
-- transaction; procedures are CALLed and can control transactions. TRIGGERS
-- automate reactions to data changes. Powerful, but keep heavy business logic in
-- the app (Spring service layer) where it's testable/versioned — use DB code for
-- data-integrity and audit concerns that must hold regardless of the app.
-- ----------------------------------------------------------------------------
