<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · routing forms](../phase-5-routing-forms/NOTES.md)
<!-- /nav -->

# Phase 6 — Version Migration (Angular 10 → latest LTS): Notes

Angular ships a **new major every ~6 months** and you *will* be asked to keep an app
current — or to rescue one that's stuck five versions behind. This phase is the
practical playbook: how Angular versions, how `ng update` actually works, what changed
at each milestone from **v10 → v20**, and the concrete breakages that bite you on a real
upgrade. It's grounded in the real spread across the work projects (Angular **10, 14,
15, 18, 20**), so the jumps below are the ones you'd actually perform.

## 6.1 — How Angular versions (the release model)

- **SemVer, honestly applied.** `MAJOR.MINOR.PATCH`. Major = breaking changes allowed;
  minor = features, backward-compatible; patch = fixes. Angular *means* it — a major
  bump can remove a deprecated API you depend on.
- **Cadence:** ~**2 majors per year** (roughly every 6 months), plus minors/patches in
  between. So v10 (2020) → v20 (2025) is ~10 majors in five years.
- **Support window per major:** ~**18 months** total = **6 months Active** (fixes +
  features) then **12 months LTS** (critical/security fixes only). There is *no* "run one
  version for years" lane like Java LTS — Angular's whole model assumes you keep moving.
- **The all-in-one version.** `@angular/core`, `@angular/common`, `@angular/router`,
  `@angular/cli`, `@angular/forms`, etc. **all share one version number** and are meant to
  move together. `@angular/cli` and `@angular/core` should always match majors.
- **Coupled peers:** each Angular major pins a **TypeScript range**, a **Node range**,
  a **zone.js** version, and an **RxJS** range. Half of upgrade pain is really *these*
  peers, not Angular code (see 6.5).
- **The golden rule: one major at a time.** You **cannot** jump v10 → v20 in one shot.
  `ng update` migration schematics are written to move you **N → N+1**. To go 10→20 you
  run the upgrade **ten times**, in order, building/testing between each.

## 6.2 — The upgrade tool: `ng update` + schematics

`ng update` is not `npm update`. It's Angular's **automated code-migration** runner.

```bash
ng update                       # dry audit: what's outdated + what's safe to update
ng update @angular/core@15 @angular/cli@15   # do ONE major, core+cli together
```

- **What it does:** bumps the package versions **and runs migration *schematics*** —
  codemods that rewrite your source (e.g. add `standalone: false`, migrate a renamed
  API, update `angular.json` schema). This is why you update *through* the tool, not by
  hand-editing `package.json`.
- **Preconditions it enforces:** a **clean git working tree** (so you can diff/revert the
  codemod), and compatible peers — it will refuse or warn on peer-dependency conflicts.
- **The canonical resource:** **`angular.dev/update-guide`** (formerly update.angular.io)
  — pick *from version* → *to version* + app complexity, and it prints the exact ordered
  command list and manual steps for that hop.
- **Reality with third-party libs:** `ng update` handles Angular's own packages; NgRx,
  Angular Material (`@angular/material` is first-party but a separate update),
  PrimeNG, ngx-translate, etc. each have their **own** version tied to an Angular major
  and their **own** `ng update`/changelog. A stuck peer dep is the #1 thing that blocks
  the whole hop.

## 6.3 — What changed at each milestone (v10 → v20)

The features that matter for a migration decision — and the version that forces or
enables them.

