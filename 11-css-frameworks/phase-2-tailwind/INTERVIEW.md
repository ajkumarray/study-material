<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · foundations](../phase-1-foundations/NOTES.md) | [Phase 3 · bootstrap ➡](../phase-3-bootstrap/NOTES.md)
<!-- /nav -->

# Phase 2 — Tailwind CSS: Interview Q&A

⭐ = asked constantly.

**Q: What is utility-first CSS / Tailwind?** ⭐⭐
An approach to styling where you compose many small, single-purpose classes directly in
the markup — `flex`, `p-4`, `bg-white`, `rounded-lg` — instead of writing custom CSS rules
with hand-picked class names. Tailwind is the library that provides this large set of
utilities, each mapped to a value from a **constrained design system** (a fixed spacing
scale, a fixed color palette, fixed breakpoints), so you can build essentially any design
from these primitives without writing new CSS or inventing class names.

```html
<li class="flex items-center justify-between bg-white rounded-lg shadow-sm p-4">
  <p class="font-medium">Lunch</p>
  <p class="font-semibold">$12.50</p>
</li>
```

**Q: How is Tailwind different from inline styles? Isn't `class="flex p-4 bg-white"`
basically the same as `style="display:flex; padding:1rem; background:white"`?** ⭐⭐
They look superficially similar but differ in every way that matters. Utility values come
from a **constrained design-token scale** — `p-4` is always `1rem` everywhere in the app —
whereas inline styles let anyone write any arbitrary number, with zero enforced
consistency. Utilities support **responsive and state variants** (`sm:`, `hover:`,
`focus:`, `dark:`) that inline styles structurally cannot express — there's no such thing
as an inline `:hover` declaration. Utility classes compile into **one shared,
browser-cacheable stylesheet** loaded once, while inline styles are duplicated inline on
every single element and can never be cached separately. And utilities that end up unused
are **purged** from the production build, while inline styles always ship exactly what's
written.

**Q: How do responsive variants work in Tailwind, and what does "mobile-first" mean here
specifically?** ⭐⭐
Prefixes like `sm:`, `md:`, `lg:`, `xl:`, `2xl:` apply a utility starting at that
breakpoint's `min-width` and up. The **unprefixed** utility is the base style that always
applies (conceptually, the "mobile" style, since there's no `xs:` prefix — you write the
smallest-screen style with no prefix at all). For example, `class="w-full sm:w-auto"`:
`width: 100%` applies unconditionally; `sm:w-auto` overrides it to `width: auto` only once
the viewport is `≥640px`. This is exactly the mobile-first pattern from raw CSS
(`min-width` media queries layering on top of a base rule), just expressed as class
prefixes instead of a separate `@media` block.

*Follow-up: what would `md:hidden lg:flex` do, and where have you seen this pattern?*
`md:hidden` hides the element from `768px` up (it's visible below that);
`lg:flex` then re-shows it as a flex container from `1024px` up. This exact stacking —
hide at the middle breakpoint, bring back at a larger one — is the standard way to swap a
mobile hamburger menu for a full nav bar: `flex md:hidden` (mobile nav, hidden on tablet+)
alongside `hidden md:flex` (desktop nav, hidden on mobile).

**Q: How do state variants like `hover:` and `focus:` work, and can they stack with
responsive variants?** ⭐
State variant prefixes apply a utility only in that interaction pseudo-class:
`hover:bg-indigo-700` only applies while the element is hovered; `focus:ring-2` only while
it has keyboard/click focus; `active:bg-indigo-800` only during a click; `disabled:opacity-50`
only when the element is disabled. They stack with responsive prefixes in a fixed left-to-
right order: `sm:hover:bg-indigo-700` means "at `≥640px`, when hovered, apply this
background." Order matters — `sm:hover:` and `hover:sm:` are not valid Tailwind syntax;
the responsive/media-query-like variant always comes first.

**Q: How does Tailwind keep the shipped CSS small despite having thousands of possible
utility classes?** ⭐⭐
Tailwind **scans your source files** for the utility class names you actually wrote (in
this repo's demo, via the `@source "./index.html"` directive in `src/input.css`) and
generates CSS rules **only for those classes** — none of the thousands of other possible
utilities are emitted. Running `npm run build` in `tailwind-demo/` compiles
`src/input.css` (2 lines) into `dist/output.css` (~441 lines / ~12 KB), and every rule in
that output maps to a class actually present in `index.html`. This is why the CSS bundle
doesn't grow unboundedly as an app grows the way an accumulating hand-written stylesheet
tends to — the output is regenerated from what's *currently* referenced, every build, not
appended to over time.

**Q: What's the point of `@apply`, and when would you use it instead of a component?**
`@apply` folds a set of utilities into one reusable custom CSS class:
`.btn { @apply px-4 py-2 rounded-lg bg-indigo-600 text-white; }`. It's useful for a pattern
that's repeated at the *CSS* level in places you can't easily turn into a shared component
— e.g. styling markup generated by a third-party library. In a normal React/Angular
codebase, though, **extracting a component** (`<Button>`) is almost always preferred: it
keeps the utility list in exactly one place, lets you add props for variants (`variant=
"secondary"`), and doesn't introduce a second, CSS-level naming system running parallel to
your component system.

**Q: How do you customize Tailwind's design system — add a brand color, a custom spacing
value?** ⭐
Define theme tokens. In **Tailwind v4** (used by this repo's demo) that's done **in CSS**
via an `@theme` block: `@theme { --color-brand: oklch(60% 0.15 250); }` — Tailwind then
auto-generates `bg-brand`, `text-brand`, `border-brand`, etc. from that one token. In
**Tailwind v3**, the same customization lived in a JavaScript `tailwind.config.js` file's
`theme.extend` object instead — v4's CSS-first `@theme`/`@import`/`@source` directives
replaced that config file. For one-off values that don't belong in the shared scale at
all, **arbitrary value** syntax is the escape hatch: `bg-[#1da1f2]`, `top-[117px]`.

**Q: What are the downsides of Tailwind?** ⭐
Verbose, busy-looking markup — long `class` attributes are the most common objection — plus
a real learning curve for the utility vocabulary before you're fast with it, and repetition
of the same utility bundle across elements if you don't extract components. Editor tooling
(the official IntelliSense extension, class sorting) mitigates the ergonomics somewhat, but
the verbosity is a genuine, valid trade-off, not a myth.

**Q: How do you avoid repeating the same long utility class list across many elements?**
*nuance*
The primary answer is **extracting a component**: write the utility bundle once inside
`<Card>` or `<Button>` (React/Angular/Vue), and every call site is just `<Card>...</Card>`
— the "verbose markup" now lives in exactly one file. `@apply` is a narrower, CSS-level
alternative for cases that don't map cleanly onto a component boundary. Since most
real apps are already organized into components for reasons unrelated to CSS (reuse,
testability, props), the utility-bundle-duplication objection mostly dissolves once you're
already componentizing — which you would be doing anyway in a React or Angular app.

*Follow-up: does using Tailwind change how you think about component boundaries?* Not
fundamentally — you still extract components for the same reasons (reusable, encapsulated
units of markup + behavior). Tailwind just means the "style" concern also lives inside that
same component boundary instead of a separate `.css` file per component, which is exactly
the colocation argument from phase 1.
