<!-- nav -->
**[🏠 Repo Home](../README.md)**
<!-- /nav -->

# 01 — Java Deep Dive

Goal: go from basics to advanced Java, ready for Spring Boot and full-stack work.
We use **Java 21 (LTS)** — the same version most companies are on or moving to.

## Curriculum

### Phase 1 — Language basics
- [x] 1.1 Hello World, how Java runs (JVM, bytecode, `javac`/`java`)
- [x] 1.2 Variables, primitive types, operators
- [x] 1.3 Control flow: `if`/`else`, `switch` (incl. modern switch expressions), loops
- [x] 1.4 Methods, parameters, overloading
- [x] 1.5 Arrays and Strings

### Phase 2 — Object-oriented programming
- [x] 2.1 Classes, objects, constructors, `this`
- [x] 2.2 Encapsulation: access modifiers, getters/setters
- [x] 2.3 Inheritance and polymorphism, `super`, overriding
- [x] 2.4 Abstract classes and interfaces (incl. default methods)
- [x] 2.5 `Object` methods: `equals`, `hashCode`, `toString`
- [x] 2.6 Enums, records, sealed classes (modern Java)
- [x] 2.7 Nested classes, `static` deep-dive

### Phase 3 — Core APIs & error handling
- [x] 3.1 Exceptions: checked vs unchecked, try-with-resources
- [x] 3.2 Collections framework: `List`, `Set`, `Map`, `Queue` — and when to use which
- [x] 3.3 Generics: type parameters, bounds, wildcards
- [x] 3.4 I/O: files, readers/writers, NIO.2 (`Path`, `Files`)

### Phase 4 — Modern & functional Java
- [x] 4.1 Lambdas and functional interfaces
- [x] 4.2 Streams API: map/filter/reduce, collectors
- [x] 4.3 `Optional` done right
- [x] 4.4 Pattern matching, `var`, text blocks

### Phase 5 — Concurrency
- [x] 5.1 Threads and the memory model basics
- [x] 5.2 `synchronized`, locks, atomic types
- [x] 5.3 Executors and thread pools
- [x] 5.4 `CompletableFuture`
- [x] 5.5 Virtual threads (Java 21's headline feature)

### Phase 6 — Tooling & professional practice
- [x] 6.1 Maven: project structure, dependencies, lifecycle
- [x] 6.2 Unit testing with JUnit 5 + AssertJ
- [x] 6.3 Debugging, logging (SLF4J), Javadoc

### Phase 7 — Data access
- [x] 7.1 SQL essentials + JDBC
- [x] 7.2 Connection pooling, transactions
- [x] 7.3 Intro to JPA/Hibernate (bridge to Spring Boot)

### Capstone
- [x] **Expense Tracker** (`capstone/expense-tracker/`) — layered console app: domain records/enums, `ExpenseRepository` interface + JDBC/HikariCP/H2 implementation, streams-based reporting service, console REPL + scripted demo, 18 JUnit 5 tests (unit against an in-memory fake + integration against real H2). Uses every phase; rebuilt as a REST API in track 02.

## Structure

```
01-java/
├── README.md              <- this curriculum, check off as we go
├── phase-1-basics/
│   ├── NOTES.md           <- this phase's concepts: the "why"
│   ├── INTERVIEW.md       <- this phase's interview Q&A + follow-ups
│   └── lesson-1-1/ ...    <- runnable code per lesson
├── phase-2-oop/
│   ├── NOTES.md
│   ├── INTERVIEW.md
│   └── lesson-2-1/ ...
├── ...
└── capstone/
```

Every phase carries its own **NOTES.md** (the why behind each lesson) and
**INTERVIEW.md** (the theory questions interviewers ask, with follow-ups),
kept next to that phase's runnable code.

Phases 1–5 use plain `javac`/`java` so you see exactly how Java works with no magic.
From Phase 6 on, we switch to Maven like a real project.

<!-- phases-nav -->
## 📂 Phase files

- **Phase 1 · basics** — [Notes](phase-1-basics/NOTES.md) · [Interview](phase-1-basics/INTERVIEW.md)
- **Phase 2 · oop** — [Notes](phase-2-oop/NOTES.md) · [Interview](phase-2-oop/INTERVIEW.md)
- **Phase 3 · core apis** — [Notes](phase-3-core-apis/NOTES.md) · [Interview](phase-3-core-apis/INTERVIEW.md)
- **Phase 4 · modern java** — [Notes](phase-4-modern-java/NOTES.md) · [Interview](phase-4-modern-java/INTERVIEW.md)
- **Phase 5 · concurrency** — [Notes](phase-5-concurrency/NOTES.md) · [Interview](phase-5-concurrency/INTERVIEW.md)
- **Phase 6 · tooling** — [Notes](phase-6-tooling/NOTES.md) · [Interview](phase-6-tooling/INTERVIEW.md)
- **Phase 7 · data** — [Notes](phase-7-data/NOTES.md) · [Interview](phase-7-data/INTERVIEW.md)
<!-- /phases-nav -->
