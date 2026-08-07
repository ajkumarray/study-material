<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · foundations](../phase-1-foundations/NOTES.md) | [Phase 3 · bootstrap ➡](../phase-3-bootstrap/NOTES.md)
<!-- /nav -->

# Phase 2 — Tailwind CSS (Utility-First): Notes

Tailwind gives you a large set of **single-purpose utility classes** mapped to a **design
system**, and you compose them in your markup to build any design — no writing CSS, no
naming things. The `tailwind-demo/` in this track is a real, tree-shaken build.

## 2.1 — The utility-first idea

- Each class does **one thing**: `flex`, `p-4` (padding), `bg-white`, `text-sm`,
  `rounded-lg`, `shadow-sm`. You **compose** them on an element to produce a design
  (see any `<li>` in the demo).
- You almost never write custom CSS or invent class names — the biggest maintenance win.
  No more `.card__header--active` naming debates or dead CSS accumulating.
- Values come from a **constrained scale** (spacing `p-1…p-96`, a fixed color palette),
  so a team's spacing/colors stay consistent by construction — a design system baked in.

## 2.2 — Variants: responsive, state, dark mode

Utilities gain power through **variant prefixes** (things inline styles can't do):

- **Responsive:** `sm: md: lg: xl:` apply at breakpoints, mobile-first
  (`w-full sm:w-auto` = full width on mobile, auto ≥640px — in the demo's button).
- **State:** `hover:` `focus:` `active:` `disabled:` `focus:ring-2` (the demo's cards use
  `hover:shadow-md`, the button uses `focus:ring-2`).
- **Dark mode:** `dark:bg-slate-800` applies under a dark theme.
- **Group/peer:** `group-hover:` styles a child when a parent is hovered.

Prefixes stack: `sm:hover:bg-indigo-700`. This declarative composition is why utilities
beat inline styles.

## 2.3 — The design system: theme, config, `@apply`

- **Theme tokens** define the scale (colors, spacing, fonts, breakpoints). In **Tailwind
  v4** (this demo) configuration is **CSS-first**: `@import "tailwindcss";` plus
  `@theme { --color-brand: …; }` in your CSS (v3 used a `tailwind.config.js`). Custom
  tokens become utilities (`bg-brand`).
- **Arbitrary values** for one-offs outside the scale: `top-[117px]`, `bg-[#1da1f2]` —
  an escape hatch that keeps you from dropping to custom CSS.
- **`@apply`** folds utilities into a custom class (`.btn { @apply px-4 py-2 rounded … }`)
  — useful for a genuinely repeated pattern like form inputs, though **extracting a
  component** (React/Angular) is usually the better DRY answer.

## 2.4 — The build & tree-shaking

- Tailwind **scans your files** for class names actually used and generates **only those**
  utilities into the output CSS. The demo's `dist/output.css` is ~12 KB because it
  contains only the classes in `index.html` — not the thousands Tailwind *could* produce.
  This is why shipped CSS stays small and doesn't grow as the app grows.
- v4's engine is fast; integrations exist for Vite/PostCSS/Next/Angular so the build is
  transparent in a real app. `@source` tells the scanner where to look (the demo points
  it at `index.html`).

## The trade-off (state it honestly)

- **Pros:** rapid development, consistent design tokens, tiny purged CSS, no naming, no
  dead CSS, styles colocated with markup, easy responsive/state variants.
- **Cons:** "ugly"/verbose markup (long `class` strings), a learning curve for the class
  names, and repetition unless you extract components. The verbosity objection is real
  but largely dissolved by componentization — you write the utility bundle once per
  component.

## Perspective

Tailwind bets that for component-based apps, styles belong **next to the markup** and
should draw from a **constrained system**, and that a build step can purge everything
unused. In exchange for busier markup you get speed, consistency, and a small stylesheet.
Pair it with your React/Angular components (extract repeated utility bundles into
components) and the verbosity objection mostly disappears.
