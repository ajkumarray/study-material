-- ============================================================================
-- Lesson 3.3 — Normalization: 1NF -> 2NF -> 3NF, by transforming a bad table
-- Run:  psql -U <user> -d <db> -f 03-normalization.sql
--
-- Normalization = organizing columns/tables to REDUCE REDUNDANCY and prevent
-- UPDATE ANOMALIES. We start with one ugly "spreadsheet" table and fix it,
-- one normal form at a time. Each form assumes the previous one holds.
-- ============================================================================

-- ============================================================
-- THE STARTING POINT — an UNNORMALIZED orders table (what a spreadsheet
-- becomes). One row per order, cramming everything in:
--
--   order_id | customer_name | customer_city | products              | prices
--   ---------+---------------+---------------+-----------------------+---------
--   1        | Ajay          | Pune          | "Clean Code, Refactoring" | "38.50, 47.99"
--   2        | Meera         | Mumbai        | "Effective Java"      | "45.00"
--
-- Problems: multiple values in one cell (products/prices), customer info
-- repeated on every order, and no way to query "orders for Clean Code" cleanly.
-- ============================================================

-- ------------------------------------------------------------
-- 1NF (First Normal Form): ATOMIC values — no repeating groups or multi-value
-- cells. Each cell holds ONE value; each row is unique. Split the list into
-- one row per product.
--
--   order_id | line | customer_name | customer_city | product      | price
--   ---------+------+---------------+---------------+--------------+-------
--   1        | 1    | Ajay          | Pune          | Clean Code   | 38.50
--   1        | 2    | Ajay          | Pune          | Refactoring  | 47.99
--   2        | 1    | Meera         | Mumbai        | Effective Java| 45.00
--
-- Now it's queryable — but customer data is DUPLICATED per line (anomaly risk).
-- ------------------------------------------------------------
DROP TABLE IF EXISTS orders_1nf;
CREATE TABLE orders_1nf (
    order_id      INT,
    line          INT,
    customer_name TEXT,
    customer_city TEXT,
    product       TEXT,
    price         NUMERIC(8,2),
    PRIMARY KEY (order_id, line)     -- composite key identifies a line
);
INSERT INTO orders_1nf VALUES
    (1,1,'Ajay','Pune','Clean Code',38.50),
    (1,2,'Ajay','Pune','Refactoring',47.99),
    (2,1,'Meera','Mumbai','Effective Java',45.00);

-- ------------------------------------------------------------
-- 2NF: be in 1NF AND remove PARTIAL DEPENDENCIES — no non-key column may depend
-- on only PART of a composite key. Here the PK is (order_id, line), but
-- customer_name/city depend on order_id ALONE (not the line). So they're
-- partially dependent -> split customers off into an orders table.
--
-- FUNCTIONAL DEPENDENCY (FD): X -> Y means "X determines Y". Here:
--   order_id -> customer_name, customer_city   (partial: only part of the key)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS orders_2nf, order_lines_2nf CASCADE;
CREATE TABLE orders_2nf (
    order_id      INT PRIMARY KEY,
    customer_name TEXT,
    customer_city TEXT
);
CREATE TABLE order_lines_2nf (
    order_id  INT REFERENCES orders_2nf(order_id),
    line      INT,
    product   TEXT,
    price     NUMERIC(8,2),
    PRIMARY KEY (order_id, line)
);
INSERT INTO orders_2nf VALUES (1,'Ajay','Pune'), (2,'Meera','Mumbai');
INSERT INTO order_lines_2nf VALUES
    (1,1,'Clean Code',38.50), (1,2,'Refactoring',47.99), (2,1,'Effective Java',45.00);
-- Customer data now stored ONCE per order, not per line.

-- ------------------------------------------------------------
-- 3NF: be in 2NF AND remove TRANSITIVE DEPENDENCIES — no non-key column may
-- depend on ANOTHER non-key column. Suppose we also stored the customer's city
-- AND that city's country: order_id -> customer -> country. `country` depends
-- on the customer, not the order key -> transitive. Also, customer_name itself
-- repeats across orders. Extract CUSTOMER into its own table.
--
--   order_id -> customer_id -> customer_name, city   (transitive on customer_id)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS orders_3nf, customer_3nf, order_lines_3nf CASCADE;
CREATE TABLE customer_3nf (
    customer_id   INT PRIMARY KEY,
    customer_name TEXT NOT NULL,
    city          TEXT
);
CREATE TABLE orders_3nf (
    order_id     INT PRIMARY KEY,
    customer_id  INT REFERENCES customer_3nf(customer_id),   -- FK, not repeated data
    ordered_on   DATE DEFAULT current_date
);
CREATE TABLE order_lines_3nf (
    order_id  INT REFERENCES orders_3nf(order_id),
    line      INT,
    product   TEXT,
    price     NUMERIC(8,2),
    PRIMARY KEY (order_id, line)
);
INSERT INTO customer_3nf VALUES (1,'Ajay','Pune'), (2,'Meera','Mumbai');
INSERT INTO orders_3nf (order_id, customer_id) VALUES (1,1), (2,2);
INSERT INTO order_lines_3nf VALUES
    (1,1,'Clean Code',38.50), (1,2,'Refactoring',47.99), (2,1,'Effective Java',45.00);

-- Now each fact lives in EXACTLY ONE place:
--   * a customer's name/city -> customer_3nf (change it once)
--   * an order's customer     -> orders_3nf (a FK)
--   * a line's product/price  -> order_lines_3nf
-- Reassemble with joins when needed:
SELECT o.order_id, c.customer_name, c.city, l.product, l.price
FROM orders_3nf o
JOIN customer_3nf c ON c.customer_id = o.customer_id
JOIN order_lines_3nf l ON l.order_id = o.order_id
ORDER BY o.order_id, l.line;

-- ----------------------------------------------------------------------------
-- THE ANOMALIES normalization prevents (why we bother):
--   UPDATE anomaly : Ajay moves city -> in 1NF you must update EVERY line row;
--                    miss one and the data contradicts itself. In 3NF: one row.
--   INSERT anomaly : can't add a customer with no order yet (1NF) -> in 3NF you can.
--   DELETE anomaly : deleting Ajay's only order erases his existence (1NF)
--                    -> in 3NF the customer row survives.
-- Mnemonic for 3NF: every non-key column depends on
--   "the KEY, the WHOLE key, and NOTHING BUT the key" (1NF, 2NF, 3NF).
-- BCNF is a stricter 3NF (every determinant is a candidate key) — rarely differs.
-- ----------------------------------------------------------------------------
