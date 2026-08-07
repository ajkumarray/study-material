<!-- nav -->
**[🏠 Repo Home](../README.md)**
<!-- /nav -->

# 05 — Software Design & Best Practices

The skills that separate "code that works" from "code that lasts" — and some
of the most heavily interviewed topics for mid/senior roles. Clean code, the
SOLID principles, the Gang-of-Four design patterns, architectural patterns,
and refactoring discipline.

Taught with **Java examples** (you know Java deeply — track 01), and
cross-referenced to patterns you *already used* in the Java capstone: the
repository interface was **Dependency Inversion**, the payment-flow skeleton
was the **Template Method** pattern, lambdas passed as behavior were
**Strategy**, and the layered app was an **architectural pattern**. This track
names, formalizes, and expands what you've been doing by instinct.

Same format as the other tracks: runnable code per lesson, plus phase-wise
`NOTES.md` (the "why") and `INTERVIEW.md` (Q&A with follow-ups). ⭐ = commonly
asked in interviews.

## Curriculum

### Phase 1 — Clean code fundamentals ✅
- [x] 1.1 Meaningful names; functions (small, single-purpose); comments that earn their place
- [x] 1.2 DRY, KISS, YAGNI; the Boy Scout Rule; magic numbers & guard clauses
- [x] 1.3 Cohesion & coupling; Law of Demeter; command-query separation
- [x] 1.4 Error handling as design; fail-fast; nulls & Optional

### Phase 2 — The SOLID principles ✅
- [x] 2.1 **S**ingle Responsibility — one reason to change (`S_SingleResponsibility.java`)
- [x] 2.2 **O**pen/Closed — open to extension, closed to modification (`O_OpenClosed.java`)
- [x] 2.3 **L**iskov Substitution — subtypes must honor the base contract (`L_LiskovSubstitution.java`)
- [x] 2.4 **I**nterface Segregation — many small interfaces over one fat one (`I_InterfaceSegregation.java`)
- [x] 2.5 **D**ependency Inversion — depend on abstractions, not concretions (`D_DependencyInversion.java`)

### Phase 3 — Creational design patterns ✅
- [x] 3.1 Factory Method & Abstract Factory
- [x] 3.2 Builder (and why records/Lombok relate)
- [x] 3.3 Singleton (done right; the enum trick) & Prototype
      *(`CreationalPatterns.java` — all runnable, assertion-tested)*

### Phase 4 — Structural design patterns ✅
- [x] 4.1 Adapter & Facade
- [x] 4.2 Decorator (and how `java.io` streams use it)
- [x] 4.3 Proxy (and how Spring AOP uses it) & Composite
- [x] 4.4 Bridge & Flyweight (in NOTES)
      *(`StructuralPatterns.java` — all runnable, assertion-tested)*

### Phase 5 — Behavioral design patterns ✅
- [x] 5.1 Strategy & Template Method (you used both already)
- [x] 5.2 Observer (and reactive/event systems) & Command
- [x] 5.3 State, Chain of Responsibility, Mediator
- [x] 5.4 Iterator, Visitor; when NOT to use a pattern
      *(`BehavioralPatterns.java` — all runnable, assertion-tested)*

### Phase 6 — Architectural patterns & principles ✅
- [x] 6.1 Layered architecture; separation of concerns
- [x] 6.2 MVC / MVP / MVVM
- [x] 6.3 Hexagonal (ports & adapters); Dependency Injection & IoC (bridge to Spring)
- [x] 6.4 Composition over inheritance; program to an interface

### Phase 7 — Refactoring, code smells & testing discipline ✅
- [x] 7.1 Code smells catalogue (long method, god class, feature envy, ...)
- [x] 7.2 Refactoring techniques (extract method/class, replace conditional with polymorphism)
- [x] 7.3 TDD & the testing pyramid; testing for design feedback
- [x] 7.4 Pragmatic principles: technical debt, code review, when to break the rules

### Capstone ✅
- [x] Take a deliberately bad "god class" and refactor it step-by-step applying
      SOLID + patterns, with tests guarding each move — then write up which
      smell each change removed and which principle/pattern it applied.

## Structure

```
05-software-design/
├── README.md
├── phase-1-clean-code/
│   ├── NOTES.md
│   ├── INTERVIEW.md
│   └── *.java        <- before/after examples you can compile & run
└── ...
```

## Note on overlap

Patterns already demonstrated in other tracks are cross-referenced rather than
repeated: DIP/Template Method/Strategy (Java capstone & OOP phases), DI &
Proxy (Spring Boot track), composition-over-inheritance (Java Phase 2). This
track is the *systematic* treatment — naming and connecting what's scattered
across the codebase into one mental model.

<!-- phases-nav -->
## 📂 Phase files

- **Phase 1 · clean code** — [Notes](phase-1-clean-code/NOTES.md) · [Interview](phase-1-clean-code/INTERVIEW.md)
- **Phase 2 · solid** — [Notes](phase-2-solid/NOTES.md) · [Interview](phase-2-solid/INTERVIEW.md)
- **Phase 3 · creational** — [Notes](phase-3-creational/NOTES.md) · [Interview](phase-3-creational/INTERVIEW.md)
- **Phase 4 · structural** — [Notes](phase-4-structural/NOTES.md) · [Interview](phase-4-structural/INTERVIEW.md)
- **Phase 5 · behavioral** — [Notes](phase-5-behavioral/NOTES.md) · [Interview](phase-5-behavioral/INTERVIEW.md)
- **Phase 6 · architecture** — [Notes](phase-6-architecture/NOTES.md) · [Interview](phase-6-architecture/INTERVIEW.md)
- **Phase 7 · refactoring** — [Notes](phase-7-refactoring/NOTES.md) · [Interview](phase-7-refactoring/INTERVIEW.md)
<!-- /phases-nav -->
