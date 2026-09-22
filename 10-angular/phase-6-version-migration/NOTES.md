<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · routing forms](../phase-5-routing-forms/NOTES.md)
<!-- /nav -->

# Phase 6 — Version Migration (Angular 10 → latest): Notes

Angular ships a **new major roughly every six months**, and any real Angular job will
eventually involve keeping an app current — or rescuing one that's stuck several
versions behind. This phase is the practical playbook: how Angular's release model
works, how `ng update` actually performs an upgrade, what changed at each milestone
from v10 onward, and the concrete breakages that bite on a real upgrade. It's grounded
in the real version spread across production work (Angular 10, 14, 15, 18, 20), so the
hops below are the ones you'd actually perform, culminating in the modern baseline
(`@angular/core` ^22 in this repo's own `expense-app`) that the rest of this track uses.

## Angular's release model

Angular follows **Semantic Versioning honestly** — a major version bump is explicitly
allowed to remove APIs you depend on, not just add features.

- **Key Concepts**
  - **`MAJOR.MINOR.PATCH`**: major = breaking changes allowed; minor = new features,
    backward-compatible; patch = bug fixes only.
  - **Cadence**: roughly **two majors per year** (about every six months), with minor
    and patch releases in between. v10 (2020) to v20 (2025) is about ten majors in
    five years — a much faster churn than, say, Java's LTS cadence.
  - **Support window per major**: about **18 months** total — **6 months Active**
    (features + fixes) followed by **12 months Long-Term Support / LTS** (critical and
    security fixes only, no new features). There is no "stay on one version
    indefinitely" lane; the whole model assumes continuous forward movement.
  - **The all-in-one version**: `@angular/core`, `@angular/common`, `@angular/router`,
    `@angular/forms`, `@angular/cli`, and the other first-party packages **share one
    version number** and are tested and released together. `@angular/cli` and
    `@angular/core` should always be on matching majors.
  - **Coupled peers**: each major pins compatible ranges for **TypeScript**, **Node**,
    **zone.js**, and **RxJS**. A large fraction of real upgrade pain comes from these
    peer ranges, not from Angular's own code changes.
  - **The one-major-at-a-time rule**: you cannot jump v10 → v20 in a single step.
    `ng update`'s migration schematics are written to move **N → N+1**; to go from 10 to
    20 you run the upgrade process **ten times, in order**, verifying the app between
    each hop.

**Why it's useful**: this model explains why "just bump the version in package.json"
is the wrong mental model for Angular — majors intentionally break things, peers are
coupled, and skipping versions skips the codemods written for the versions in between.
Interviewers ask about this constantly because it's the single most common real-world
Angular maintenance task.

## `ng update` and migration schematics

`ng update` is Angular's **automated code-migration tool** — not a simple version
bumper like `npm update`.

```bash
ng update                                     # dry audit: what's outdated, what's safe to update
ng update @angular/core@15 @angular/cli@15    # perform ONE major hop, core + cli together
```

- **Key Concepts**
  - **What it actually does**: bumps the package versions in `package.json` **and
    runs migration schematics** — codemods that rewrite your source and config for
    that specific version bump (e.g. adding `standalone: false` where needed, migrating
    a renamed API, updating `angular.json`'s schema). This is the entire reason you
    upgrade *through* the CLI rather than hand-editing `package.json` and running
    `npm install`.
  - **Preconditions it enforces**: a **clean git working tree** (so the schematic's
    changes are diffable and revertible) and compatible peer dependencies — it warns or
    refuses on a peer-dependency conflict rather than silently proceeding into a broken
    state.
  - **The canonical resource**: **`angular.dev/update-guide`** — pick your current
    version, your target version, and your app's complexity, and it generates the exact
    ordered command list and manual steps for that specific hop.
  - **Where it stops helping**: `ng update` handles Angular's *own* first-party
    packages. Third-party libraries coupled to an Angular major — **NgRx**, **Angular
    Material** (first-party, but a separate package with its own update), **PrimeNG**,
    **ngx-translate**, **ng-bootstrap** — each have their own version and their own
    `ng update`/changelog. A library with no release yet for your target major is the
    **single most common thing that blocks a hop**.

**Why it's useful**: knowing exactly what `ng update` does (and doesn't) do is the
difference between an upgrade that's mostly automated and one where you silently ship
un-migrated, deprecated code because you skipped the tool and edited `package.json`
directly.

## The migration playbook (a real multi-major upgrade)

A repeatable process for taking, say, a v10 app all the way to current — ten
individual hops, each treated as its own small project.

```bash
# Per hop (repeat for each major, in order):
ng update @angular/core@N @angular/cli@N        # the Angular hop itself
ng update @angular/material@N                   # matching coupled-library hop, if applicable
npm run build && npm test                       # gate: must pass before moving on
git commit -am "chore: angular N-1 -> N"         # one commit per hop, never stack two
```

- **Key Concepts (the playbook steps)**
  1. **Inventory first**: record current Angular, TypeScript, Node, RxJS, zone.js
     versions, and every Angular-coupled library with its version. These peers dictate
     the realistic pace of the whole migration.
  2. **Green baseline**: the app must build, pass its test suite, and have a clean
     committed working tree *before* you start — and again after *every* hop. Never
     stack two version hops into one commit; each hop needs to be independently
     revertible.
  3. **Consult `angular.dev/update-guide`** for each individual hop (10→11, 11→12, …) —
     copy its exact commands and manual steps rather than guessing.
  4. **One major at a time**, `core` + `cli` together, then whatever coupled libraries
     have a release for that specific major.
  5. **Bump Node/TypeScript** whenever a hop's target major requires it — the CLI
     refuses to run against an out-of-range Node or TypeScript version, so do this as
     part of the hop, not as an afterthought.
  6. **Run the schematics, then read the diff.** `ng update` rewrites your source; treat
     that diff as real code review, not a black box. Where a deprecated API was removed
     outright (no automatic migration possible), fix it by hand per the update guide.
  7. **Modernization migrations are a separate, later step** — run these only *after*
     the version climb is complete, as their own reviewable PRs:
     ```bash
     ng generate @angular/core:control-flow    # *ngIf/*ngFor  -> @if/@for
     ng generate @angular/core:standalone      # NgModules      -> standalone components
     ng generate @angular/core:inject          # constructor DI -> inject()
     ```
  8. **Test the historically risky surfaces** at each relevant hop: forms (typed forms
     landed at 14), Material styling (the MDC rebuild at 15), any library that lagged
     behind, and build/SSR configuration (the builder default flipped at 17).

**Why not skip a version?** Each N→N+1 schematic assumes the *previous* hop's
schematic already ran and left the codebase in that version's expected shape. Skipping
a version means the codemod written for the version you skipped never executes — you
end up on a bytecode-current but source-*un-migrated* codebase, with deprecated
patterns silently lingering until they break at the next upgrade or, worse, at runtime.

**Why it's useful**: this is the concrete, defensible answer to "how would you upgrade
a five-major-behind Angular app" — a question that comes up in almost any interview for
a role maintaining an existing Angular codebase.

## What changed at each milestone (v10 → v22)

The version-by-version headline features that matter for migration decisions, and the
practical impact of each on an upgrade in progress.

| Ver | Year | Headline | Migration impact |
|----|------|----------|------------------|
| **10** | 2020 | Stricter CLI defaults, `strict` flag, warns on CommonJS deps | Baseline for the legacy apps in production use |
| **11** | 2020 | Webpack 5 opt-in, HMR CLI, faster builds | Low risk |
| **12** | 2021 | **Ivy on by default**; View Engine deprecated; `strictTemplates`; Sass migration | Prod builds now Ivy; libraries still ngcc-compiled |
| **13** | 2021 | **View Engine removed**; Ivy-everywhere (no more ngcc); RxJS 7.4; dropped IE11 | Hard break for any library not shipped as Ivy |
| **14** | 2022 | **Standalone components (preview)**; **typed Reactive Forms**; `inject()`; `provideRouter` | Typed forms can surface real type errors; standalone is opt-in |
| **15** | 2022 | **Standalone stable**; **Material MDC migration**; functional router guards; auto-imports | MDC is usually the single largest chunk of upgrade work |
| **16** | 2023 | **Signals (dev preview)**; **required inputs**; `DestroyRef`/`takeUntilDestroyed`; esbuild dev server (preview) | Mostly additive; esbuild builder opt-in |
| **17** | 2023 | **New control flow `@if`/`@for`/`@switch`**; **`@defer` deferrable views**; esbuild/Vite application builder default | Build system default flips; control-flow migration schematic available |
| **18** | 2024 | **Zoneless (experimental)**; Material 3; event replay for SSR; route redirects as functions | Zoneless opt-in; Material 3 theming rework |
| **19** | 2024 | **Standalone the default** (`standalone: true` implicit); incremental hydration; `linkedSignal`; `resource()` | Standalone-by-default flips a compiler default; schematic marks remaining NgModule bits |
| **20** | 2025 | Signals & related APIs **stabilized**; `afterRenderEffect` refinements; continued zoneless push | Modern baseline for most teams |
| **21–22** | 2025–2026 | Continued signals/zoneless maturity; this repo's `expense-app` targets this range (`@angular/core: ^22`) | Fully standalone, signal-first, `@if`/`@for` throughout — the style every code sample in Phases 1–5 uses |

- **Key Concepts**
  - **The mental model of the era shift**: v10–13 = View Engine → Ivy cleanup. v14–15 =
    standalone components + typed forms arrive. v16–17 = signals + the new control flow
    + esbuild — often described as the "renaissance." v18+ = zoneless + hydration +
    signals stabilized, standalone-by-default.
  - An app still on v10 (NgModules, `*ngIf`, constructor DI, decorators-only, zone-based
    change detection) and this repo's v22 `expense-app` (standalone by default, `@if`/
    `@for`, `input()`/signals, `inject()`) are almost different *styles* of the same
    framework, even though the upgrade path between them is a continuous, incremental
    chain of majors.

