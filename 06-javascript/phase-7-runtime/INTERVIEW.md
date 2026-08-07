<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · modern](../phase-6-modern/NOTES.md)
<!-- /nav -->

# Phase 7 — Runtime, DOM & Tooling: Interview Q&A

⭐ = asked constantly.

**Q: `dependencies` vs `devDependencies`?** ⭐
`dependencies` ship with/are needed to run the app (frameworks, libraries used at runtime). `devDependencies` are needed only to build/test/lint (bundlers, test runners, TypeScript) and aren't installed in production (`npm install --omit=dev`). Analogous to Maven compile vs test scope.

**Q: What is `package-lock.json` and should you commit it?** ⭐
It pins the exact resolved version of every dependency (and transitive dependency), guaranteeing everyone installs identical trees — reproducible builds. Always commit it. `package.json` declares ranges; the lockfile records what those ranges resolved to.

**Q: What do the semver ranges `^` and `~` mean?**
`^1.2.3` allows compatible updates `>=1.2.3 <2.0.0` (minor + patch). `~1.2.3` allows patch updates `>=1.2.3 <1.3.0`. No prefix = that exact version. Based on MAJOR.MINOR.PATCH where major = breaking changes.

**Q: `npm install` vs `npx`?**
`npm install` downloads packages into `node_modules` (add `-g` for global). `npx` runs a package's binary on demand — downloading temporarily if needed — without a global install (e.g. `npx create-react-app`). Great for one-off tools.

**Q: What is the DOM?** ⭐
The Document Object Model — the browser's in-memory tree representation of the HTML page, exposed to JS as objects you can query and mutate. Changing the DOM changes what's rendered.

**Q: `querySelector` vs `getElementById`?**
`getElementById("x")` is fast and id-only. `querySelector(".cls")`/`querySelectorAll` accept any CSS selector and are more flexible. `querySelectorAll` returns a static NodeList (not a live array — spread it or use `forEach`).

**Q: What is event delegation?** ⭐
Attaching one listener to a common ancestor and using `event.target` to handle events from many children — instead of a listener per child. Benefits: fewer listeners (performance), and it works for dynamically added elements. Relies on event bubbling.

**Q: What is event bubbling vs capturing?**
When an event fires, it travels down from the root to the target (capturing phase) then back up (bubbling phase). Listeners run in the bubbling phase by default; pass `{ capture: true }` for the capturing phase. `stopPropagation()` halts travel; `preventDefault()` cancels the default action.

**Q: Does `fetch` reject on a 404 or 500?** ⭐ *classic gotcha*
No — fetch only rejects on network failures. HTTP error statuses still resolve the promise; you must check `response.ok` (or `response.status`) and throw yourself. This surprises people coming from libraries like axios (which do reject on 4xx/5xx).

**Q: How do you send JSON in a fetch POST?**
`fetch(url, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(data) })`. Set the header so the server parses it as JSON, and stringify the body (fetch doesn't do it automatically).
