<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · architecture](../phase-6-architecture/NOTES.md)
<!-- /nav -->

# Phase 7 — Refactoring & Code Smells: Interview Q&A

⭐ = asked constantly.

**Q: What is refactoring?** ⭐⭐
Changing code's internal structure without changing its external behavior, to improve readability, reduce complexity, and ease future change. It's done in small, safe, test-backed steps — not a rewrite. The goal is a better design for the same functionality.

**Q: What is a code smell? Give examples.** ⭐⭐
A surface symptom of a deeper design problem (not a bug). Examples: long method, god/large class, duplicated code, long parameter list, feature envy, switch-on-type, primitive obsession, shotgun surgery, anemic domain, deep nesting. Smells tell you *where* to consider refactoring.

**Q: How do you refactor a long method / god class?**
Long method → Extract Method (pull named sub-steps out). God class → Extract Class (split by responsibility toward SRP), Move Method/Field to where the data lives. Do it in small steps, running tests after each, so behavior is provably preserved.

**Q: What is "replace conditional with polymorphism"?** ⭐
Turning a type-switch (`switch(type){...}`) into a set of subclasses/strategies, one per case, dispatched polymorphically. Adding a new type then means adding a class instead of editing the conditional (Open/Closed). It removes the switch-on-type smell.

**Q: How do you refactor safely?** ⭐⭐
Have tests first (write them if missing), make one small structural change, run the tests (must stay green), commit, repeat. Never mix refactoring with feature changes — if a test breaks you won't know which caused it. Tests are the safety net that makes refactoring not a gamble.

**Q: What is TDD and how does it help design?** ⭐
Test-Driven Development: Red (write a failing test) → Green (make it pass) → Refactor (clean up). It drives small, testable, well-factored units, documents behavior, and provides the refactoring safety net continuously. Writing tests first surfaces design problems early (hard-to-test code is usually poorly designed).

**Q: Why is untestable code a design smell?**
Difficulty testing usually signals tight coupling, hidden dependencies, or too many responsibilities — the same problems that make code hard to change. Improving testability (injecting dependencies, splitting responsibilities) improves the design. So tests are both a safety net and a design pressure.

**Q: What is technical debt and how do you manage it?** ⭐
The implied future cost of shortcuts (deliberate trade-offs or accidental cruft). Like financial debt, some is strategic, but unpaid interest — slower changes, more bugs — compounds. Manage it by making it visible (tracking), paying it down via continuous refactoring (Boy Scout Rule), and weighing debt consciously against delivery pressure.

**Q: When should you NOT apply a principle or pattern?** *nuance*
When it adds complexity without relieving real pain — a needless abstraction, a factory for a single implementation, DRY-ing coincidentally-similar code. SOLID/patterns serve readability and changeability; over-applying them creates its own debt. Keep it simple (KISS/YAGNI) until complexity is justified. Judgment over dogma.

**Q: What do you look for in a code review?**
Correctness, but also readability, design (SRP, coupling, smells), test coverage, naming, error handling, and consistency — plus security and edge cases. Give specific, kind, actionable feedback; focus on the code, not the person; and use it to spread knowledge and standards across the team.
