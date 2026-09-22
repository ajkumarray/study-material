<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · modern](../phase-6-modern/NOTES.md)
<!-- /nav -->

# Phase 7 — Runtime, DOM & Tooling: Notes

## 1. npm & `package.json`

**npm** (Node Package Manager) is both the CLI tool and the public registry that the JavaScript ecosystem uses to share and install code — roughly the equivalent of Maven Central plus the `mvn` command in the Java world. Every npm project is described by a **`package.json`** manifest, the JS analog of a `pom.xml`.

### Key Concepts

- **`package.json`**: the project's manifest — identity (`name`/`version`), module system (`"type": "module"` for ESM), `scripts` (named, runnable commands), and two dependency lists.
- **`dependencies`**: packages the app needs **at runtime** — frameworks, libraries the shipped code actually imports (Maven's compile scope).
- **`devDependencies`**: packages needed only to **build/test/lint** — bundlers, test runners, TypeScript itself — never installed in a production-only install (Maven's test/provided scope).
- **`package-lock.json`**: pins the exact, resolved version of every dependency (including transitive ones), guaranteeing everyone who runs `npm install` gets an identical dependency tree.
- **`node_modules/`**: where installed dependencies actually live on disk — always `.gitignore`d, never committed, since it's fully reproducible from `package.json` + the lockfile.
- **Semantic versioning (semver)**: `MAJOR.MINOR.PATCH`, where major = breaking changes, minor = backward-compatible features, patch = backward-compatible fixes.

### Worked Example: a minimal `package.json`

```json
{
  "name": "js-phase-7-demo",
  "version": "1.0.0",
  "type": "module",
  "scripts": {
    "start": "node npm-and-fetch.mjs",
    "demo": "node npm-and-fetch.mjs"
  },
  "dependencies": {},
  "devDependencies": {}
}
```

`"type": "module"` tells Node to treat every `.js` file in this project as an ES module (supporting `import`/`export` directly, Phase 6) rather than the legacy CommonJS default. `"scripts"` defines named shortcuts — `npm run demo` runs exactly `node npm-and-fetch.mjs`, without needing to remember or type the full command.

### Worked Example: the core npm commands

```bash
npm install              # read package.json, fetch every dependency into node_modules/
npm install lodash       # add a new runtime dependency (updates package.json + lockfile)
npm install -D vitest    # add a new dev-only dependency
npm run demo             # run the "demo" script from package.json
npx create-react-app app # run a package's binary WITHOUT installing it globally first
```

`npm install <pkg>` both downloads the package and records it in `package.json`'s `dependencies` (or `devDependencies` with `-D`/`--save-dev`). `npx` is specifically for **running** a package's executable on demand — useful for one-off scaffolding tools you don't want permanently installed.

### Comparison Table: semver range prefixes

| Range | Meaning | Example allows |
|---|---|---|
| `1.2.3` (no prefix) | Exact version only | `1.2.3` |
| `~1.2.3` | Patch-level changes only | `>=1.2.3 <1.3.0` |
| `^1.2.3` | Compatible changes (minor + patch) | `>=1.2.3 <2.0.0` |

### Why It's Useful

Understanding `dependencies` vs. `devDependencies` determines what actually ships to production (`npm install --omit=dev` skips dev-only tooling entirely, keeping deploy artifacts smaller). Committing `package-lock.json` is what prevents "works on my machine" bugs caused by two developers (or a developer and a CI server) silently resolving different transitive dependency versions from the same loosely-specified `^`/`~` ranges in `package.json`.

### Summary

- `package.json` is the project manifest; `package-lock.json` pins exact versions for reproducible installs — always commit both, never commit `node_modules/`.
- `dependencies` ship with the app; `devDependencies` are build/test-only tooling.
- `npm install <pkg>` adds and downloads a dependency; `npx <tool>` runs a package binary without a permanent global install.
- Semver: `^` allows minor+patch updates, `~` allows patch-only updates, no prefix pins exactly.

---

## 2. The DOM: Selecting, Creating & Modifying Elements

The **DOM** (Document Object Model) is the browser's live, in-memory tree representation of an HTML page — every tag becomes a node object that JavaScript can read and mutate, and any mutation is reflected immediately in what the user sees on screen. This is JavaScript's original job (before Node.js existed): a `<script>` tag running directly against a live page, with no imports, no build step.

### Key Concepts

- **Selecting elements**: `document.querySelector(selector)` returns the first element matching a CSS selector; `document.querySelectorAll(selector)` returns all matches (as a static `NodeList`, not a live array — spread it or use `.forEach` to work with it like an array).
- **`getElementById(id)`**: a narrower, slightly faster selector for the common "find by id" case.
- **Creating elements**: `document.createElement(tagName)` builds a new, detached element node — it exists in memory but isn't part of the visible page until attached.
- **Modifying content**: `.textContent` sets plain text safely (auto-escaped); `.innerHTML` sets raw HTML (powerful, but a real XSS risk with untrusted input); `.classList.add/remove/toggle` manages CSS classes.
- **Attaching to the page**: `.appendChild(node)` (or the parent's `.append(...)`) inserts a node into the visible tree.

### Worked Example: selecting and building a list from state

```js
const input = document.querySelector("#task");
const list = document.querySelector("#list");
const count = document.querySelector("#count");

let todos = [];   // application state — nothing to do with the DOM yet

function render() {
  list.innerHTML = "";                      // wipe the old DOM content
  for (const todo of todos) {
    const li = document.createElement("li");
    li.textContent = todo.text;
    if (todo.done) li.classList.add("done");
    list.appendChild(li);
  }
  const remaining = todos.filter(t => !t.done).length;
  count.textContent = `${remaining} of ${todos.length} remaining`;
}
```

`render()` is a manual "sync the DOM to match `todos`" function: it wipes everything inside `#list`, then rebuilds one `<li>` per todo from scratch, setting text and a CSS class based on that todo's state. Every time `todos` changes, something has to remember to call `render()` again — nothing does this automatically.

### Comparison Table: `querySelector` vs `getElementById`

| | `getElementById(id)` | `querySelector(selector)` / `querySelectorAll(selector)` |
|---|---|---|
| Selector syntax | Bare id string only | Any CSS selector (`.class`, `#id`, `div > p`, `[data-x]`, ...) |
| Returns | A single element or `null` | First match (`querySelector`) or a static `NodeList` (`querySelectorAll`) |
| Flexibility | Narrow, one use case | General-purpose |
| Typical choice | Rarely used directly anymore | The default modern choice |

### Why It's Useful

Understanding raw DOM manipulation is exactly what makes it clear *why* React (or any UI framework) exists: with raw DOM, you're responsible for manually selecting, creating, updating, and destroying nodes every single time application state changes, and keeping the visible page in sync with that state is entirely your job. React's model — describe the UI as a function of state, and let the framework diff and patch the real DOM — is a direct abstraction over exactly this manual `render()` pattern.

### Summary

- The DOM is the browser's live object tree for an HTML page; mutating it changes what's rendered.
- `querySelector`/`querySelectorAll` (CSS selectors) are the modern, general-purpose way to find elements.
- `createElement` + `appendChild` build and attach new nodes; `textContent`/`classList` update existing ones.
- Manually keeping the DOM in sync with application state (a hand-written `render()` function) is exactly the problem frameworks like React solve.

---

## 3. DOM Events

Events are how a web page responds to user interaction (clicks, key presses, form submissions) and other occurrences (page load, network state changes). `addEventListener` is the standard, modern way to react to them.

### Key Concepts

- **`element.addEventListener(type, handler)`**: registers `handler` to run whenever an event of `type` (e.g. `"click"`, `"keydown"`) fires on `element`.
- **The event object**: every handler receives an `Event` object describing what happened — `e.target` (the actual element the event originated on), `e.key` (for keyboard events), `e.preventDefault()` (cancels the default browser action, like following a link), `e.stopPropagation()` (halts further event travel).
- **Bubbling vs. capturing**: an event travels down from the document root to the target element first (**capturing** phase), then back up from the target to the root (**bubbling** phase). Listeners run during the bubbling phase by default; pass `{ capture: true }` to run during capturing instead.
- **Event delegation**: attaching one listener to a common ancestor, using `e.target` to identify which specific descendant triggered it — instead of attaching a separate listener to every individual child.

### Worked Example: wiring up click and keyboard events

```js
document.querySelector("#add").addEventListener("click", () => {
  const text = input.value.trim();
  if (!text) return;
  todos.push({ text, done: false });
  input.value = "";
  render();
});

input.addEventListener("keydown", (e) => {
  if (e.key === "Enter") document.querySelector("#add").click();
});
```

The button's `click` listener reads the current input value, updates the `todos` array, clears the input, and re-renders. The input's `keydown` listener checks `e.key` to detect the Enter key specifically, then simulates a click on the Add button — a common pattern for making a form submittable via both mouse and keyboard.

### Worked Example: a listener per element, and its cost

```js
function render() {
  list.innerHTML = "";
  for (const todo of todos) {
    const li = document.createElement("li");
    li.textContent = todo.text;
    if (todo.done) li.classList.add("done");
    li.addEventListener("click", () => {
      todo.done = !todo.done;   // mutate state
      render();                 // re-render — the manual "React loop"
    });
    list.appendChild(li);
  }
}
```

Every `<li>` gets its own `click` listener here — correct, but it means every re-render throws away old listeners (along with the old elements) and attaches brand-new ones. For a small todo list this is fine; for a list with thousands of items, or one whose items are added dynamically at high frequency, this pattern doesn't scale as well as event delegation.

### Worked Example: event delegation

```js
list.addEventListener("click", (e) => {
  if (e.target.tagName === "LI") {
    const index = [...list.children].indexOf(e.target);
    todos[index].done = !todos[index].done;
    render();
  }
});
```

Instead of one listener per `<li>`, this attaches a **single** listener to the parent `<ul>` and inspects `e.target` (the actual element clicked) to figure out which child was responsible — this works even for `<li>` elements added *after* the listener was attached, since the listener lives on the stable parent, not on each individual (and potentially newly-created) child. This relies entirely on event **bubbling**: a click on an `<li>` bubbles up through `<ul>`, where the delegated listener catches it.

### Why It's Useful

Event delegation is a standard performance and correctness pattern for lists, tables, and any UI with many similar interactive children — fewer total listeners, and it automatically covers elements added after the fact without needing to re-attach anything. Understanding bubbling/capturing explains why `e.stopPropagation()` is sometimes necessary (to prevent a nested click handler from also triggering a parent's handler) and why `e.preventDefault()` is needed on form submission handlers to stop the browser's default full-page-reload behavior.

### Summary

- `addEventListener(type, handler)` is the standard way to respond to user interaction.
- Events bubble from the target up to the root by default; `e.target` identifies exactly which element triggered it.
- Event delegation (one listener on a shared ancestor) beats one-listener-per-child for lists and dynamic content.
- `e.preventDefault()` cancels default browser behavior; `e.stopPropagation()` halts further event travel.

---

## 4. `fetch` & Working with APIs

`fetch(url, options)` is the modern, promise-based HTTP client built directly into browsers and (since version 18) Node.js — no external library required for basic HTTP requests.

### Key Concepts

- **Returns a Promise**: `fetch(...)` resolves with a `Response` object once the HTTP response headers have arrived — it does not wait for the full body, which is why reading the body (`res.json()`, `res.text()`) is itself a *separate*, second `await`.
- **The classic gotcha — `fetch` does not reject on HTTP error statuses**: a `404` or `500` response still successfully *resolves* the promise; only genuine network failures (DNS failure, connection refused, offline) cause a rejection. You must check `response.ok` (or `response.status`) yourself and `throw` explicitly if it's not what you expect.
- **Sending JSON**: pass `{ method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(data) }` — `fetch` never stringifies the body automatically.
- **Parallel requests**: combine with `Promise.all` (Phase 5) to fire off several independent requests concurrently instead of awaiting them one at a time.

### Worked Example: GET with the `res.ok` check

```js
async function getPost(id) {
  const res = await fetch(`https://jsonplaceholder.typicode.com/posts/${id}`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);   // fetch itself won't throw on a 404!
  return res.json();                                     // parsing the body is ALSO async
}

const post = await getPost(1);
console.log(post.title);
```

Two separate `await`s are needed: one for the network round trip itself (`fetch(...)`), and one for parsing the response body (`res.json()`) — the body may still be streaming in even after the headers/status are known. The manual `if (!res.ok) throw ...` line is not optional boilerplate — without it, a `404 Not Found` response would silently flow through as if it succeeded, and `res.json()` would either fail confusingly or return an unrelated error payload treated as if it were real data.

### Worked Example: POST with a JSON body

```js
const created = await fetch("https://jsonplaceholder.typicode.com/posts", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ title: "hello", body: "from node", userId: 1 }),
}).then(r => r.json());
console.log(created.id);
```

The `Content-Type` header tells the server how to interpret the body; `JSON.stringify(data)` converts the JS object into the actual JSON text `fetch` sends over the wire — `fetch` has no idea your `body` argument is "meant to be JSON" unless you serialize it yourself and label it with the matching header.

### Worked Example: parallel requests

```js
const ids = [1, 2, 3];
const posts = await Promise.all(ids.map(id => getPost(id)));
console.log(posts.length);   // 3 — all three requests ran concurrently
```

`ids.map(id => getPost(id))` immediately creates three in-flight promises (three requests start essentially simultaneously); `Promise.all` then waits for all three to resolve. This is the same sequential-vs-parallel principle from Phase 5, applied directly to real network calls — awaiting `getPost` three times in a row inside a loop would needlessly serialize three independent HTTP round trips.

### Worked Example: handling an expected failure

```js
await getPost(999999).catch(e => console.log("expected miss handled:", e.message));
// expected miss handled: HTTP 404
```

Because `getPost` explicitly throws on `!res.ok`, a request for a nonexistent resource produces a normal, catchable rejection with a useful message — exactly the behavior you have to build yourself, since raw `fetch` alone would have resolved "successfully" with a 404 response object.

### Comparison Table: `fetch`'s rejection behavior

| Scenario | Does the `fetch(...)` promise reject? |
|---|---|
| Network failure (DNS, offline, connection refused) | Yes |
| HTTP `404 Not Found` | **No** — resolves with `res.ok === false` |
| HTTP `500 Internal Server Error` | **No** — resolves with `res.ok === false` |
| HTTP `200 OK` | No — resolves normally |
| `res.json()` on a non-JSON body | Yes — the body-parsing promise rejects |

### Why It's Useful

`fetch` is exactly how a frontend application talks to any backend API — including a Spring Boot REST API (track 02) — so understanding its promise-based shape, the `res.ok` gotcha, and how to send/parse JSON bodies is directly applicable to real full-stack work. The parallel-fetch pattern (`Promise.all` over an array of ids) is one of the most common real-world performance techniques for pages that need multiple independent pieces of data.

### Summary

- `fetch` returns a promise that resolves once headers arrive; parsing the body (`.json()`/`.text()`) is a second, separate `await`.
- `fetch` only rejects on network failure — always check `res.ok`/`res.status` and throw explicitly for HTTP errors.
- POST/PUT requests need an explicit `Content-Type` header and a `JSON.stringify`-ed body — nothing is automatic.
- Use `Promise.all(ids.map(fetchOne))` to run independent requests concurrently instead of sequentially.