**Why it's useful**: recognizing *which* version introduced a given pattern (standalone
at 14/15, signals at 16, `@if`/`@for` at 17) lets you date a codebase at a glance from
its syntax alone, and tells you exactly which modernization schematics would apply to
bring it forward.

## The breakages that actually bite (know these cold)

The concrete, real-world list of "what goes wrong on an Angular upgrade" — the specific
things worth naming unprompted in an interview about Angular maintenance.

- **View Engine → Ivy (v12/v13)**. The production compiler flips to Ivy by default at
  v12; View Engine and **ngcc** (the compatibility compiler that let View-Engine-era
  libraries work under Ivy) are removed entirely at v13. Any dependency still published
  **View-Engine-only** simply stops compiling — you must upgrade it to an Ivy-compatible
  version or replace it. The symptom is cryptic ngcc/compilation errors that don't
  obviously point at "this library is too old."
- **Angular Material's MDC migration (v15)**. Material's components were rebuilt on
  **Material Design Components (MDC)** — same component selectors, but different
  internal DOM structure, CSS, and element sizing. Custom style overrides and tight
  layouts that depended on the old DOM shape break visually. There's a migration
  schematic plus temporary `legacy-*` components as a bridge, but reconciling custom
  CSS is manual work — on a real upgrade, this is usually the **single biggest chunk**
  of effort.
