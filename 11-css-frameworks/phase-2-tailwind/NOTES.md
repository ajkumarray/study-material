<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · foundations](../phase-1-foundations/NOTES.md) | [Phase 3 · bootstrap ➡](../phase-3-bootstrap/NOTES.md)
<!-- /nav -->

# Phase 2 — Tailwind CSS (Utility-First): Notes

Tailwind gives you a large library of **single-purpose utility classes** mapped to a
**constrained design system**, and you compose them directly in your markup to build any
design — with essentially no hand-written CSS and no naming things. Everything in this
phase is grounded in the real, working demo at `11-css-frameworks/tailwind-demo/` —
`index.html` is the markup, `src/input.css` is the one-line source stylesheet, and
`dist/output.css` is the actual compiled, tree-shaken CSS (Tailwind v4.3.3, ~441 lines /
~12 KB) produced by `npm run build`.

## The Utility-First Idea

**Utility-first CSS** means you style an element by attaching many small classes that each
do exactly **one thing**, instead of writing a custom CSS rule with many declarations.

- **One class, one job**: `flex` sets `display: flex`, `p-4` sets `padding: 1rem`,
  `bg-white` sets `background-color: white`, `rounded-lg` sets a large `border-radius`,
  `shadow-sm` applies a small box-shadow. You compose several of these on one element to
  produce a full design.
- **You almost never write custom CSS or invent class names.** This removes an entire
  category of team friction — no more debates over whether something is
  `.card__header--active` or `.CardHeaderActive`, and no more dead CSS classes left behind
  after a component is deleted (because there's no per-component CSS file to forget to
  clean up).
- **Values come from a constrained scale**, not arbitrary numbers. Spacing utilities
  (`p-1`, `p-2`, `p-4`, ... up to `p-96`) map to a fixed spacing scale (Tailwind's default:
  `0.25rem` increments); colors come from a fixed palette (`slate-100`...`slate-900`,
  `indigo-600`, `emerald-700`, etc.). This means a whole team's spacing and colors stay
  consistent **by construction** — a design system that's enforced by the tool, not by
  code review discipline.

```html
<!-- From tailwind-demo/index.html — one "card" is a bundle of utilities: -->
<li class="flex items-center justify-between bg-white rounded-lg shadow-sm
           ring-1 ring-slate-200 p-4 hover:shadow-md transition-shadow">
  <div>
    <p class="font-medium">Lunch</p>
    <p class="text-sm text-slate-500">food</p>
  </div>
  <p class="font-semibold text-slate-900">$12.50</p>
</li>
```

In this example: `flex items-center justify-between` lays the row out with the description
on the left and the price on the right, vertically centered; `bg-white rounded-lg shadow-sm
ring-1 ring-slate-200` gives it the "card" look (white background, rounded corners, subtle
shadow, a thin border via `ring`); `p-4` adds internal padding; `hover:shadow-md
transition-shadow` deepens the shadow smoothly on hover. Every visual detail of the card is
readable directly from the class list — there's no separate `.css` file to cross-reference
to understand how this element looks.

### Common Utility Categories

| Category | Example classes (from the demo) | Raw CSS equivalent |
|---|---|---|
| Layout / display | `flex`, `min-h-screen` | `display: flex;`, `min-height: 100vh;` |
| Flex alignment | `items-center`, `justify-between` | `align-items: center;`, `justify-content: space-between;` |
| Spacing (padding/margin) | `p-4`, `p-6`, `mb-4`, `mt-6`, `px-3`, `py-1` | `padding: 1rem;`, `margin-bottom: 1rem;`, etc. |
| Spacing between children | `space-y-3` | adds `margin-top` to each sibling but the first |
| Sizing | `max-w-md`, `w-full`, `mx-auto` | `max-width: 28rem;`, `width: 100%;`, `margin-inline: auto;` |
| Typography | `text-2xl`, `text-sm`, `font-bold`, `font-medium`, `font-semibold`, `tracking-tight` | `font-size`, `font-weight`, `letter-spacing` |
| Color | `bg-slate-100`, `text-slate-800`, `text-emerald-700` | `background-color`, `color` (from the fixed palette) |
| Borders / effects | `rounded-lg`, `rounded-full`, `shadow-sm`, `ring-1 ring-slate-200` | `border-radius`, `box-shadow`, a 1px inset "border" via box-shadow |
| Transitions | `transition-shadow` | `transition-property: box-shadow; ...` |

## Variants: Responsive, State, and Dark Mode

Utilities gain real power through **variant prefixes** — a colon-separated prefix that
conditions when a utility applies. This is the capability inline styles fundamentally
cannot offer.

