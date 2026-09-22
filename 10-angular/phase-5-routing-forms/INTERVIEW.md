<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · reactivity](../phase-4-reactivity/NOTES.md) | [Phase 6 · version migration ➡](../phase-6-version-migration/NOTES.md)
<!-- /nav -->

# Phase 5 — Routing & Forms: Interview Q&A

⭐ = asked constantly.

**Q: How does routing work in Angular, end to end?** ⭐
You define a `Routes` array of `{ path, component }` entries (plus optional
`redirectTo`, `title`, guards, resolvers, `loadComponent`), register it app-wide with
`provideRouter(routes, ...features)` in `app.config.ts`, and place a
`<router-outlet />` in the template wherever the active route's component should
render. Navigation happens either declaratively (`routerLink="/expenses"` on a
clickable element) or programmatically (`Router.navigate(['/expenses'])` from code,
e.g. after a form saves successfully). Both update the URL and browser history without
a full page reload.

**Q: What does `pathMatch: 'full'` do, and why does the empty-path redirect need it?**
*nuance* Without `pathMatch: 'full'`, a route's `path` is matched as a *prefix* by
default — `{ path: '', redirectTo: 'expenses' }` alone would match the empty prefix of
*every* URL and redirect everything, not just the literal empty path. `pathMatch:
'full'` requires the entire remaining URL segment to be empty for that specific route
to match, so the redirect only fires when the user is actually at the app's root with
nothing after it.

**Q: What is lazy loading, and what's the difference between `loadComponent` and
`loadChildren`?** ⭐⭐ Lazy loading defers fetching a route's code until the user
actually navigates to it, via a dynamic `import()`, splitting the app into smaller
chunks fetched on demand instead of one large upfront bundle. `loadComponent` lazily
loads a single standalone component for one route — the fine-grained option standalone
components enabled (`expense-app`'s `expenses/:id` route does exactly this).
`loadChildren` lazily loads a whole set of child routes — typically an entire feature
area — as one chunk; it's the older, coarser mechanism, still used when grouping a
large section of the app rather than one screen.

**Q: How do you read route parameters, and what's the "modern" way to do it?** ⭐
Classically via `ActivatedRoute` — `.paramMap` (an Observable, for parameters that can
change without the component being recreated, e.g. navigating between two detail
pages of the same route) or `.snapshot.paramMap` (a one-time read). The modern approach
is `withComponentInputBinding()` — a `provideRouter` feature that binds route
parameters (and resolved route `data`) directly to a component's `input()`/`@Input()`
of the matching name, removing the `ActivatedRoute` injection and subscription
entirely.

```ts
readonly id = input.required({ transform: (v: string) => Number(v) });
```

Note the `transform`: route parameters always arrive as strings (URLs are text), so
`ExpenseDetail`'s `id` input converts it to a `number` right in the input declaration.

**Q: What are route guards, and what does each type control?** ⭐⭐ Functions that run
before a navigation completes and decide whether it's allowed. `canActivate` — can the
user *enter* this route (the classic auth check, redirecting to a login page if not).
`canDeactivate` — can the user *leave* the current route (warn about unsaved form
changes before navigating away). `canMatch` — should this route even be *considered* a
match at all, evaluated before `canActivate`; useful for swapping which route handles a
path based on a feature flag or user role rather than matching-then-blocking. All three
can return `boolean`, a `UrlTree` (redirect elsewhere via
`router.createUrlTree([...])`), or an `Observable`/`Promise` of either for async
checks. Modern guards are plain functions using `inject()`, not classes.

```ts
export const authGuard: CanActivateFn = () =>
  inject(AuthService).isLoggedIn() ? true : inject(Router).createUrlTree(['/login']);
```

**Q: What's a resolver, and when would you use one instead of fetching inside the
component?** A function (`ResolveFn`) that pre-fetches data *before* a route activates,
so the destination component renders with the data already available instead of first
rendering and then showing a loading state. Use a resolver when the component genuinely
can't render anything useful without the data; fetch inside the component (in
`ngOnInit`, or via a signal-backed service like this repo's `ExpenseService`) when a
brief loading state is acceptable and you'd rather navigate immediately. It's a
trade-off between "navigation waits for data" and "navigation is instant, view shows a
loading state" — be ready to argue either side depending on the UX requirement.

**Q: Reactive vs Template-driven forms — what's the real difference, and which do you
default to?** ⭐⭐ Reactive Forms define the model explicitly in TypeScript
(`FormBuilder.group({...})` producing a `FormGroup` of `FormControl`s), with the
template binding to that pre-built model via `[formGroup]`/`formControlName`.
Template-driven forms build the model *implicitly*, assembled by Angular from
`[(ngModel)]`-bound inputs declared directly in the template. Reactive Forms are
strongly typed (especially with `.nonNullable.group(...)`, standard since typed forms
landed in v14), fully unit-testable without rendering the DOM, and scale cleanly to
dynamic/complex forms (conditional fields, cross-field validation, reacting to
`valueChanges` with RxJS operators). Template-driven forms are quicker to write for a
tiny form but don't scale past that. The general — and Angular-team — recommendation is
Reactive Forms for anything beyond the trivial case.

```ts
readonly form = this.fb.nonNullable.group({
  description: ['', Validators.required],
  amount: [0, [Validators.required, Validators.min(0.01)]],
});
```

**Q: What does `.nonNullable.group(...)` buy you over plain `.group(...)`?** *nuance*
Typed Reactive Forms (stable since v14) make every control generic over its value type.
Plain `FormBuilder.group(...)` allows a control's value to become `null` (e.g. after
`.reset()` with no argument), so `.value`/`.getRawValue()` types include `null` and
downstream code has to guard against it. `.nonNullable.group(...)` excludes `null` from
each control's type — resetting returns the control to its *initial* value instead of
`null` — so `const { description, amount, category } = form.getRawValue()` is fully
typed with no nullability to check, matching how the values are actually used (e.g.
passed straight into `ExpenseService.add()`, which expects real strings/numbers).

