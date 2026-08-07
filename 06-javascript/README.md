<!-- nav -->
**[🏠 Repo Home](../README.md)**
<!-- /nav -->

# 06 — JavaScript Deep Dive

The frontend track's foundation. Everything else on this side — TypeScript,
React, Next.js, Angular — is JavaScript underneath, so we master it first.

Taught **for someone who already knows Java** (track 01): each lesson leans on
Java contrasts, because the two languages agree on syntax and disagree on
almost everything underneath. Same format as the Java track — runnable code
per lesson, plus phase-wise `NOTES.md` and `INTERVIEW.md`.

Runtime: **Node.js 22** (we run `.mjs`/`.js` files directly with `node`; the
browser DOM comes in Phase 7).

## Curriculum

### Phase 1 — Language fundamentals ✅
- [x] 1.1 Values & types; dynamic typing; `typeof` (vs Java's static types)
- [x] 1.2 Variables: `var` / `let` / `const`, scope, hoisting, the TDZ
- [x] 1.3 Operators & coercion: `==` vs `===`, truthiness, the famous gotchas
- [x] 1.4 Control flow & functions: declarations vs expressions, default/rest params

### Phase 2 — Functions, scope & closures (JS's heart) ✅
- [x] 2.1 First-class functions, higher-order functions, callbacks
- [x] 2.2 Closures — the concept interviewers probe most
- [x] 2.3 `this`, call/apply/bind, arrow-function `this`
- [x] 2.4 Recursion, IIFEs, currying, composition

### Phase 3 — Objects & prototypes ✅
- [x] 3.1 Objects: literals, property descriptors, getters/setters
- [x] 3.2 The prototype chain (vs Java class inheritance)
- [x] 3.3 `class` syntax, inheritance, `#private`, static
- [x] 3.4 `Object` toolbox: keys/values/entries, freeze, spread, destructuring

### Phase 4 — Arrays & collections ✅
- [x] 4.1 Array methods: map/filter/reduce (Streams déjà vu), find, some/every
- [x] 4.2 Destructuring, spread/rest, iteration protocols
- [x] 4.3 `Map`, `Set`, `WeakMap`; JSON

### Phase 5 — Asynchronous JavaScript (the big one) ✅
- [x] 5.1 The event loop, call stack, task/microtask queues
- [x] 5.2 Callbacks & callback hell
- [x] 5.3 Promises: then/catch/finally, combinators
- [x] 5.4 async/await (vs Java's CompletableFuture / virtual threads)

### Phase 6 — Modern JS & modules ✅
- [x] 6.1 ES modules: import/export (vs CommonJS)
- [x] 6.2 Iterators & generators
- [x] 6.3 Optional chaining, nullish coalescing, modern operators
- [x] 6.4 Error handling, `try/catch`, custom errors

### Phase 7 — Runtime, DOM & tooling ✅
- [x] 7.1 npm, package.json, the module ecosystem
- [x] 7.2 The DOM & events (first browser code — `dom-demo.html`)
- [x] 7.3 `fetch` & working with APIs

### Capstone
- [ ] A small browser app (e.g., the expense tracker's frontend) talking to a
      real API — which becomes the client for the Spring Boot backend later.
      *(Recommended once React is learned — track 06 — rather than raw DOM.)*

## Structure

```
06-javascript/
├── README.md
├── phase-1-fundamentals/
│   ├── NOTES.md         <- concepts, with Java contrasts
│   ├── INTERVIEW.md     <- JS interview Q&A + follow-ups
│   └── lesson-1-1/ ...
└── ...
```

<!-- phases-nav -->
## 📂 Phase files

- **Phase 1 · fundamentals** — [Notes](phase-1-fundamentals/NOTES.md) · [Interview](phase-1-fundamentals/INTERVIEW.md)
- **Phase 2 · functions** — [Notes](phase-2-functions/NOTES.md) · [Interview](phase-2-functions/INTERVIEW.md)
- **Phase 3 · objects** — [Notes](phase-3-objects/NOTES.md) · [Interview](phase-3-objects/INTERVIEW.md)
- **Phase 4 · arrays** — [Notes](phase-4-arrays/NOTES.md) · [Interview](phase-4-arrays/INTERVIEW.md)
- **Phase 5 · async** — [Notes](phase-5-async/NOTES.md) · [Interview](phase-5-async/INTERVIEW.md)
- **Phase 6 · modern** — [Notes](phase-6-modern/NOTES.md) · [Interview](phase-6-modern/INTERVIEW.md)
- **Phase 7 · runtime** — [Notes](phase-7-runtime/NOTES.md) · [Interview](phase-7-runtime/INTERVIEW.md)
<!-- /phases-nav -->
