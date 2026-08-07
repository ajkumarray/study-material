/*
 * Phase 7 (part 1) — Runtime & tooling: npm, package.json, fetch
 * Run:  node npm-and-fetch.mjs
 *
 * Covers 7.1 npm/package.json and 7.3 fetch/APIs (both run in Node).
 * The DOM (7.2) is browser-only — see dom-demo.html in this folder.
 */

// ===========================================================================
// 7.1 — npm & package.json (concepts; see package.json in this folder)
// ===========================================================================
console.log("=== 7.1 npm & package.json ===");

console.log(`
  npm = Node Package Manager: the registry + CLI for JS dependencies
  (the JS world's Maven Central + mvn, roughly).

  package.json — the project manifest (JS's pom.xml):
    "name"/"version"     project identity
    "type": "module"     use ES modules (.js files become ESM)
    "scripts"            named commands: npm run dev / test / build
    "dependencies"       needed at runtime      (Maven compile scope)
    "devDependencies"    needed only to build/test (Maven test/provided)

  key commands:
    npm install          read package.json, fetch deps into node_modules/
    npm install <pkg>    add a dependency
    npm run <script>     run a script from "scripts"
    npx <tool>           run a package binary without installing globally

  package-lock.json      pins EXACT resolved versions (reproducible builds,
                         like a Maven lockfile) — commit it.
  node_modules/          the downloaded deps — never commit; .gitignore it.
  semver "^1.2.3"        ^ = compatible (<2.0), ~ = patch only (<1.3).
`);

// ===========================================================================
// 7.3 — fetch & working with APIs
// ===========================================================================
console.log("=== 7.3 fetch ===");

// fetch is the modern HTTP client, built into browsers AND Node 18+.
// It returns a PROMISE (Phase 5), so async/await is the natural style.
// We hit a public test API (needs internet; degrades gracefully if offline).
async function getPost(id) {
    const res = await fetch(`https://jsonplaceholder.typicode.com/posts/${id}`);
    // fetch does NOT reject on HTTP errors (404/500) — only on network
    // failure. You MUST check res.ok yourself (a classic gotcha):
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return res.json();                              // .json() also returns a promise
}

async function main() {
    try {
        const post = await getPost(1);
        console.log("GET post 1 title:", post.title?.slice(0, 40) + "...");

        // POST with a JSON body — headers + stringified body:
        const created = await fetch("https://jsonplaceholder.typicode.com/posts", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ title: "hello", body: "from node", userId: 1 }),
        }).then((r) => r.json());
        console.log("POST created id:", created.id);

        // Parallel requests — fetch several at once (Phase 5's Promise.all):
        const ids = [1, 2, 3];
        const posts = await Promise.all(ids.map((id) => getPost(id)));
        console.log("parallel fetched titles:", posts.length);

        // Error handling in action — a bad id path:
        await getPost(999999).catch((e) => console.log("expected miss handled:", e.message));
    } catch (e) {
        // Offline / DNS failure lands here — the lesson still "passes".
        console.log("network unavailable (fine for offline study):", e.message);
    }
}

await main();
console.log("\nphase 7 (node part) done. Open dom-demo.html for the DOM lesson.");
