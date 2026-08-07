<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · reactivity](../phase-4-reactivity/NOTES.md) | [Phase 6 · version migration ➡](../phase-6-version-migration/NOTES.md)
<!-- /nav -->

# Phase 5 — Routing & Forms: Interview Q&A

⭐ = asked constantly.

**Q: How does routing work in Angular?** ⭐
You define a `Routes` array of `{ path, component }`, register it with
`provideRouter(routes)`, and the active route's component renders in `<router-outlet>`.
Navigate with `routerLink` (declarative) or `Router.navigate()` (programmatic).

**Q: What is lazy loading and why use it?** ⭐⭐
Loading a route's component/bundle only when the route is visited, via
`loadComponent: () => import(...)`. It code-splits the app so the initial bundle is
smaller and loads faster; the rest is fetched on demand. Essential for large apps.

**Q: How do you read route parameters?** ⭐
Via `ActivatedRoute` (`paramMap`, `snapshot`) or, with `withComponentInputBinding()`, by
binding the param directly to a component `input()` of the same name. The input-binding
approach is the cleanest modern option and removes the `ActivatedRoute` boilerplate.

**Q: What are route guards?** ⭐⭐
Functions that control navigation: `canActivate` (allow entering — e.g. auth),
`canDeactivate` (allow leaving — e.g. unsaved-changes warning), `canMatch` (whether a
route is considered — feature flags/roles). They return boolean/UrlTree/Observable and
use `inject()` for dependencies.

**Q: What's a resolver?**
A function that pre-fetches data before a route activates, so the component renders with
data already available (`resolve` config). It's an alternative to fetching inside the
component, trading a slightly delayed navigation for a ready-rendered view.

**Q: Reactive vs Template-driven forms?** ⭐⭐
Reactive forms define the model in TypeScript (`FormGroup`/`FormControl` via
`FormBuilder`), giving typed, explicit, testable forms with code-declared validation —
preferred for anything non-trivial. Template-driven forms build the model in the template
with `ngModel` — simpler for small forms but less scalable and harder to test.

**Q: How does form validation work?** ⭐
Attach validators (`Validators.required`, `min`, custom functions) to controls. Angular
tracks validity and states (`valid`/`invalid`, `touched`, `dirty`) on controls and the
group; you use them to show errors and disable submit (`[disabled]="form.invalid"`).

**Q: Why choose Reactive Forms for a complex form?** *nuance*
The form state and validation live in TypeScript, so they're strongly typed, unit-testable
without rendering, and easy to compose/derive (e.g. dynamic controls, cross-field
validation, reacting to `valueChanges` with RxJS). Template-driven forms can't express
that complexity cleanly.