- **Typed Reactive Forms (v14)**. `FormGroup`/`FormControl` became generic over their
  value type. Code that was previously loosely typed (`.value`/`.get('x')` returning
  `any`) now produces real compile-time type errors — good long-term, noisy on the
  specific hop. `UntypedFormGroup`/`UntypedFormControl` exist as an escape hatch to
  defer the type cleanup rather than block the version bump on it.
- **RxJS 6 → 7 (around v13)**. Stricter typings across the board; **`toPromise()` is
  deprecated** in favor of `firstValueFrom`/`lastValueFrom`; some operator overloads and
  `combineLatest` call signatures changed. Surfaces mostly as type errors scattered
  across services.
- **TypeScript and Node range bumps**. Every major raises the minimum supported
  TypeScript (new strictness settings can fail a build that compiled fine before) and
  Node version (CI images and `.nvmrc` must move in lockstep). An out-of-range Node or
  TS version makes the CLI refuse to run at all, rather than degrading gracefully.
- **Coupled-library lockstep**. NgRx, Angular Material, PrimeNG, ngx-translate,
  ng-bootstrap each pin their own compatible Angular major. If one hasn't released for
  your target major yet, it **blocks the entire hop** until it does (or you swap the
  dependency out) — plan an upgrade around whichever dependency moves slowest, not
  around Angular itself.
