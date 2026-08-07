<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · routing forms](../phase-5-routing-forms/NOTES.md)
<!-- /nav -->

# Phase 6 — Version Migration: Interview Q&A

Answer out loud before reading. ⭐ = asked constantly. These are the questions you'll
get when a role involves **maintaining/upgrading** an existing Angular app (very common).

## 6.1 — Release model

**Q: How does Angular version and how often does it release?** ⭐
SemVer, and it means it — a major can remove deprecated APIs. It ships **~2 majors a
year** (every ~6 months). Each major is supported ~**18 months**: 6 months Active
(features + fixes) then 12 months LTS (critical/security fixes only). Unlike Java there's
no "sit on one version for years" lane — the model assumes you keep moving.

**Q: Why do all the `@angular/*` packages share one version?** ⭐
They're released as a set and are tested together; `@angular/core` and `@angular/cli`
especially must share a major. Each major also pins compatible **TypeScript, Node,
zone.js, and RxJS** ranges — mismatched peers are half of all upgrade pain.

**Q: Can you jump from Angular 10 straight to 20?** ⭐⭐
No. `ng update`'s migration schematics are written **N → N+1**; skip a version and its
codemod never runs, leaving un-migrated code. You go **one major at a time**, ten hops,
building and testing (and committing) between each. The `angular.dev/update-guide` gives
the exact steps per hop.

## 6.2 — Tooling

**Q: What's the difference between `ng update` and `npm update`?** ⭐⭐
`npm update` only bumps version numbers. `ng update` bumps the packages **and runs
migration schematics** — codemods that rewrite your source and config (renamed APIs,
`angular.json` schema, standalone flags). That automated code-migration is the whole
point; you upgrade *through* `ng update`, never by hand-editing `package.json`.

**Q: What does `ng update` require before it'll run?**
A **clean git working tree** (so its codemods are reviewable/revertible) and compatible
peer dependencies — it warns or aborts on conflicts. `--allow-dirty`/`--force` exist but
mask real problems; fix the peer instead.

**Q: How do you actually plan a multi-major upgrade?** ⭐
Inventory current Angular/TS/Node/RxJS + every coupled library. Get a green, committed
baseline. Then per hop: consult the update guide, `ng update @angular/core@N
@angular/cli@N` together plus the coupled libs (Material/NgRx) for that N, bump Node/TS if
the hop demands it, review the schematic diff, `npm run build && npm test`, commit. Repeat.

**Q: Where does `ng update` stop helping?**
It handles Angular's own packages (Material is first-party but a separate update). Every
third-party lib coupled to an Angular major — **NgRx, PrimeNG, ngx-translate,
ng-bootstrap** — has its own version and changelog. A library with no release for your
target major **blocks the whole hop**; plan the upgrade around the slowest dependency.

## 6.3 — Milestone knowledge

**Q: What was the Ivy transition and why does it matter for upgrades?** ⭐⭐
Ivy is Angular's rendering/compilation engine that replaced **View Engine**. It became
the default at **v12** and View Engine (plus **ngcc**, the lib compatibility compiler)
was **removed at v13**. The migration risk: any dependency still published *View-Engine-
only* stops compiling — you must upgrade or replace it. Cryptic ngcc errors are the tell.

**Q: What are the biggest breaking changes between 10 and today?** ⭐
Three to name on sight: **Ivy/View-Engine removal (12/13)**, **Angular Material's MDC
rebuild (15)** — same component names but new DOM/CSS that breaks styling — and **typed
Reactive Forms (14)**, which turn previously-loose form code into real type errors. Plus
RxJS 6→7 (`toPromise` deprecated), rising TS/Node minimums, and the esbuild/Vite builder
becoming default at **17**.

**Q: What actually changes going 10 → 15 → 20 in *how you write* Angular?** ⭐
v14–15 bring **standalone components** and **typed forms**; v16–17 bring **signals**, the
new **`@if`/`@for` control flow**, `@defer`, and the **esbuild** builder; v19 makes
**standalone the default** and adds hydration/`resource()`; v20 stabilizes signals. A v10
app (NgModules, `*ngIf`, constructor DI, zone-based) and a v20 app (standalone, `@if`,
`inject()`, signals, heading zoneless) are almost different *styles* of the same
framework.

**Q: Is the standalone / `@if` / signals rewrite part of the version upgrade?**
No — keep them separate. First get **current** (the version climb, hop by hop). *Then*, as
independent PRs, run the modernization schematics: `ng generate
@angular/core:control-flow`, `:standalone`, `:inject`. Mixing them into the version bump
makes every hop's diff unreviewable.

## 6.4 — The real breakages

**Q: You upgrade Material and the whole app looks broken. What happened?** ⭐
The **MDC migration (v15)**. Material's components were rebuilt on Material Design
Components — same selectors, **different internal DOM, CSS, and element sizing** — so
custom style overrides and tight layouts break. Angular ships a schematic plus
`legacy-*` components as a temporary bridge, but reconciling your CSS is manual and is
usually the **largest single piece of work** in a modern Angular upgrade.

**Q: After bumping majors, your forms won't compile. Why?**
**Typed Reactive Forms (v14).** `FormGroup`/`FormControl` are now generic, so
`.get('x')` / `.value` that were `any` before are type-checked. Fix the types properly,
or opt out temporarily with `UntypedFormGroup`/`UntypedFormControl` and migrate
incrementally.

**Q: What non-Angular things break on an upgrade?** ⭐
The **coupled peers**: TypeScript minimum rises (new strictness can fail the build), Node
minimum rises (CI/`.nvmrc`), **RxJS 6→7** deprecates `toPromise()` (→
`firstValueFrom`/`lastValueFrom`) and tightens typings, and the **esbuild/Vite builder
default at v17** can break custom-webpack setups and CommonJS-only deps. Out-of-range
Node/TS makes the CLI refuse to run at all.

**Q: What breaks when a team finally goes zoneless (18+)?**
Anything relying on **zone.js's implicit change detection** — mutating state outside
Angular's awareness (a raw `setTimeout`, a third-party callback, a non-signal mutation)
and expecting the view to refresh. Zoneless requires signals or explicit
`markForCheck`/`ChangeDetectorRef`. It's opt-in through v20, so it breaks only when you
choose to move — but you should know *why*.

**Q: A junior force-upgrades three majors at once with `--force` and it "works." Risk?**
The N→N+1 schematics for the skipped versions never ran, so the code is bytecode-current
but **source-un-migrated** — deprecated patterns linger and will detonate on the next
upgrade or at runtime. `--force`/`--allow-dirty` also hid the peer-dependency conflicts
that would have flagged an incompatible library. Correct move: revert, go one major at a
time on a clean tree, gate each hop with a build+test+commit.
