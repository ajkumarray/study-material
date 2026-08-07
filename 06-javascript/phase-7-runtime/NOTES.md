<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · modern](../phase-6-modern/NOTES.md)
<!-- /nav -->

# Phase 7 — Runtime, DOM & Tooling: Notes

## 7.1 — npm & package.json
**npm** = registry + CLI for JS dependencies (the JS world's Maven Central + `mvn`). **package.json** is the manifest (JS's `pom.xml`): `name`/`version`, `type:"module"` (use ESM), `scripts` (named commands — `npm run dev`), `dependencies` (runtime — Maven compile scope) vs `devDependencies` (build/test only — Maven test/provided). Commands: `npm install` (fetch deps into `node_modules/`), `npm install <pkg>`, `npm run <script>`, `npx <tool>` (run a binary without global install). **`package-lock.json`** pins exact resolved versions (reproducible builds — commit it). **`node_modules/`** is downloaded deps — never commit. **Semver ranges:** `^1.2.3` = compatible up to `<2.0`, `~1.2.3` = patches only `<1.3`, exact = `1.2.3`.

## 7.2 — The DOM & events (browser-only — see `dom-demo.html`)
The **DOM** is the browser's live tree of nodes representing the page. First browser JS: a `<script>` acting on the page (no Node, no import). Core skills: **select** (`document.querySelector`/`querySelectorAll` with CSS selectors), **create/modify** (`createElement`, `textContent`, `classList`, `appendChild`, `innerHTML`), **events** (`addEventListener("click", handler)`; the `event` object; keyboard events via `e.key`). The demo is a raw-DOM todo app; its takeaway is *why React exists* — with raw DOM you manually select, create, update, and destroy nodes, and keep UI in sync with state by hand. React lets you describe UI as a function of state and diffs the DOM for you (track 06 rebuilds this exact app in React).

## 7.3 — fetch & APIs
**`fetch(url, options)`** is the modern HTTP client, built into browsers and Node 18+; returns a **promise** (Phase 5), so async/await is natural. **Key gotcha:** fetch only rejects on **network failure**, not on HTTP error status — a 404/500 still resolves, so you must check `res.ok`/`res.status` yourself. Body parsing (`res.json()`, `res.text()`) also returns a promise. **POST/PUT:** pass `{ method, headers: {"Content-Type":"application/json"}, body: JSON.stringify(data) }`. Run independent requests in **parallel** with `Promise.all(ids.map(fetchOne))`. Verified live against a public API: GET, POST, parallel fetch, and 404 handling. This is exactly how the frontend will talk to the Spring Boot backend.