| Ver | Year | Headline | Migration impact |
|----|------|----------|------------------|
| **10** | 2020 | Stricter CLI defaults, `strict` flag, warns on CommonJS deps | Baseline for the legacy apps |
| **11** | 2020 | Webpack 5 opt-in, HMR CLI, faster builds | Low risk |
| **12** | 2021 | **Ivy on by default**; View Engine deprecated; `strictTemplates`; Sass migration; nullish in templates | Prod builds now Ivy; libs still ngcc-compiled |
| **13** | 2021 | **View Engine removed**; **IvyEverywhere** (no more ngcc for libs); RxJS 7.4; dropped IE11; `ng build` = production by default | **Hard break for old libraries** not shipped as Ivy |
| **14** | 2022 | **Standalone components (preview)**; **typed Reactive Forms**; `inject()`; `provideRouter`; page-title strategy | Typed forms can surface real type errors; standalone opt-in |
| **15** | 2022 | **Standalone stable**; **Material MDC migration**; directive composition API; `NgOptimizedImage`; functional router guards; auto-imports | **MDC is the big one** — Material CSS/DOM changes break styling |
| **16** | 2023 | **Signals (dev preview)**; **required inputs**; `DestroyRef`/`takeUntilDestroyed`; **esbuild dev-server (preview)**; `@angular/ssr`; self-closing tags | Mostly additive; esbuild builder opt-in |
| **17** | 2023 | **New control flow `@if`/`@for`/`@switch`**; **deferrable views `@defer`**; **esbuild/Vite application builder default**; SSR revamp; new brand/`angular.dev` | Build system default flips; control-flow migration schematic |
| **18** | 2024 | **Zoneless (experimental)**; **Material 3**; event replay for SSR; route redirects as functions; `@angular/build` split out | Zoneless opt-in; Material 3 theming rework |
| **19** | 2024 | **Standalone the default** (`standalone: true` implicit); **incremental hydration**; `linkedSignal`; **`resource()` API**; `ng generate` migrations for standalone | Standalone-by-default flips a default; schematic marks NgModule bits |
| **20** | 2025 | Signals & related APIs **stabilized**; effect/`afterRenderEffect` refinements; continued zoneless push | Modern baseline; the flagship app target |

**The mental model of the era shift:** v10–13 = *ViewEngine→Ivy* cleanup. v14–15 =
*standalone + typed forms* arrive. v16–17 = *signals + new control flow + esbuild* — the
"renaissance." v18–20 = *zoneless + hydration + signals stabilized*. An app on 10 and an
app on 20 are almost different frameworks in *style* (NgModule/`*ngIf`/decorators-only vs
standalone/`@if`/signals) even though the upgrade path is continuous.

## 6.4 — The migration playbook (multi-major, real-world)

A repeatable process for, say, taking a **v10 app to v20** (ten hops):

1. **Inventory first.** Note current Angular, TypeScript, Node, RxJS, zone.js, and every
   Angular-coupled library (Material, NgRx, PrimeNG, ngx-translate, ng-bootstrap…) with
   its version. These peers dictate the pace.
2. **Green baseline.** App must **build + tests pass + committed clean** before you start.
   Every hop ends the same way. Never stack two hops on one commit.
3. **Consult `angular.dev/update-guide`** for *each* hop (10→11, 11→12, …). Copy its
   exact command and manual steps.
4. **One major at a time**, core + cli together, then the coupled libs for that major:
   ```bash
   ng update @angular/core@11 @angular/cli@11
   # then, for versions where they gate you:
   ng update @angular/material@11
   # and the matching NgRx / etc. for that major
   npm run build && npm test        # gate before the next hop
   git commit -am "chore: angular 10 -> 11"
   ```
5. **Bump Node/TypeScript when a hop demands it** — each major pins ranges; the CLI will
   refuse an out-of-range Node/TS. Do this as part of the hop, not later.
6. **Run the schematics; review the diff.** `ng update` rewrites code — read what it
   changed. Where it can't (deprecated API removed outright), fix by hand per the guide.
7. **Optional modernization migrations** (do *after* you're current, as separate PRs, not
   during the version climb):
   ```bash
   ng generate @angular/core:control-flow          # *ngIf/*ngFor  → @if/@for
   ng generate @angular/core:standalone            # NgModules      → standalone
   ng generate @angular/core:inject                # constructor DI → inject()
   ng generate @angular/material:mdc-migration     # (v15 era) legacy → MDC components
   ```
