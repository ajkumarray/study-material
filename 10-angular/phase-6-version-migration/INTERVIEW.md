<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · routing forms](../phase-5-routing-forms/NOTES.md)
<!-- /nav -->

# Phase 6 — Version Migration: Interview Q&A

⭐ = asked constantly. These come up whenever a role involves **maintaining or
upgrading** an existing Angular app — which is most Angular roles, since very few teams
are greenfield.

## Release model

**Q: How does Angular version, and how often does it release?** ⭐
Semantic Versioning, applied strictly — a major bump is explicitly allowed to remove
deprecated APIs, not just add features. Angular ships roughly **two majors a year**
(about every six months). Each major gets about **18 months** of support total: **6
months Active** (new features plus fixes) followed by **12 months LTS** (critical and
security fixes only). Unlike a Java-style "pick an LTS and sit on it for years" model,
Angular's whole design assumes continuous forward movement — there's no long-term
"parking" version.

**Q: Why do all the `@angular/*` packages share one version number?** ⭐
They're developed, tested, and released as a single coordinated set — `@angular/core`,
`@angular/common`, `@angular/router`, `@angular/forms`, and `@angular/cli` are meant to
always be on the same major. Each major also pins compatible ranges for **TypeScript**,
**Node**, **zone.js**, and **RxJS** — a huge fraction of real-world upgrade pain is
actually these coupled peer-dependency ranges shifting, not Angular's own APIs
changing.

**Q: Can you jump straight from Angular 10 to Angular 20?** ⭐⭐
No. `ng update`'s migration schematics are written to move a project **N to N+1**
only — skip a version and that version's codemod simply never runs, leaving the
codebase with un-migrated, deprecated patterns even though `package.json` says you're
current. The correct approach is one major at a time — ten separate hops to go from 10
to 20 — each with its own build, test run, and commit, consulting
`angular.dev/update-guide` for the exact steps of that specific hop.

## Tooling

**Q: What's the difference between `ng update` and `npm update`?** ⭐⭐
`npm update` only bumps version numbers in `package.json`/`package-lock.json` according
to semver ranges — it has no idea an Angular major might need source code changes.
`ng update` bumps the packages **and runs migration schematics** — codemods that
rewrite your actual source and config (renamed APIs, `angular.json` schema changes,
adding explicit flags where a default changed). That automated code migration is the
entire reason to upgrade *through* `ng update` rather than hand-editing
`package.json` and reinstalling.

**Q: What does `ng update` require before it will run?**
A **clean git working tree** — so the schematic's changes are cleanly diffable and
revertible if something goes wrong — and compatible peer dependencies, which it warns
or aborts on rather than silently proceeding. `--allow-dirty` and `--force` exist to
override both checks, but doing so hides real problems (an uncommitted change getting
mixed into the schematic's diff, or an incompatible library) rather than resolving
them; the correct move is almost always to commit/stash first and fix the peer.

**Q: How would you actually plan and execute a multi-major upgrade?** ⭐
Start with an inventory: current Angular, TypeScript, Node, RxJS, zone.js versions, and
every Angular-coupled library with its version — these dictate the realistic pace.
Get to a green, fully committed baseline. Then, per hop: consult
`angular.dev/update-guide` for that specific N→N+1 step, run `ng update
@angular/core@N @angular/cli@N` together, run the matching update for any coupled
library that has a release for N (Material, NgRx), bump Node/TypeScript if the hop
requires it, review the schematic's diff like real code review, run the build and full
test suite, and commit — one hop per commit, never stacking two. Repeat until current.
Modernization schematics (standalone, control-flow, `inject()`) come only *after*, as
separate PRs.

**Q: Where does `ng update` stop helping you?**
It only manages Angular's own first-party packages (Material is first-party but ships
as its own package with its own update command). Every third-party library coupled to
an Angular major — **NgRx, PrimeNG, ngx-translate, ng-bootstrap** — has its own version
and its own changelog/update process. A library with no release yet for your target
major is the single most common thing that **blocks an entire hop**; you plan the
upgrade's pace around whichever dependency is slowest to release, not around Angular
itself.

## Milestone knowledge

**Q: What was the Ivy transition, and why does it matter for upgrades?** ⭐⭐
Ivy is Angular's compilation/rendering engine that replaced the older **View Engine**.
It became the default production compiler at **v12**, and View Engine plus **ngcc**
(the compatibility compiler that let View-Engine-only libraries keep working under Ivy)
were **removed entirely at v13**. The migration risk: any dependency still published in
View-Engine-only form simply stops compiling once you're on v13+ — you have to upgrade
it to an Ivy-compatible release or replace it outright. The tell in practice is cryptic
ngcc/compilation errors that don't obviously say "this specific library is too old."

