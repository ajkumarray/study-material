-- ============================================================================
-- Databases Capstone — Part A: a normalized PostgreSQL schema, end to end.
-- Domain: an online bookstore (users buy books; books have authors & genres).
-- Demonstrates: schema design + constraints (P3), analytical queries with joins
-- + window functions (P2), indexing + EXPLAIN (P4), and a transaction (P5).
-- Run:  psql -U <user> -d <db> -f part-a-postgres.sql
-- ============================================================================

DROP TABLE IF EXISTS purchase, book, author, app_user CASCADE;

-- ---- SCHEMA (3NF, with constraints — Phase 3) ----
CREATE TABLE author (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name     TEXT NOT NULL,
    country  TEXT
);
CREATE TABLE book (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title      TEXT NOT NULL,
    author_id  BIGINT NOT NULL REFERENCES author(id),
    genre      TEXT NOT NULL,
    price      NUMERIC(8,2) NOT NULL CHECK (price > 0),
    stock      INT NOT NULL DEFAULT 0 CHECK (stock >= 0)
);
CREATE TABLE app_user (
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email   TEXT NOT NULL UNIQUE,
    balance NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (balance >= 0)
);
CREATE TABLE purchase (                          -- the user<->book junction (many-to-many)
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES app_user(id),
    book_id      BIGINT NOT NULL REFERENCES book(id),
    quantity     INT NOT NULL CHECK (quantity > 0),
    purchased_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---- SEED ----
INSERT INTO author (name, country) VALUES
    ('Robert Martin','USA'), ('Martin Fowler','UK'), ('Joshua Bloch','USA');
INSERT INTO book (title, author_id, genre, price, stock) VALUES
    ('Clean Code',1,'tech',38.50,10), ('Clean Architecture',1,'tech',32.00,5),
    ('Refactoring',2,'tech',47.99,8), ('Effective Java',3,'tech',45.00,12),
    ('A Mystery',2,'fiction',12.99,20);
INSERT INTO app_user (email, balance) VALUES
    ('ajay@dev.io',500), ('meera@dev.io',300);
INSERT INTO purchase (user_id, book_id, quantity) VALUES
    (1,1,2),(1,4,1),(2,3,1),(2,1,1),(1,2,1);

-- ---- ANALYTICAL QUERIES (joins + aggregation + window functions — P2) ----
-- Revenue per genre:
SELECT b.genre, SUM(p.quantity * b.price) AS revenue
FROM purchase p JOIN book b ON b.id = p.book_id
GROUP BY b.genre ORDER BY revenue DESC;

-- Top-selling book PER GENRE (window function — the P2 highlight):
SELECT genre, title, units FROM (
    SELECT b.genre, b.title, SUM(p.quantity) AS units,
           ROW_NUMBER() OVER (PARTITION BY b.genre ORDER BY SUM(p.quantity) DESC) AS rn
    FROM purchase p JOIN book b ON b.id = p.book_id
    GROUP BY b.genre, b.title
) ranked WHERE rn = 1;

-- Each user's spend + running total over time:
SELECT u.email, p.purchased_at::date AS day, (p.quantity*b.price) AS amount,
       SUM(p.quantity*b.price) OVER (PARTITION BY u.id ORDER BY p.purchased_at) AS running_total
FROM purchase p JOIN app_user u ON u.id=p.user_id JOIN book b ON b.id=p.book_id
ORDER BY u.email, p.purchased_at;

-- ---- INDEXING + PROOF (Phase 4) ----
CREATE INDEX idx_purchase_user ON purchase(user_id);
CREATE INDEX idx_purchase_book ON purchase(book_id);
CREATE INDEX idx_book_genre_price ON book(genre, price DESC);   -- composite
ANALYZE;
-- Prove an index is used (look for Index Scan, not Seq Scan):
EXPLAIN ANALYZE SELECT * FROM purchase WHERE user_id = 1;

-- ---- A TRANSACTION: buy a book atomically (Phase 5) ----
-- Debit the user, decrement stock, record the purchase — all or nothing.
BEGIN;
    -- lock the rows we mutate (pessimistic; prevents lost updates / oversell)
    SELECT balance FROM app_user WHERE id = 1 FOR UPDATE;
    SELECT stock, price FROM book WHERE id = 3 FOR UPDATE;

    UPDATE app_user SET balance = balance - (1 * (SELECT price FROM book WHERE id=3)) WHERE id = 1;
    UPDATE book     SET stock   = stock - 1 WHERE id = 3;      -- CHECK(stock>=0) blocks overselling
    INSERT INTO purchase (user_id, book_id, quantity) VALUES (1, 3, 1);
COMMIT;   -- if any step failed (e.g., insufficient balance/stock), ROLLBACK undoes all

SELECT email, balance FROM app_user WHERE id = 1;
SELECT title, stock FROM book WHERE id = 3;

-- ----------------------------------------------------------------------------
-- This one file exercises the entire relational half of the track: a normalized,
-- constrained schema; joins/aggregation/window analytics; indexing verified with
-- EXPLAIN; and an atomic, lock-protected transaction. Part B (see CAPSTONE.md)
-- models the SAME domain in MongoDB, Redis, and Neo4j and contrasts them.
-- ----------------------------------------------------------------------------
