<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · fundamentals](../phase-1-fundamentals/NOTES.md) | [Phase 3 · objects ➡](../phase-3-objects/NOTES.md)
<!-- /nav -->

# Phase 2 — Functions & Closures: Interview Q&A

⭐ = asked constantly.

**Q: What is a closure?** ⭐⭐ *the single most-asked JS question*
A function together with the lexical scope it was defined in — it retains access to those outer variables even after the outer function has returned. Enables private state, factories, memoization, and the module pattern. Example: `makeCounter` returning methods that share a hidden `count`.

**Q: Give a real use for closures.**
Data privacy (private variables via the module pattern), function factories (`multiplier(n)`), memoization/caching, `once` wrappers, maintaining state in event handlers and React hooks (`useState` is closures under the hood).

**Q: How is `this` determined in JavaScript?** ⭐⭐
By the call site, not the definition: method call → object before the dot; plain call → undefined (strict) or global; `call`/`apply`/`bind` → explicit; `new` → the new instance; arrow function → inherited lexically. Not fixed to a class instance like Java.

**Q: `call` vs `apply` vs `bind`?** ⭐
All set `this`. `call(thisArg, a, b)` invokes immediately with comma args; `apply(thisArg, [a, b])` invokes immediately with an args array; `bind(thisArg)` returns a new function permanently bound (call it later). Mnemonic: **A**pply = **A**rray.

**Q: Why don't arrow functions have their own `this`?** ⭐
By design they capture `this` lexically from the enclosing scope — solving the "lost this" problem in callbacks (`this.members.map(m => this.name)` works). Corollary: never use an arrow as an object method that needs `this`, or as a constructor (they can't be `new`ed), or where you need `arguments`.

**Q: What is currying?**
Transforming `f(a, b, c)` into a sequence of unary functions `f(a)(b)(c)`, each a closure over prior arguments. Enables partial application/specialization. Distinct from partial application (fixing *some* args of a multi-arg function).

**Q: What's the difference between `pipe` and `compose`?**
Both combine functions; `pipe` applies left-to-right (`reduce`), `compose` right-to-left (`reduceRight`). `pipe(f, g)(x) === g(f(x))`; `compose(f, g)(x) === f(g(x))`.

**Q: What does `bind` return, and does it mutate the original?**
A brand-new function with `this` (and optionally leading args) fixed; the original is unchanged. Re-binding a bound function has no further effect (first bind wins).
