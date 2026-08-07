<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · solid ➡](../phase-2-solid/NOTES.md)
<!-- /nav -->

# Phase 1 — Clean Code: Interview Q&A

⭐ = asked constantly.

**Q: What does "clean code" mean to you?** ⭐
Code optimized for reading and changing: meaningful names, small single-purpose functions, no duplication, shallow nesting, and obvious flow — producing the same behavior but far cheaper to maintain and safer to modify. You read code far more than you write it, so readability is the priority.

**Q: What makes a good function?** ⭐
It does one thing at one level of abstraction, is small, has few parameters (0–3), a descriptive name, no hidden side effects, and reads like a sentence. If you can't name it clearly or it has "and" in its purpose, split it.

**Q: DRY, KISS, YAGNI — explain each.** ⭐⭐
DRY: don't duplicate knowledge — one authoritative source, so changes happen in one place. KISS: keep it simple — the simplest solution that works. YAGNI: don't build for imagined future needs — add flexibility when a real requirement appears. Together they fight over-engineering and drift.

**Q: When are comments good vs bad?**
Bad: restating what the code already says (noise that rots as code changes). Good: explaining *why* — non-obvious rationale, trade-offs, warnings, links to a spec/bug. Best: code so clear it needs no comment. A comment excusing bad code is a signal to fix the code.

**Q: What is the Law of Demeter?** ⭐
"Only talk to your immediate friends": a method should call methods on itself, its parameters, objects it creates, and its own fields — not reach through chains (`a.getB().getC().getD()`). Chains couple you to the whole object graph; expose a method that answers the question instead. Reduces coupling.

**Q: What is Command-Query Separation?**
A method should either perform an action (command — returns void, has side effects) or answer a question (query — returns a value, no side effects), not both. It makes queries safe to call freely and code easier to reason about.

**Q: How should you handle nulls and errors?** ⭐
Prefer `Optional` or exceptions over returning null (null is error-prone and invisible in signatures). Fail fast, validate input early with guard clauses, throw meaningful typed exceptions with context, never swallow exceptions silently, and translate exceptions at layer boundaries. Keep error handling separate from the happy path.

**Q: What is cohesion vs coupling?** ⭐
Cohesion: how focused a module is (high = its parts serve one purpose). Coupling: how dependent modules are on each other (low = they interact through minimal, stable interfaces). Aim for high cohesion + low coupling — the essence of maintainable design, which SOLID promotes.

**Q: How do you keep a codebase clean over time?**
The Boy Scout Rule (leave code cleaner than you found it), continuous small refactoring backed by tests, code review, consistent style (automated formatters/linters), and treating readability as a first-class requirement. Cleanliness is maintained by habit, not one big cleanup.

**Q: Isn't clean code just subjective/style?** *nuance*
Formatting is partly convention (pick one, automate it), but the core — clear names, small functions, low coupling, no duplication, obvious flow — objectively reduces defects and change cost, and correlates with testability. The goal (readability and changeability) is concrete even where specific choices vary.
