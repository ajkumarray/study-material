-- ============================================================================
-- Phase 1 setup — a tiny bookstore schema we'll reuse across Phase 1 lessons.
-- Run this first:  psql -U <user> -d <db> -f 00-setup.sql
-- (Idempotent: drops and recreates, so you can re-run anytime.)
-- ============================================================================

DROP TABLE IF EXISTS book CASCADE;
DROP TABLE IF EXISTS author CASCADE;

-- author: one row per writer.
CREATE TABLE author (
    id       SERIAL PRIMARY KEY,          -- auto-incrementing surrogate key
    name     TEXT NOT NULL,
    country  TEXT
);

-- book: one row per title, linked to an author.
CREATE TABLE book (
    id         SERIAL PRIMARY KEY,
    title      TEXT NOT NULL,
    author_id  INTEGER REFERENCES author(id),   -- foreign key -> author
    price      NUMERIC(6,2) NOT NULL,           -- money: exact, never float
    genre      TEXT,
    published  DATE,
    in_stock   INTEGER NOT NULL DEFAULT 0
);

-- Seed data.
INSERT INTO author (name, country) VALUES
    ('Robert Martin', 'USA'),
    ('Joshua Bloch',  'USA'),
    ('Martin Fowler', 'UK'),
    ('Kathy Sierra',  'USA'),
    ('Unknown Writer', NULL);          -- note the NULL country — used in lesson 1.3

INSERT INTO book (title, author_id, price, genre, published, in_stock) VALUES
    ('Clean Code',                 1, 38.50, 'tech',    '2008-08-01', 12),
    ('Clean Architecture',         1, 32.00, 'tech',    '2017-09-20',  0),
    ('Effective Java',             2, 45.00, 'tech',    '2018-01-06',  7),
    ('Refactoring',                3, 47.99, 'tech',    '2018-11-30',  3),
    ('Head First Java',            4, 29.99, 'tech',    '2005-02-09', 20),
    ('Patterns of Enterprise...',  3, 54.00, 'tech',    '2002-11-15',  1),
    ('A Mystery Novel',            5, 12.99, 'fiction', '2010-05-05',  0),
    ('An Untitled Draft',       NULL,  9.99, NULL,       NULL,         5);
-- ^ last row has NULL author, genre, and published on purpose (NULL lessons).

-- Quick check:
SELECT (SELECT count(*) FROM author) AS authors,
       (SELECT count(*) FROM book)   AS books;