- **Responsive variants** (`sm:` `md:` `lg:` `xl:` `2xl:`): apply the utility only at that
  breakpoint and up — mobile-first, exactly like the raw `min-width` media queries from
  phase 1. In the demo's button:
  ```html
  <button class="mt-6 w-full sm:w-auto ...">+ Add expense</button>
  ```
  `w-full` (`width: 100%`) is the base/mobile style; `sm:w-auto` only overrides it to
  `width: auto` once the viewport is `≥640px`. Read left-to-right, the class list tells you
  exactly how the element behaves at each screen size.
- **State variants** (`hover:` `focus:` `active:` `disabled:`): apply only in that
  interaction state. The demo's cards use `hover:shadow-md` (deepen the shadow on mouse
  hover); the "Add expense" button uses
  `focus:outline-none focus:ring-2 focus:ring-indigo-400` (replace the default focus
  outline with a custom focus ring, for accessibility, when the button is keyboard-focused)
  and `active:bg-indigo-800` (a darker shade while the button is being clicked).
- **Dark mode** (`dark:`): `dark:bg-slate-800` applies only when dark mode is active
  (based on the user's OS preference or a manual toggle, depending on configuration).
- **Group and peer variants**: `group-hover:` styles a child element when a *parent*
  marked `group` is hovered (e.g., reveal an icon inside a card only when the whole card is
  hovered); `peer-invalid:` styles an element based on a **sibling** form input's validity
  state (e.g., show an error message only when the adjacent `<input>` is invalid).
- **Stacking prefixes**: variants compose left to right — `sm:hover:bg-indigo-700` means
  "at `≥640px` viewport width, when hovered, set this background."

```html
<!-- Every distinct interaction state the button can be in, expressed declaratively: -->
<button class="w-full sm:w-auto
               bg-indigo-600 hover:bg-indigo-700 active:bg-indigo-800
               text-white font-medium rounded-lg px-4 py-2
               focus:outline-none focus:ring-2 focus:ring-indigo-400">
  + Add expense
</button>
```

In this example: the button is full-width on phones and shrinks to fit its content at
`sm:` and above; its background is `indigo-600` at rest, darkens one shade on `hover`, and
darkens further while `active` (mid-click); and when it receives keyboard focus, the
browser's default outline is suppressed in favor of a visible indigo focus ring. Five
distinct visual states, all specified inline, with no JavaScript and no separate
`:hover`/`:focus`/`:active` CSS rules to maintain.

**Why it's useful:** you can read an element's *entire* interactive behavior — its base
style, its responsive behavior, and every hover/focus/active/dark-mode state — from one
`class` attribute, without opening a separate stylesheet and hunting for the matching
selector. This is a major real-world debugging and code-review win: the styling is exactly
where the element is.

## The Design System: Theme Tokens & Configuration

- **Theme tokens** are the underlying scale (colors, spacing, font sizes, breakpoints,
  border radii) that every utility class draws its value from — this is what makes
  utility-first CSS a *design system*, not just a big bag of unrelated classes.
- **Tailwind v4 (what this demo uses) is CSS-first for configuration.** The entire source
  stylesheet is:
  ```css
  /* tailwind-demo/src/input.css */
  @import "tailwindcss";
  @source "./index.html";
  ```
  `@import "tailwindcss"` pulls in Tailwind's default theme and base styles.
  `@source "./index.html"` tells Tailwind's scanner exactly which file(s) to scan for
  utility class names — this is what makes the tree-shaken build below possible. (Tailwind
  v3 instead used a JavaScript `tailwind.config.js` file to configure the theme and
  content-scanning paths; v4 replaced that with `@import`/`@theme`/`@source` directives
  directly in CSS.)
- **Custom theme tokens** are declared with an `@theme` block in your CSS, e.g.
  `@theme { --color-brand: oklch(60% 0.15 250); }`, and Tailwind automatically generates
  matching utilities for it (`bg-brand`, `text-brand`, `border-brand`, ...). The generated
  `dist/output.css` shows this same mechanism at work for the *built-in* palette — every
  color and spacing value the demo uses is emitted as a CSS custom property inside a
  `@layer theme { :root { --color-indigo-600: oklch(51.1% 0.262 276.966); ... } }` block at
  the top of the file, and the utility classes below reference those variables.
- **Arbitrary values** are the escape hatch for one-off values outside the design scale:
  `top-[117px]`, `bg-[#1da1f2]`, `w-[calc(100%-2rem)]`. This keeps you from having to drop
  out of Tailwind into custom CSS for a single unusual value, while still visually flagging
  (via the square brackets) that it's a one-off, not part of the shared scale.
- **`@apply`** folds a set of utilities into one reusable custom class, for cases where a
  pattern is genuinely repeated at the CSS level rather than the component level:
  ```css
  .btn {
    @apply px-4 py-2 rounded-lg font-medium bg-indigo-600 text-white hover:bg-indigo-700;
  }
  ```
  This is handy for things like form control styling shared across many places that
  *aren't* naturally one component (e.g., third-party markup you can't easily componentize).
  In a React/Angular codebase, though, **extracting a component** (`<Button>`) is almost
  always preferred over `@apply` — it keeps the utility list in exactly one place (the
  component definition) and lets you pass props for variants, whereas `@apply` just gives
  you another CSS class name to remember and keep in sync.