- **Removed or renamed APIs and config**. Examples across the version range:
  `entryComponents` (made obsolete under Ivy), `ModuleWithProviders` requiring its
  generic parameter, `@ViewChild({ static })` semantics changing, `HttpModule` →
  `HttpClientModule` in older versions, `defaultProject` removed from `angular.json`,
  and `ng build` becoming production-by-default at a point where an explicit `--prod`
  flag started erroring instead of being redundant.
- **Build-system flip (v17)**. The default builder moved to the **esbuild/Vite
  application builder**. Custom webpack configuration (via
  `@angular-builders/custom-webpack`), certain polyfill/`scripts` entries, and
  CommonJS-only dependencies can all need attention, or a deliberate pin to the older
  builder, at this hop.
- **Zone.js / zoneless (v18+)**. Zoneless remains opt-in through the current majors, but
  code that implicitly relies on Zone.js's "any async operation triggers a
  change-detection check" behavior — mutating state outside Angular's awareness (a raw
  `setTimeout`, a third-party library's callback, a non-signal mutation) and expecting
  the view to refresh — is exactly what breaks the moment a team chooses to go
  zoneless.
- **The clean-tree/`ng update` friction**. A dirty working tree makes `ng update`
  refuse outright; peer-dependency conflicts make it warn or abort. `--force` and
  `--allow-dirty` exist to override both, but they hide the real problem rather than
  solving it — the correct move is almost always to fix the peer or commit/stash first.

**Why it's useful**: this is the list an interviewer is actually listening for when
they ask "tell me about a hard Angular upgrade you did" or "what would you expect to
break going from Angular 10 to current" — naming Ivy (12/13), Material MDC (15), and
typed forms (14) unprompted signals real hands-on migration experience, not just
theoretical version-number knowledge.

## Summary — Key takeaways

- Angular releases ~2 majors/year, ~18-month support window per major (6 Active + 12
  LTS); all first-party packages share one version and pin compatible TS/Node/RxJS/
  zone.js ranges.
- `ng update` is a codemod runner, not a version bumper — it requires a clean git tree,
  and third-party libraries need their own separate updates.
- You cannot skip majors: schematics are N→N+1, so upgrading five majors behind means
  five separate hops, each gated by a green build + test + commit.
- Know the three landmine versions cold: **Ivy/View-Engine removal (12/13)**,
  **Material MDC (15)**, and **typed Reactive Forms (14)** — plus RxJS 6→7, rising
  TS/Node minimums, and the esbuild/Vite builder default at 17.
- The *style* shift (standalone, `@if`/`@for`, signals, `inject()`) is optional
  modernization layered on **after** the version climb is complete, via its own
  schematics and its own PRs — never mixed into the version-bump commits themselves.
- This repo's `expense-app` (Angular ^22) represents the destination of that whole
  journey: fully standalone, signal-first, new control flow throughout.