**Q: Name the biggest breaking changes between Angular 10 and today.** ⭐
Three to lead with, unprompted: **Ivy/View-Engine removal (12/13)** — old libraries
stop compiling; **Angular Material's MDC rebuild (15)** — same component names, totally
different DOM/CSS, which breaks styling and is usually the single largest chunk of work
on a real upgrade; and **typed Reactive Forms (14)** — previously-loose form code
starts producing real type errors. Round it out with RxJS 6→7 (`toPromise()`
deprecated in favor of `firstValueFrom`/`lastValueFrom`), steadily rising TypeScript and
Node minimums, and the esbuild/Vite application builder becoming the default at **17**.

**Q: How does *how you write* Angular actually change going from 10 to current?** ⭐
v14–15 introduce **standalone components** and **typed Reactive Forms**. v16–17 bring
**signals** (dev preview at 16), the new **`@if`/`@for`/`@switch`** control flow,
**`@defer`**, and the **esbuild** application builder. v19 flips **standalone to the
default** and adds incremental hydration and `linkedSignal`/`resource()`. v20+
stabilizes signals and pushes further toward **zoneless**. A v10 app — NgModules,
`*ngIf`/`*ngFor`, constructor injection, decorators-only, Zone.js-driven change
detection — and a current app like this repo's `expense-app` (standalone by default,
`@if`/`@for`, signal-based `input()`, `inject()`) are close to different *styles* of
the same framework, even though the upgrade path between them is one continuous chain
of incremental majors.

**Q: Is the standalone / new control-flow / signals rewrite part of the version
upgrade itself?**
No — deliberately keep them separate. First get the app **current**, hop by hop,
through the pure version climb. *Only then*, as independent, separately-reviewable
PRs, run the modernization schematics: `ng generate @angular/core:standalone`,
`:control-flow`, `:inject`. Mixing modernization changes into a version-bump commit
makes that commit's diff unreviewable — you can no longer tell which changes were
forced by the version bump and which were a style choice, which makes bisecting a
regression much harder.

## The real breakages

**Q: You upgrade Angular Material and the whole app's styling looks broken. What
happened, and how do you recover?** ⭐
The **MDC migration at v15**. Material's components were rebuilt on Material Design
Components — same selectors, but genuinely different internal DOM structure, CSS, and
element sizing, so any custom style override or tightly-coupled layout that depended on
the old DOM shape breaks. Angular ships a migration schematic plus temporary
`legacy-*` component variants as a bridge so you can upgrade the version without
immediately fixing every style, but reconciling the actual CSS is manual work — budget
for it as the largest single item in a v14/15-era upgrade, not an afterthought.

**Q: After bumping majors, your reactive forms code suddenly won't compile. Why?**
**Typed Reactive Forms, landed at v14.** `FormGroup`/`FormControl` became generic over
their value type, so code that previously read `.value`/`.get('x')` as `any` now gets
real compile-time type checking — and often real, previously-hidden bugs surface as
type errors. The pragmatic options are to fix the types properly (the long-term-correct
move) or temporarily opt out with `UntypedFormGroup`/`UntypedFormControl` to unblock the
version bump and migrate the types incrementally afterward.

**Q: Beyond Angular's own APIs, what non-Angular things break on an upgrade?** ⭐
The coupled peers: the minimum supported **TypeScript** version rises with each major
(new compiler strictness can fail a build that compiled cleanly before), the minimum
**Node** version rises (CI images and `.nvmrc` need to move in lockstep, or the CLI
simply refuses to run), **RxJS 6→7** deprecates `toPromise()` in favor of
`firstValueFrom`/`lastValueFrom` and tightens some operator typings, and the
**esbuild/Vite builder becoming default at v17** can break custom webpack
configuration or CommonJS-only dependencies that assumed the old builder.

**Q: What breaks when a team finally turns on zoneless change detection (18+)?**
Anything that implicitly relies on **Zone.js's "any async operation triggers a
change-detection check"** behavior — state mutated outside Angular's awareness (a raw
`setTimeout` callback, a third-party library's own callback, a plain object mutated in
place instead of through a signal) and then expected to show up in the view without any
explicit signal update or `ChangeDetectorRef.markForCheck()` call. Zoneless is opt-in
through the current majors, so it only breaks code the moment a team actually chooses
to adopt it — but knowing *why* it breaks (no more implicit "anything async → check
everything" safety net) is the actual interview signal here.

**Q: A teammate force-upgrades three majors at once with `--force --allow-dirty` and
claims it "works" — what's the actual risk?** ⭐
The N→N+1 migration schematics for every version they skipped **never ran**, so the
code is now on a current *package* version but is source-level **un-migrated** for the
intermediate majors — deprecated patterns and renamed APIs that should have been
codemodded are still sitting there, and will surface as a confusing failure at the
*next* upgrade or, worse, silently at runtime. `--force`/`--allow-dirty` also suppressed
the peer-dependency conflict warnings that would have flagged an incompatible library
up front. The correct fix is to revert, then go through the same three majors one at a
time on a clean tree, with a build-test-commit gate after each hop.
