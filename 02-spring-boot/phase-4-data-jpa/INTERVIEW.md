<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · di](../phase-3-di/NOTES.md) | [Phase 5 · validation errors ➡](../phase-5-validation-errors/NOTES.md)
<!-- /nav -->

# Phase 4 — Spring Data JPA: Interview Q&A

⭐ = asked constantly.

**Q: What does Spring Data JPA give you over plain JPA/Hibernate?** ⭐⭐
Repositories from interfaces — extend `JpaRepository<Entity, Id>` and CRUD + pagination + sorting are generated at runtime; derived query methods turn method names into queries; `@Query` for custom JPQL/SQL. It eliminates the boilerplate DAO layer (the ~120-line JDBC repository becomes one interface).

**Q: How do derived query methods work?** ⭐
Spring Data parses the method name (`findByCategoryAndAmountGreaterThan`) against the entity's properties and generates the query. Keywords (`And`, `Or`, `Between`, `OrderBy`, `Like`, `In`, `Top`) express conditions and ordering — no implementation written.

**Q: When do you use `@Query`?**
When the query is too complex for a derived method, needs a projection/DTO, or needs performance tuning. JPQL by default (object-oriented, portable); `nativeQuery=true` for database-specific SQL.

**Q: What does `@Transactional` do and how?** ⭐⭐
Declaratively wraps a method in a transaction — commit on success, rollback on unchecked exceptions. Spring implements it with an AOP **proxy** that begins/commits/rolls back around the call. Gotchas: it only works on calls *through the proxy* (self-invocation within the same class is not intercepted), and by default it rolls back on `RuntimeException`/`Error` but not checked exceptions (configurable via `rollbackFor`).

**Q: What is the N+1 select problem and how do you fix it?** ⭐⭐
Loading N parent rows then triggering one query per parent for a lazy association → N+1 round trips. Fix with `JOIN FETCH` in JPQL, `@EntityGraph`, or batch fetching. Detect it by watching the SQL log explode. (Covered in Java Phase 7 too — a favorite because it separates people who've run JPA in production from those who haven't.)

**Q: Why can't a JPA entity be a Java record?** ⭐
Hibernate requires a no-arg constructor and mutable fields to instantiate-then-populate via reflection and to create lazy-loading proxies (by subclassing). Records are final and immutable. Use records for DTOs/projections around the entity layer, not for entities.

**Q: FetchType LAZY vs EAGER?**
LAZY (default for collections/`@OneToMany`) loads the association on first access — avoids over-fetching but risks `LazyInitializationException` outside a session and N+1. EAGER (default for `@ManyToOne`) loads with the parent. Best practice: default everything LAZY, fetch explicitly where needed.

**Q: `ddl-auto` settings — and production practice?**
`create`/`create-drop`/`update` build the schema from entities (dev/test convenience); `validate` checks entities against an existing schema; `none` disables it. Production uses `validate` (or `none`) with versioned migrations (Flyway/Liquibase) owning the schema — never `update` against a real database.

**Q: What is the persistence context / first-level cache?**
A per-transaction cache of managed entities. Within a transaction, the same entity is returned for repeated lookups, and changes are tracked (dirty checking) and flushed at commit — so mutating a managed entity updates the DB without an explicit `save`.