**Q: How does form validation work, and how do you decide when to show an error to the
user?** ⭐ Attach `Validators.x` (built-in: `required`, `min`/`max`, `email`,
`pattern`, `minLength`/`maxLength`) or a custom validator function
(`(control) => ValidationErrors | null`) to a control. Angular tracks `valid`/`invalid`
on both the control and the enclosing group, plus state flags: `touched` (blurred at
least once), `dirty` (value changed from initial), `pristine`, and `pending` (an async
validator is running). The typical pattern is to only show an error once a control is
both `invalid` *and* `touched` (or `dirty`) — showing "required" immediately on page
load, before the user has interacted with the field, is poor UX. Disabling the submit
button on `form.invalid` (as `add-expense.html` does with `[disabled]="form.invalid"`)
is the simplest form of validation-driven UX and doesn't depend on touched/dirty at
all.

**Q: What's a `FormArray`, and when would you reach for one?**
The third control type alongside `FormGroup` and `FormControl` — it holds a *dynamic
list* of controls, added or removed at runtime with `.push(control)`/`.removeAt(i)`.
Use it for a variable-length set of inputs the user can add to — line items on an
invoice, a list of tags, multiple phone numbers — where a fixed `FormGroup` with named
keys doesn't fit because the number of controls isn't known ahead of time.

*Follow-up: "How would you validate that a `FormArray` has at least one entry?"* — A
group- or array-level custom validator (rather than a per-control one) checking
`formArray.length > 0`, attached where the `FormArray` itself is constructed, since no
individual control's own validity can express "the list as a whole must be non-empty."

**Q: Why choose Reactive Forms specifically for a complex, dynamic form?** *nuance*
Because the entire form state lives in TypeScript you fully control: you can add/remove
controls programmatically (`FormArray`), express cross-field validation as a
group-level validator function, subscribe to `form.valueChanges` (an Observable) to
react to changes with RxJS operators (e.g. debounce, derive a computed total), and unit
test all of that without ever rendering a template. Template-driven forms' model is
inferred from markup, so equivalent dynamic-field or cross-field logic ends up fighting
the framework rather than being expressed directly in code.
