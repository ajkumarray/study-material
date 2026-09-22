<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · modern](../phase-6-modern/NOTES.md)
<!-- /nav -->

# Phase 7 — Runtime, DOM & Tooling: Interview Q&A

⭐ = asked constantly.

## npm & `package.json`

**Q: `dependencies` vs `devDependencies`?** ⭐

`dependencies` are packages the application actually needs **at runtime** — frameworks, libraries imported by the shipped code (React, Express, date-handling libraries) — the equivalent of Maven's compile scope. `devDependencies` are needed only during **development, building, or testing** — bundlers (Webpack/Vite), test runners (Jest/Vitest), linters, TypeScript itself — and are typically excluded from a production install with `npm install --omit=dev`, keeping deploy artifacts leaner. Mixing the two up (putting a bundler in `dependencies`) doesn't break anything functionally, but it bloats production installs unnecessarily.

**Q: What is `package-lock.json`, and should you commit it?** ⭐

It records the **exact, resolved** version of every dependency and transitive dependency actually installed — not just the semver *range* declared in `package.json`, but precisely which version within that range was picked. Committing it guarantees that every developer (and CI) running `npm install` gets an identical `node_modules/` tree, eliminating "works on my machine" bugs caused by two people resolving a loose `^1.2.3` range to two different actual versions weeks apart. Always commit it.

**Q: What do the semver range prefixes `^` and `~` mean?**

Given `MAJOR.MINOR.PATCH`: `^1.2.3` allows any version considered backward-compatible — effectively `>=1.2.3 <2.0.0` (new minor and patch releases, but never a major/breaking one). `~1.2.3` is stricter, allowing only patch-level updates — `>=1.2.3 <1.3.0`. No prefix at all (`1.2.3`) pins that exact version with no automatic updates whatsoever.

**Q: `npm install` vs `npx` — what's the difference?**

`npm install <pkg>` downloads a package into the local `node_modules/` folder (optionally `-g` for a global, machine-wide install) and records it as a dependency in `package.json`. `npx <tool>` **runs** a package's executable directly — downloading it temporarily if it isn't already installed — without leaving behind any permanent global install. `npx` is the standard way to run one-off scaffolding/CLI tools (`npx create-react-app my-app`) without cluttering the global package list.

---

## The DOM & Events

**Q: What is the DOM?** ⭐

The Document Object Model — the browser's live, in-memory tree representation of an HTML page, where every tag becomes a node object exposed to JavaScript. Reading or mutating that tree via JS (`document.querySelector`, `.textContent`, `.appendChild`, etc.) immediately changes what the user actually sees rendered on the page — the DOM tree *is* the rendered page, not a separate copy of it.

**Q: `querySelector` vs `getElementById`?**

`getElementById("x")` looks up a single element by its `id` attribute only — narrow, but historically the fastest option. `querySelector(".cls")` / `querySelectorAll(".cls")` accept **any** valid CSS selector (classes, attribute selectors, descendant combinators, pseudo-classes), making them far more flexible and the standard modern choice. One gotcha: `querySelectorAll` returns a **static `NodeList`**, not a live array and not automatically iterable with array methods like `.map()` — spread it (`[...list]`) or use its own `.forEach` to work with it array-style.

**Q: What is event delegation, and why use it instead of a listener per element?** ⭐

Event delegation means attaching a **single** event listener to a common ancestor element and using `event.target` inside that one handler to figure out which specific descendant actually triggered the event — instead of attaching a separate listener to every individual child. Benefits: far fewer total listeners (better memory/performance for long lists), and it automatically works for elements added to the DOM **after** the listener was attached, since the listener lives on a stable parent rather than on each (possibly not-yet-created) child. It relies entirely on event bubbling — a click on a child bubbles up to the ancestor where the delegated listener is waiting.

**Q: Explain event bubbling vs. capturing.**

When an event fires on an element, it doesn't just run handlers on that element — it travels through the DOM tree in two phases. First the **capturing** phase: the event travels *down* from the document root toward the target element. Then the **bubbling** phase: it travels back *up* from the target toward the root. By default, `addEventListener` registers a handler for the bubbling phase; passing `{ capture: true }` as a third argument registers it for the capturing phase instead. `event.stopPropagation()` halts the event's further travel through either phase; `event.preventDefault()` is unrelated to travel — it cancels the browser's default action for that event (like a link navigating, or a form submitting and reloading the page).

---

## `fetch` & APIs

**Q: Does `fetch` reject its promise on a 404 or 500 response?** ⭐ *classic gotcha*

No. `fetch`'s returned promise only rejects on a genuine **network failure** — DNS resolution failure, connection refused, the client being offline. An HTTP response with an error status code, like `404 Not Found` or `500 Internal Server Error`, still counts as a "successful" network round trip as far as `fetch` is concerned, so the promise **resolves** normally, just with `response.ok === false` and `response.status` set to the error code. You have to check `response.ok` (or `response.status`) yourself and explicitly `throw` if it's not acceptable — this surprises people coming from libraries like axios, which do reject automatically on 4xx/5xx responses.

**Q: How do you send a JSON body in a `fetch` POST request?**

```js
fetch(url, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(data),
});
```
Two things are required, and `fetch` does neither automatically: the `Content-Type: application/json` header (so the receiving server knows to parse the body as JSON), and `JSON.stringify(data)` to actually convert the JS object into JSON text — passing a raw object as `body` would send something like `"[object Object]"` instead of real JSON.

**Q: Why does reading a `fetch` response body require a second `await`?**

`fetch(...)`'s promise resolves as soon as the HTTP response's status and headers have arrived — the response *body* may still be streaming in over the network at that point. `res.json()` (or `res.text()`, `res.blob()`, etc.) reads and parses that body, and since the body might not be fully received yet, that operation is itself asynchronous and returns its own promise, requiring its own `await`: `const res = await fetch(url); const data = await res.json();`.

**Q: How would you fetch several independent resources efficiently, given a list of ids?**

```js
const posts = await Promise.all(ids.map(id => getPost(id)));
```
Mapping the array of ids into an array of `fetch`-returning promises starts every request essentially simultaneously; `Promise.all` then waits for all of them to complete, so the total time is roughly the duration of the *slowest* single request, not the sum of every request's duration — the same sequential-vs-parallel principle from Phase 5, applied directly to real network I/O. Looping with `await getPost(id)` inside a `for` loop instead would serialize the requests needlessly.

**Q: What's the standard error-handling pattern for a `fetch`-based API call?**

Explicitly check `response.ok` right after `fetch` resolves and `throw` a real `Error` (ideally including `response.status`) if the request wasn't successful — then let that thrown error propagate to a surrounding `try`/`catch` (or a `.catch()` on the promise chain):
```js
async function getPost(id) {
  const res = await fetch(`.../posts/${id}`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}
```
This is necessary precisely because `fetch` won't do it for you — without the explicit `if (!res.ok) throw`, an HTTP error response would silently be treated as a success, and calling code would try to use error-page content as if it were valid data.