8. **Test the risky surfaces** each hop: forms (typed-forms break at 14), Material
   styling (MDC break at 15), any library that lagged, and SSR/build config (builder flips
   at 17).

**Why not skip versions?** The N→N+1 schematics assume the previous ones already ran.
Jump two majors and the codemod for the version you skipped never executes — you inherit
silent, un-migrated code and a much harder debug.

## 6.5 — The breakages that actually bite (know these cold)

The interview-and-real-life list of "what goes wrong on an Angular upgrade":

- **View Engine → Ivy (12/13).** Prod compiler flips to Ivy at 12; View Engine + **ngcc**
  removed at 13. Any dependency still published **View-Engine-only** stops compiling —
  you must upgrade or replace it. Symptom: cryptic ngcc/compilation errors.
- **Angular Material MDC migration (v15).** Material's components were rebuilt on
  **Material Design Components (MDC)**. Same component names, **different DOM + CSS +
  larger/differently-sized elements** → your custom overrides and layouts break visually.
  There's a schematic + `legacy-*` components as a bridge, but the CSS cleanup is manual.
  On a real upgrade this is usually the **single biggest chunk of work**.
- **Typed Reactive Forms (v14).** `FormGroup`/`FormControl` became generic. Previously
  loose `.value`/`.get('x')` code now produces **real type errors** (or you opt out with
  `UntypedFormGroup`). Good long-term, noisy on the hop.
- **RxJS 6 → 7 (around v13).** Stricter typings; **`toPromise()` deprecated** (→
  `firstValueFrom`/`lastValueFrom`); some overloads/`combineLatest` signatures changed.
  Surfaces as type errors across services.
- **TypeScript & Node range bumps.** Each major raises the **minimum TS** (new strictness
  can fail the build) and **Node** (CI images/`.nvmrc` must move). Out-of-range = CLI
  refuses to run.
- **Coupled-library lockstep.** **NgRx, Material, PrimeNG, ngx-translate, ng-bootstrap**
  each pin an Angular major. If one has no release for your target major yet, it **blocks
  the whole hop** until it does (or you swap it out). Plan the upgrade around the
  slowest-moving dependency.
- **Removed/renamed APIs & config.** e.g. `entryComponents` (obsolete under Ivy),
  `ModuleWithProviders` needs its generic, `@ViewChild({static})` semantics, `HttpModule`
  → `HttpClientModule` (older), `defaultProject` removed from `angular.json`,
  `ng build` becoming production-by-default (your explicit `--prod` flag errors).
- **Build-system flip (v17).** Default builder moves to the **esbuild/Vite application
  builder**. Custom webpack tweaks (via `@angular-builders/custom-webpack`), some
  polyfill/`scripts` config, and certain CommonJS-only deps need attention or a pinned
  older builder.
- **Zone.js / zoneless (v18+).** Still opt-in through v20, but code that leans on Zone's
  implicit change detection (e.g. mutating state outside Angular and expecting a
  re-render) is what breaks when a team *does* move zoneless.
- **The clean-tree/`ng update` friction.** Dirty working tree → `ng update` refuses.
  Peer-dep conflicts → it warns/aborts. `--force`/`--allow-dirty` exist but hide real
  problems; fix the peer instead.

## Perspective

Angular upgrades are **routine, tooled, and incremental** — not the rewrites they're
feared to be — *if* you respect the model: one major at a time, `ng update` (not manual
`package.json` edits), a green build committed between every hop, and the coupled peers
(TS/Node/Material/NgRx) treated as first-class. The three landmines to name on sight are
**Ivy (13)**, **Material MDC (15)**, and **typed forms (14)**; the biggest *style* shift
(standalone + `@if`/`@for` + signals) is optional modernization you layer on **after**
you're current, via schematics. Knowing this is exactly the skill an Angular-10-to-LTS
migration assignment is testing.
