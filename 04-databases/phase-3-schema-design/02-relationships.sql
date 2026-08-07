-- ============================================================================
-- Lesson 3.2 — Relationships: 1-to-1, 1-to-many, many-to-many
-- Run:  psql -U <user> -d <db> -f 02-relationships.sql
--
-- Relationships are modeled with FOREIGN KEYS. Where the FK lives (and whether
-- it's UNIQUE) determines the cardinality.
-- ============================================================================

DROP TABLE IF EXISTS enrollment CASCADE;
DROP TABLE IF EXISTS course CASCADE;
DROP TABLE IF EXISTS student CASCADE;
DROP TABLE IF EXISTS user_profile CASCADE;
DROP TABLE IF EXISTS app_user CASCADE;

-- ============================================================
-- ONE-TO-ONE: each user has at most one profile, each profile one user.
-- Model: FK on the child + a UNIQUE constraint (so it can't be one-to-many).
-- Use when you split rarely-used/large columns off the main table.
-- ============================================================
CREATE TABLE app_user (
    id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email  TEXT NOT NULL UNIQUE
);
CREATE TABLE user_profile (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id  BIGINT NOT NULL UNIQUE REFERENCES app_user(id) ON DELETE CASCADE,  -- UNIQUE => 1:1
    bio      TEXT,
    avatar   TEXT
);

-- ============================================================
-- ONE-TO-MANY: one course has many students? No — here one STUDENT belongs to
-- one advisor... let's use the classic: one AUTHOR has many BOOKS.
-- Model: FK on the "many" side, NOT unique. (This is the most common relationship.)
-- ============================================================
CREATE TABLE student (
    id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name  TEXT NOT NULL
);
CREATE TABLE course (
    id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title TEXT NOT NULL
);

-- ============================================================
-- MANY-TO-MANY: a student takes many courses; a course has many students.
-- You CANNOT put a FK on either side. Model with a JUNCTION (join/bridge)
-- table holding a FK to each, with a composite PK to prevent duplicate pairs.
-- The junction is also the natural home for relationship ATTRIBUTES (grade, date).
-- ============================================================
CREATE TABLE enrollment (
    student_id  BIGINT NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    course_id   BIGINT NOT NULL REFERENCES course(id)  ON DELETE CASCADE,
    grade       TEXT,
    enrolled_on DATE NOT NULL DEFAULT current_date,
    PRIMARY KEY (student_id, course_id)     -- composite PK: a pair appears once
);

-- ---- Seed ----
INSERT INTO app_user (email) VALUES ('ajay@dev.io');
INSERT INTO user_profile (user_id, bio) VALUES (1, 'backend dev');

INSERT INTO student (name) VALUES ('Ajay'), ('Meera'), ('Ravi');
INSERT INTO course (title) VALUES ('Databases'), ('Algorithms'), ('Networks');

INSERT INTO enrollment (student_id, course_id, grade) VALUES
    (1, 1, 'A'), (1, 2, 'B'),          -- Ajay: Databases, Algorithms
    (2, 1, 'A'), (2, 3, 'A'),          -- Meera: Databases, Networks
    (3, 2, 'C');                       -- Ravi: Algorithms

-- ---- Query a many-to-many: resolve it with TWO joins through the junction ----
-- "Who takes which course?"
SELECT s.name AS student, c.title AS course, e.grade
FROM enrollment e
JOIN student s ON s.id = e.student_id
JOIN course  c ON c.id = e.course_id
ORDER BY s.name, c.title;

-- "How many students per course?" (aggregate over the junction)
SELECT c.title, count(*) AS students
FROM enrollment e JOIN course c ON c.id = e.course_id
GROUP BY c.title ORDER BY students DESC;

-- ----------------------------------------------------------------------------
-- CARDINALITY CHEATSHEET (where does the FK go?):
--   1:1    -> FK on either side + UNIQUE on the FK
--   1:many -> FK on the MANY side (no UNIQUE)
--   many:many -> a JUNCTION table with a FK to each side + composite PK
-- The junction table turns an unmodelable m:n into two 1:many relationships,
-- and is where relationship attributes (grade, joined date, role) live.
-- ----------------------------------------------------------------------------