## The Build & Tree-Shaking

This is the part of Tailwind that's easy to get wrong in theory and is fully verifiable in
this repo's demo:

- Tailwind **scans your source files** (per the `@source` directive, or its default
  content-detection heuristics) for the utility class names actually *used*, and generates
  CSS **only for those classes** — nothing else. Tailwind ships with literally thousands of
  possible utility combinations, but none of the unused ones make it into your CSS bundle.
- Verify it yourself:
  ```bash
  cd 11-css-frameworks/tailwind-demo
  npm install
  npm run build   # tailwindcss -i ./src/input.css -o ./dist/output.css
  ```
  The resulting `dist/output.css` is ~441 lines / roughly 12 KB, and every utility class in
  it (`flex`, `items-center`, `bg-white`, `rounded-lg`, `hover:shadow-md`, `sm:w-auto`, ...)
  corresponds exactly to a class actually written in `index.html` — nothing more. If you add
  a new utility class to the markup and rerun `npm run build`, the output grows by exactly
  that class's generated rule; if you delete a class from the markup and rebuild, its rule
  disappears from the output.
- This is *why* the CSS payload for a Tailwind app doesn't grow uncontrollably as the app
  grows — unlike a hand-rolled stylesheet, where old rules tend to accumulate because no one
  is sure it's safe to delete them, Tailwind's output is regenerated from scratch every
  build based only on what markup currently references.
- Tailwind v4's build engine is a fast, purpose-built CSS compiler (not PostCSS-plugin-based
  like older versions), and official integrations exist for Vite, PostCSS, Next.js, and
  Angular so this scanning/generation step is invisible inside a normal app build pipeline
  — you don't run the CLI by hand in a real app, a bundler plugin does it as part of `npm
  run build`/`npm run dev`.

## The Trade-off (Stated Honestly)

| | Pros | Cons |
|---|---|---|
| **Development speed** | Compose a design directly in markup with no context-switching to a `.css` file | Learning curve for the utility class vocabulary before you're fast |
| **Consistency** | Every value comes from a shared, constrained design-token scale | — |
| **Shipped CSS size** | Tree-shaken — only classes actually used are generated (~12 KB in this demo) | — |
| **Markup readability** | An element's full style/state is visible in one place | Long `class` strings can look "ugly" and make markup noisy/hard to scan |
| **Reuse** | — | Repeating the same long utility bundle across many elements, unless you extract a component |

The **verbosity objection is real** but is largely solved by componentization: in a
React/Angular/Vue app you write the utility bundle **once**, inside the component
definition (`<Card>`, `<Button>`), and every usage site is just `<Button>Save</Button>` —
the "ugly markup" lives in exactly one file, not scattered across every call site.

## Perspective

Tailwind bets that for component-based UIs, styling belongs **next to the markup** it
styles rather than in a separate file, and that it should be drawn from a **constrained
design system** rather than free-form values — with a build step that can safely purge
everything that ends up unused. In exchange for a busier-looking `class` attribute, you get
speed (no naming, no context-switching), consistency (a shared token scale enforced by the
tool), and a stylesheet that stays small regardless of how large the app grows. Pair it with
component extraction in React/Angular (the same pattern you already use for reusing
markup), and the verbosity objection mostly disappears — the utility bundle for any given
visual pattern lives in exactly one place.

## Summary / Key Takeaways

- Utility-first means composing many single-purpose classes (`flex`, `p-4`, `bg-white`) in
  markup instead of writing custom CSS rules — values come from a fixed design-token scale,
  not arbitrary numbers.
- Variant prefixes (`sm:`, `hover:`, `dark:`, `group-hover:`) express responsive, state, and
  dark-mode behavior declaratively, and stack (`sm:hover:bg-indigo-700`) — something inline
  styles cannot do.
- Tailwind v4 configures via CSS (`@import "tailwindcss"`, `@theme`, `@source`) rather than
  a JS config file (that was v3); custom tokens automatically become new utilities.
- The build **scans your markup and generates only the utilities actually used** — this is
  why the shipped CSS stays small (~12 KB in the demo) and doesn't grow unboundedly as the
  app grows, unlike accumulating hand-written CSS.
- The real cost is verbose markup and a class-name learning curve; the standard fix is
  **extracting a component** so the utility bundle is written once and reused everywhere,
  with `@apply` as a narrower CSS-level escape hatch for non-componentized repetition.
