<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · di](../phase-3-di/NOTES.md) | [Phase 5 · validation errors ➡](../phase-5-validation-errors/NOTES.md)
<!-- /nav -->

# Phase 4 — Spring Data JPA: Notes

## 1. The one-line repository (the payoff)

**Definition:** Spring Data JPA generates a working data-access implementation *at runtime* from a declared interface — no SQL, no boilerplate DAO class, no manual `ResultSet` mapping.

```java
public interface ExpenseRepository extends JpaRepository<Expense, Long> { }
```

That single line is the entire persistence layer for expenses. Compare this to the Java capstone's `JdbcExpenseRepository`, which was roughly 120 lines: opening a `Connection`, building `PreparedStatement`s for every query, manually mapping each `ResultSet` row back into an `Expense` object, and closing resources in `finally` blocks (or try-with-resources). Spring Data eliminates all of that.

**Key concepts:**
- **`JpaRepository<Expense, Long>`** — the two generic parameters are the entity type and the type of its primary key. Extending it supplies, for free: `save(entity)`, `saveAll(iterable)`, `findById(id)` (returns `Optional<Expense>`), `findAll()`, `existsById(id)`, `deleteById(id)`, `delete(entity)`, `count()`, plus pagination and sorting overloads — all without a single implementation line written by you.
- **How it actually works at runtime**: Spring Data doesn't generate a `.java` file you can open. At application startup, it creates a **dynamic proxy** implementing the `ExpenseRepository` interface, backed by a generic implementation (`SimpleJpaRepository`) that knows how to translate calls like `save` and `findById` into JPA `EntityManager` operations. When `ExpenseService`'s constructor asks for an `ExpenseRepository`, this proxy is exactly what gets injected — it looks and behaves like a normal object, but there is no source file defining `ExpenseRepository`'s method bodies anywhere in the project.
- **This is the direct beneficiary of DI (Phase 3)**: because `ExpenseService` depends on the *interface* `ExpenseRepository`, not a concrete class, it has no idea (and doesn't need to know) that the implementation handed to it is a dynamically generated proxy rather than hand-written code.

**Why it's useful:** this is usually the single most persuasive "aha" moment for anyone coming from raw JDBC — an entire CRUD data-access layer collapses to one interface declaration, and the framework still generates correct, tested SQL underneath it.

## 2. JPA entities

**Definition:** An entity is a Java class annotated `@Entity`, mapped by JPA/Hibernate to a database table, where each instance represents one row and each field (by default) represents one column.

```java
@Entity
@Table(name = "expense")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // DB auto-increment
    private Long id;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)  // exact money (NUMERIC)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)                          // store "FOOD", not an ordinal
    @Column(nullable = false, length = 30)
    private Category category;

    @Column(name = "spent_on", nullable = false)
    private LocalDate spentOn;

    protected Expense() { }                               // required by JPA

    public Expense(String description, BigDecimal amount, Category category, LocalDate spentOn) {
        this.description = description;
        this.amount = amount;
        this.category = category;
        this.spentOn = spentOn;
    }
    // getters + setters...
}
```

**Key concepts:**
- **`@Entity`** marks the class as JPA-managed; **`@Table(name = "expense")`** overrides the default table name (which would otherwise be derived from the class name).
- **`@Id` + `@GeneratedValue(strategy = GenerationType.IDENTITY)`** — `@Id` marks the primary-key field; `IDENTITY` delegates key generation to the database's auto-increment column (H2/Postgres/MySQL all support this), so `id` is `null` on a new, unsaved `Expense` and gets populated by Hibernate immediately after the `INSERT`.
- **`@Column`** customizes column mapping: `nullable = false` adds a `NOT NULL` constraint, `precision`/`scale` on the `BigDecimal amount` field ensure the database column is an exact-precision `NUMERIC(12,2)` (never use floating-point types for money — this is the same lesson as using `BigDecimal` over `double` in plain Java), and `name = "spent_on"` maps the Java field `spentOn` to a differently-named column (snake_case database convention vs. camelCase Java convention).
- **`@Enumerated(EnumType.STRING)`** stores the enum's *name* (`"FOOD"`) in the database column rather than its ordinal position (`0`). This is a deliberate, important choice: if `Category`'s declaration order ever changes (someone adds a new value in the middle), ordinal storage would silently corrupt every existing row's meaning, while string storage remains correct and human-readable in the database.
- **A no-arg constructor is required** (`protected Expense() { }`) — Hibernate needs it to instantiate an entity via reflection before populating its fields, and to build lazy-loading proxy subclasses (see below). It's `protected`, not `public`, so application code can't accidentally call it and get a half-empty `Expense` — only JPA's reflection-based instantiation and Hibernate-generated proxy subclasses can use it.
- **Fields must be mutable** — Hibernate populates an entity by calling its no-arg constructor and then setting fields (via reflection or generated accessors), which is fundamentally incompatible with `final` fields set only in a canonical constructor.

**Why an entity can't be a Java `record` (contrast with the Phase 2.3 DTOs):** records are implicitly `final`, have no no-arg constructor, and their fields are `final` and only ever set once, in the canonical constructor. Hibernate's instantiate-then-populate reflection strategy, and its need to generate lazy-loading proxy *subclasses* of the entity (impossible for a `final` class), both require exactly what records refuse to offer. This is precisely why `expense-api`'s DTOs (`CreateExpenseRequest`, `ExpenseResponse`) are records while the entity `Expense` is a plain mutable class — two different jobs, two different shapes.

## 3. Derived query methods

**Definition:** A derived query method is a repository interface method whose name Spring Data parses, token by token, into a working query — you write only the method signature; the SQL/JPQL is generated automatically.

```java
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    // WHERE category = ?
    List<Expense> findByCategory(Category category);

    // WHERE spent_on >= ?  ORDER BY spent_on DESC
    List<Expense> findBySpentOnGreaterThanEqualOrderBySpentOnDesc(LocalDate from);

    // WHERE amount > ?
    List<Expense> findByAmountGreaterThan(BigDecimal threshold);
}
```

`ExpenseRepositoryTest` (a `@DataJpaTest`, Phase 6.1) proves these actually generate correct SQL:
```java
@Test
void derivedQueryFindByCategory() {
    repo.save(new Expense("a", new BigDecimal("10"), Category.FOOD, LocalDate.now()));
    repo.save(new Expense("b", new BigDecimal("20"), Category.FOOD, LocalDate.now()));
    repo.save(new Expense("c", new BigDecimal("30"), Category.RENT, LocalDate.now()));

    assertThat(repo.findByCategory(Category.FOOD)).hasSize(2);   // matches the two FOOD rows
    assertThat(repo.findByCategory(Category.RENT)).hasSize(1);
}
```
In this test, three expenses are saved, two `FOOD` and one `RENT`. Calling `repo.findByCategory(Category.FOOD)` — a method with *no implementation written anywhere in the codebase* — correctly returns exactly the two `FOOD` rows. Spring Data parsed `findByCategory` at startup, recognized `Category` as a property of `Expense`, and generated a `WHERE category = ?` query behind the proxy.

**Key concepts / recognized keywords:**

| Method name fragment | Generated condition |
|---|---|
| `findByX` | `WHERE x = ?` |
| `findByXAndY` | `WHERE x = ? AND y = ?` |
| `findByXOrY` | `WHERE x = ? OR y = ?` |
| `findByXGreaterThan` / `LessThan` | `WHERE x > ?` / `WHERE x < ?` |
| `findByXGreaterThanEqual` | `WHERE x >= ?` |
| `findByXBetween` | `WHERE x BETWEEN ? AND ?` |
| `findByXLike` | `WHERE x LIKE ?` |
| `findByXIn` | `WHERE x IN (?, ?, ...)` |
| `findByXIsNull` | `WHERE x IS NULL` |
| `...OrderByXDesc` / `Asc` | `ORDER BY x DESC` / `ASC` |
| `findFirst3By...` / `findTop3By...` | `LIMIT 3` |
| `existsByX` | returns `boolean` instead of a list |
| `countByX` | returns `long` |

`UserRepository` (used by the security layer, Phase 7) uses this same mechanism: `Optional<AppUser> findByUsername(String username)` and `boolean existsByUsername(String username)` — no SQL, no implementation, just names Spring Data parses against `AppUser`'s fields.

**Why it's useful:** the method name *is* the query specification — readable directly in calling code (`repository.findByCategory(...)` is self-documenting), and because Spring Data validates the property names against the entity at startup, a typo (`findByCatgory`) fails immediately with a clear startup error rather than compiling and failing mysteriously at runtime.

## 4. `@Query` and pagination

**Definition:** For queries too complex to express as a method name, `@Query` lets you write explicit JPQL (or native SQL) directly on a repository method; `Pageable`/`Page<T>` add pagination and sorting to any query without hand-written `LIMIT`/`OFFSET` logic.

```java
// Not present in expense-api today, but the idiomatic escape hatch once a
// derived-query method name would become unreadably long:
@Query("SELECT e FROM Expense e WHERE e.category = :category AND e.amount > :min")
List<Expense> findExpensiveInCategory(@Param("category") Category category,
                                       @Param("min") BigDecimal min);

// Native SQL, when JPQL can't express something DB-specific:
@Query(value = "SELECT * FROM expense WHERE spent_on >= CURRENT_DATE - 7", nativeQuery = true)
List<Expense> lastWeek();
```

**Key concepts:**
- **JPQL** (Java Persistence Query Language) is object-oriented — it queries entity classes and their fields (`Expense`, `e.category`), not raw table/column names, and is portable across different database vendors because Hibernate translates it into vendor-specific SQL.
- **`nativeQuery = true`** switches to raw, database-specific SQL — needed for vendor-specific functions, complex window queries, or performance-tuned hand-written SQL that JPQL can't express, at the cost of portability.
- **Pagination**: any repository method (derived or `@Query`) can accept a `Pageable` parameter and return `Page<T>` instead of `List<T>`; Spring Data automatically adds `LIMIT`/`OFFSET` to the generated query *and* runs a second `COUNT` query, so `Page<T>` carries both the current page's content and the total element/page count.
- **When to reach for `@Query` over a derived method**: once the condition logic is too complex for a readable method name, once you need a projection (returning a DTO shape directly from the query instead of a full entity), or once you need fine control over exactly what SQL runs for performance reasons.

**Why it's useful:** knowing there's a graceful escalation path — derived method name → `@Query` JPQL → `@Query` native SQL — from "simple and declarative" to "full control," without ever dropping to raw JDBC, is what makes Spring Data practical for real-world query complexity, not just toy CRUD.

## 5. `@Transactional` and the persistence layer

**Definition:** `@Transactional` declaratively wraps a method's execution in a database transaction — begin before the method runs, commit if it completes normally, roll back if it throws an unchecked exception — without any explicit `Connection`/`commit()`/`rollback()` code.

```java
@Service
@Transactional                       // class-level: every public method runs in a transaction
public class ExpenseService {

    private final ExpenseRepository repository;

    public ExpenseService(ExpenseRepository repository) {
        this.repository = repository;
    }

    public Expense add(String description, BigDecimal amount, Category category, LocalDate spentOn) {
        return repository.save(new Expense(description, amount, category, spentOn));
    }

    @Transactional(readOnly = true)   // read-only txns are an optimization hint
    public List<Expense> all() {
        return repository.findAll();
    }

    public void delete(long id) {
        if (!repository.existsById(id)) {
            throw new ExpenseNotFoundException(id);   // unchecked -> triggers rollback
        }
        repository.deleteById(id);
    }
}
```

**Key concepts:**
- **How it works — AOP proxying**: Spring never modifies your class's bytecode directly. Instead, it wraps the bean in a **proxy** object at startup (the same dynamic-proxy mechanism underlying Spring AOP generally). Every call that comes in *through the proxy* is intercepted: the proxy opens a transaction, delegates to your real method, then commits or rolls back based on the outcome, before returning control to the caller. This is why `@Transactional` **only takes effect on calls made through the proxy** — a classic gotcha is a method calling another `@Transactional` method on `this` from within the same class (a "self-invocation"); that call bypasses the proxy entirely and runs with no transactional behavior at all, silently.
- **Default rollback rule**: a transaction rolls back automatically on any unchecked exception (`RuntimeException` or `Error`) propagating out of the method — exactly what happens in `delete` above when `ExpenseNotFoundException` (a `RuntimeException`) is thrown. Checked exceptions do **not** trigger a rollback by default; that's overridable per-annotation with `@Transactional(rollbackFor = SomeCheckedException.class)`.
- **`@Transactional(readOnly = true)`** on query methods (`all()`, `byId()`, `recent()`, `totalsByCategory()` in `ExpenseService`) is an optimization hint to Hibernate: it can skip dirty-checking (see below) for that transaction since nothing will be modified, reducing overhead on pure reads.
- **Class-level `@Transactional`** applies the default settings to every public method in the class; individual methods can override with their own `@Transactional` (as `readOnly = true` does here for queries, leaving the mutating methods with the class-level default).

**Dirty checking**: inside an active transaction, Hibernate tracks every change made to a *managed* entity (one loaded from, or saved into, the current persistence context) and automatically issues the corresponding `UPDATE` when the transaction commits — **without an explicit `save()` call**. Fetch an `Expense`, call a setter on it inside a transactional method, and Hibernate detects the change and persists it at commit time on its own.

**The N+1 select problem**: the most common JPA performance trap. Fetching a list of N parent entities (say, N expenses, each with a lazily-loaded association) and then accessing that lazy association on every one of them triggers one query to fetch the parents, plus N additional queries — one per parent — to fetch each one's association, for N+1 total round trips instead of a single well-joined query. `expense-api`'s domain model is simple enough (no associations between entities yet) to avoid this directly, but it's a near-guaranteed topic in any JPA interview. The fix is to eagerly fetch the association in the *same* query, using `JOIN FETCH` in JPQL or Spring Data's `@EntityGraph` annotation, rather than relying on the default lazy load-on-access behavior.

**`FetchType` — LAZY vs. EAGER**: `@OneToMany`/`@ManyToMany` associations default to `LAZY` (loaded only on first access — avoids over-fetching, but is exactly what causes N+1 if accessed in a loop, or throws `LazyInitializationException` if accessed after the owning transaction/session has already closed). `@ManyToOne`/`@OneToOne` default to `EAGER` (loaded immediately with the parent). Best practice in real applications: default every association to `LAZY` explicitly, and fetch eagerly only where a specific query actually needs the associated data, using `JOIN FETCH`/`@EntityGraph`.

**`ddl-auto` and schema management**: `expense-api`'s `application.yml` sets `spring.jpa.hibernate.ddl-auto: update`, which tells Hibernate to inspect the `@Entity` classes and automatically create/alter database tables to match — convenient for local development, since the schema always tracks the code. In production, this is dangerous (Hibernate can make destructive or unexpected schema changes based on entity refactors) — the standard practice is `ddl-auto: validate` (Hibernate checks the entities against the existing schema and fails fast on mismatch, but never modifies it) or `none`, with the actual schema owned by versioned migration tools like Flyway or Liquibase, applied deliberately and reviewably rather than inferred from Java classes.

**The persistence context (first-level cache)**: within a single transaction, Hibernate maintains a per-transaction cache of every entity it has loaded or saved — the *persistence context*. Looking up the same entity twice by id within one transaction returns the exact same Java object instance the second time (no redundant `SELECT`), and it's this same tracked-instance mechanism that powers dirty checking: Hibernate compares an entity's current field values against a snapshot taken when it entered the persistence context, and issues an `UPDATE` for anything that changed, at flush/commit time.

**Why it's useful:** `@Transactional` is the single mechanism that replaces the Java capstone's manual `connection.setAutoCommit(false)` / `commit()` / `rollback()` pattern with one annotation — but understanding *how* it works (an AOP proxy, self-invocation bypassing it, default rollback only on unchecked exceptions) is what separates knowing the annotation exists from being able to debug why a transaction silently didn't roll back in production.

**Summary:**
- `JpaRepository<Entity, Id>` generates a full CRUD implementation at runtime via a dynamic proxy — no hand-written DAO code.
- Entities need a no-arg constructor and mutable fields (Hibernate reflection + lazy proxies) — that's exactly why they can't be records, unlike the DTOs.
- Derived query methods turn a method *name* into a query; `@Query` (JPQL or native) is the escape hatch for anything more complex; `Pageable`/`Page<T>` add pagination for free.
- `@Transactional` wraps a method in a transaction via an AOP proxy — self-invocation bypasses it, and by default only unchecked exceptions trigger rollback.
- Dirty checking auto-persists changes to managed entities at commit, no explicit `save()` needed.
- N+1 is the classic JPA performance bug; fix lazy-loaded collections with `JOIN FETCH`/`@EntityGraph`.
- `ddl-auto: update` is a dev convenience only — production schema is owned by `validate`/`none` plus real migrations.
