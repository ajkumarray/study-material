<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · di](../phase-3-di/NOTES.md) | [Phase 5 · validation errors ➡](../phase-5-validation-errors/NOTES.md)
<!-- /nav -->

# Phase 4 — Spring Data JPA: Notes

## 4.1 — The one-line repository (the payoff)

In the Java capstone, `JdbcExpenseRepository` was ~120 lines of PreparedStatements, ResultSet mapping, and connection handling. Here:
```java
public interface ExpenseRepository extends JpaRepository<Expense, Long> { }
```
That's it. **Spring Data generates the implementation at startup** (a runtime proxy). `JpaRepository<Entity, IdType>` supplies `save`, `saveAll`, `findById` (→ `Optional`), `findAll`, `existsById`, `deleteById`, `count`, pagination, and more — all free.

**Entities** (Phase 4 recap of Java 7.3): `@Entity`, `@Id` + `@GeneratedValue`, `@Column`, `@Enumerated(STRING)`. An entity **can't be a record** — Hibernate needs a no-arg constructor and mutable fields for reflective instantiation and lazy proxies. That's why our `Expense` entity is a mutable class while the DTOs are records.

## 4.2 — Derived queries, @Query, pagination

**Derived query methods** — Spring parses the *method name* into a query:
- `findByCategory(Category c)` → `WHERE category = ?`
- `findByAmountGreaterThan(BigDecimal a)` → `WHERE amount > ?`
- `findBySpentOnGreaterThanEqualOrderBySpentOnDesc(LocalDate d)` → `WHERE spent_on >= ? ORDER BY spent_on DESC`

Keywords: `And`, `Or`, `Between`, `LessThan`, `GreaterThanEqual`, `Like`, `In`, `IsNull`, `OrderBy...Desc`, `Top`/`First`. The name *is* the query — no SQL, no implementation (our `ExpenseRepositoryTest` proves the generated SQL is correct).

For anything complex: **`@Query("...")`** with JPQL (or `nativeQuery=true` for raw SQL). **Pagination/sorting:** accept a `Pageable`, return a `Page<T>` — Spring adds `LIMIT`/`OFFSET` and a count query automatically.

## 4.3 — Transactions & the persistence layer

**`@Transactional`** wraps a method in a database transaction (the Java Phase 7.2 begin/commit/rollback pattern, now declarative). On the service: each public method runs in a transaction that **commits on success and rolls back on unchecked exceptions**. `@Transactional(readOnly = true)` on queries is an optimization hint (no dirty-checking/flush).

**How it works:** Spring wraps the bean in a **proxy** (AOP) that opens a transaction before the method and commits/rolls back after — which is why `@Transactional` only applies to calls *through* the proxy (a self-invocation inside the same class bypasses it — a classic gotcha).

**Dirty checking** still applies (Java 7.3): mutate a managed entity inside a transaction and Hibernate auto-issues the `UPDATE` at commit — no explicit `save` needed. The **N+1 problem** (Java 7.3) is the top JPA performance trap; fix with `JOIN FETCH`/`@EntityGraph`. `spring.jpa.hibernate.ddl-auto` builds the schema from entities in dev (`update`), but production uses `validate` + Flyway/Liquibase migrations.
