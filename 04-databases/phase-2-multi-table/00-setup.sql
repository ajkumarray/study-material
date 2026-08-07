-- ============================================================================
-- Phase 2 setup — a richer bookstore: authors, books, customers, and sales.
-- Run first:  psql -U <user> -d <db> -f 00-setup.sql
-- Idempotent (drops + recreates). This schema powers all Phase 2 lessons.
-- ============================================================================

DROP TABLE IF EXISTS sale CASCADE;
DROP TABLE IF EXISTS book CASCADE;
DROP TABLE IF EXISTS customer CASCADE;
DROP TABLE IF EXISTS author CASCADE;

CREATE TABLE author (
    id       SERIAL PRIMARY KEY,
    name     TEXT NOT NULL,
    country  TEXT
);

CREATE TABLE book (
    id         SERIAL PRIMARY KEY,
    title      TEXT NOT NULL,
    author_id  INTEGER REFERENCES author(id),
    price      NUMERIC(6,2) NOT NULL,
    genre      TEXT
);

CREATE TABLE customer (
    id    SERIAL PRIMARY KEY,
    name  TEXT NOT NULL,
    city  TEXT
);

CREATE TABLE sale (
    id           SERIAL PRIMARY KEY,
    book_id      INTEGER REFERENCES book(id),
    customer_id  INTEGER REFERENCES customer(id),
    quantity     INTEGER NOT NULL,
    sold_on      DATE NOT NULL
);

INSERT INTO author (name, country) VALUES
    ('Robert Martin', 'USA'),      -- 1
    ('Martin Fowler', 'UK'),       -- 2
    ('Joshua Bloch',  'USA'),      -- 3
    ('Kathy Sierra',  'USA'),      -- 4
    ('Silent Author', 'Canada');   -- 5  (writes no books -> LEFT JOIN demo)

INSERT INTO book (title, author_id, price, genre) VALUES
    ('Clean Code',           1, 38.50, 'tech'),      -- 1
    ('Clean Architecture',   1, 32.00, 'tech'),      -- 2
    ('Refactoring',          2, 47.99, 'tech'),      -- 3
    ('Effective Java',       3, 45.00, 'tech'),      -- 4
    ('Head First Java',      4, 29.99, 'tech'),      -- 5
    ('An Orphan Book',    NULL, 19.99, 'fiction');   -- 6  (NULL author -> JOIN demo)

INSERT INTO customer (name, city) VALUES
    ('Ajay',  'Pune'),     -- 1
    ('Meera', 'Mumbai'),   -- 2
    ('Ravi',  'Pune'),     -- 3
    ('Zoya',  'Delhi');    -- 4  (buys nothing -> LEFT JOIN demo)

INSERT INTO sale (book_id, customer_id, quantity, sold_on) VALUES
    (1, 1, 2, '2026-01-10'),
    (1, 2, 1, '2026-01-12'),
    (4, 1, 1, '2026-02-01'),
    (3, 3, 3, '2026-02-05'),
    (1, 3, 1, '2026-02-20'),
    (5, 2, 2, '2026-03-01'),
    (4, 2, 1, '2026-03-03'),
    (2, 1, 1, '2026-03-15');
-- Note: book 6 ('An Orphan Book') has NO sales -> useful for anti-joins.

SELECT (SELECT count(*) FROM author)   AS authors,
       (SELECT count(*) FROM book)     AS books,
       (SELECT count(*) FROM customer) AS customers,
       (SELECT count(*) FROM sale)     AS sales;
