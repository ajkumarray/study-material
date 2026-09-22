<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · tailwind](../phase-2-tailwind/NOTES.md)
<!-- /nav -->

# Phase 3 — Bootstrap (Component-Based): Notes

Bootstrap gives you **pre-built, opinionated components** and a **responsive grid**, all
styled by applying class names — you assemble a polished UI fast without designing it from
scratch. Everything below is grounded in the real, installed Bootstrap 5.3.8 dist at
`11-css-frameworks/bootstrap-demo/` — `index.html` references the actual
`node_modules/bootstrap/dist/css/bootstrap.min.css` and `.../js/bootstrap.bundle.min.js`,
and includes a genuinely working modal.

## What Bootstrap Is: Component-First

Where Tailwind (phase 2) gives you primitives and lets you build components yourself,
Bootstrap gives you **finished, named components** — the opposite bet.

- You add a class like `btn btn-primary` or `card` and get a fully styled, accessible,
  cross-browser-tested UI element for free — no composing primitives, no design decisions
  about padding/color/border-radius left to you.
- Bootstrap **also** ships a smaller utility layer (`d-flex`, `mb-4`, `text-secondary`) for
  spacing/layout tweaks around its components, so it isn't *purely* component-only — but
  the components are the headline feature and what most people reach for Bootstrap for.
- This makes Bootstrap **component-first with utilities**, the mirror image of Tailwind's
  **utilities-only** approach (Tailwind has no pre-built "card" component at all — you
  build one from primitives, as the tailwind-demo does).

## The Grid System

Bootstrap's grid is arguably its most enduring, widely copied contribution: a predictable,
responsive **container → row → columns** structure on a conceptual 12-column grid.

- **`container`**: centers content and constrains its max-width at each breakpoint (so text
  lines don't stretch edge-to-edge on a wide monitor). **`container-fluid`** is always
  100% width instead.
- **`row`**: a horizontal wrapper for columns; internally implemented with flexbox, and
  applies negative margins that offset the columns' padding so gutters (the gaps between
  columns) line up correctly.
- **`col-*`**: a column that claims some number out of 12 grid units. `col-12` = full
  width (all 12 units); `col-6` = half width; `col-4` = a third.
- **Responsive column classes**: `col-md-6` means "become 6/12 width starting at the `md`
  breakpoint (768px) and up; below that, fall back to whatever smaller-breakpoint or
  unprefixed class is present (or full-width by default)." This is the same mobile-first
  philosophy as phase 1/2 — the unprefixed or smallest class is the base, and larger
  breakpoints adjust upward.

```html
<div class="container">
  <div class="row">
    <div class="col-12 col-md-6">Left half at md+, full width below</div>
    <div class="col-12 col-md-6">Right half at md+, full width below</div>
  </div>
</div>
```

The demo's `bootstrap-demo/index.html` uses the container/grid pattern too, though for a
single-column card layout: `<main class="container py-5" style="max-width: 32rem;">` — a
centered container with vertical padding (`py-5`), further constrained with an inline
`max-width` for this particular narrow expense-list layout.

In this example: on a phone (`<768px`), both columns stack full-width, one above the other
(`col-12` applies, `col-md-6` doesn't kick in yet); at `≥768px`, they sit side by side,
each taking half the row. No custom CSS was written — the entire responsive two-column
layout comes from four class names.

## Components & Utility Classes

- **Components are the headline feature**: `btn btn-primary` (button), `card`,
  `list-group` / `list-group-item`, `navbar`, `modal`, `alert`, `dropdown`, `badge`, and
  form controls (`form-control`, `form-label`). Adding the class gives you a consistent,
  accessible, cross-browser-styled element immediately.
- The demo's entire expense card is built from exactly this component set:
  ```html
  <span class="badge text-bg-success fs-6">Total $1,042.50</span>

  <ul class="list-group shadow-sm">
    <li class="list-group-item d-flex justify-content-between align-items-center py-3">
      <div>
        <div class="fw-medium">Lunch</div>
        <small class="text-secondary">food</small>
      </div>
      <span class="fw-semibold">$12.50</span>
    </li>
    <!-- ... -->
  </ul>

  <button type="button" class="btn btn-primary mt-4"
          data-bs-toggle="modal" data-bs-target="#addModal">
    + Add expense
  </button>
  ```
  `badge text-bg-success` is a ready-made "pill" label component with a semantic success
  color; `list-group`/`list-group-item` is a ready-made bordered-row list container; `btn
  btn-primary` is the canonical Bootstrap button, styled consistently everywhere it's used.
- **Utility classes** exist alongside components, for the smaller spacing/flex/color
  adjustments that don't warrant a whole component: `d-flex` (`display: flex`),
  `justify-content-between`, `align-items-center`, `mb-4`/`mt-4`/`py-3` (margin/padding
  scale, same idea as Tailwind's `p-4` but Bootstrap's own numbered scale), `text-bg-
  success` (background + matching contrasting text color together), `fw-medium`/
  `fw-semibold` (font-weight), `text-secondary` (muted text color), `fs-6` (font-size
  step). This is a real, if smaller, utility layer — Bootstrap didn't invent utility
  classes, it just isn't utility-*only* the way Tailwind is.

| Bootstrap utility | Raw CSS |
|---|---|
| `d-flex` | `display: flex;` |
| `justify-content-between` | `justify-content: space-between;` |
| `align-items-center` | `align-items: center;` |
| `mb-4` | `margin-bottom: 1.5rem;` (Bootstrap's own spacing scale, `0`–`5`) |
| `py-3` | `padding-block: 1rem;` |
| `fw-semibold` | `font-weight: 600;` |
| `text-secondary` | `color: var(--bs-secondary-color);` |

## Interactive Components Need JavaScript

- **Static components** (cards, the grid, buttons as plain elements, badges) are pure CSS —
  they need nothing but the stylesheet.
- **Interactive components** — modal, dropdown, collapse, tooltip, carousel, toast — need
  Bootstrap's **JS bundle**, which also bundles **Popper** (a positioning library used for
  dropdowns/tooltips/popovers so they place themselves correctly relative to their trigger
  element, flipping to stay on-screen near viewport edges).
- They're driven declaratively via **`data-bs-*` attributes**, with no hand-written
  JavaScript required for the common case:
  ```html
  <button type="button" class="btn btn-primary" data-bs-toggle="modal" data-bs-target="#addModal">
    + Add expense
  </button>

  <div class="modal fade" id="addModal" tabindex="-1" aria-hidden="true">
    <div class="modal-dialog">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">Add expense</h5>
          <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
        </div>
        <div class="modal-body">...</div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
          <button type="button" class="btn btn-primary">Save</button>
        </div>
      </div>
    </div>
  </div>

  <script src="./node_modules/bootstrap/dist/js/bootstrap.bundle.min.js"></script>
  ```
  This is exactly `bootstrap-demo/index.html`'s modal. `data-bs-toggle="modal"` +
  `data-bs-target="#addModal"` on the trigger button tells Bootstrap's JS which modal
  element (`id="addModal"`) to open when clicked; `data-bs-dismiss="modal"` on any button
  inside the modal closes it. None of this requires writing a click handler yourself — the
  JS bundle, once loaded, wires all `data-bs-*`-annotated elements up automatically.
- A JavaScript API also exists (`new bootstrap.Modal(el).show()`) for programmatic control
  when you need to open/close a component from your own application logic rather than a
  direct user click.

**Why it's useful:** you get a fully accessible (focus-trapping, `aria-*`-wired, keyboard-
dismissible with `Esc`) modal/dropdown/tooltip without writing or testing that
accessibility logic yourself — a real, non-trivial amount of work Bootstrap has already
solved and battle-tested across browsers.

## Customization

- **Theming via Sass (the primary, supported way to rebrand Bootstrap)**: Bootstrap is
  authored in Sass with variables for everything — `$primary`, `$secondary`,
  `$border-radius`, spacing maps, breakpoints. You override these variables in your own
  Sass file *before* importing Bootstrap's source, then compile — this regenerates every
  component's CSS using your values, so `btn-primary` genuinely becomes your brand color
  everywhere, not just where you remembered to override it.
- **CSS custom properties**: Bootstrap 5 also exposes many values as CSS variables at
  runtime (`--bs-primary`, `--bs-border-radius`, `--bs-body-bg`, ...), which is a lighter-
  weight way to retheme without a Sass build step — you can override `--bs-primary` in your
  own stylesheet or inline. This mechanism is also what powers Bootstrap's **built-in dark
  mode**: setting `data-bs-theme="dark"` on the `<html>` (or a subtree) swaps the CSS
  variable values to dark-appropriate colors.
- **The Utilities API**: a Sass-level configuration point for generating your *own* custom
  utility classes (or removing ones you don't use) from Bootstrap's build system, if the
  built-in utility layer doesn't cover something you need.

## The Trade-off (Stated Honestly)

| | Pros | Cons |
|---|---|---|
| **Speed** | Extremely fast to a polished, consistent, accessible UI | — |
| **Component breadth** | Huge, battle-tested component set (modals, dropdowns, navbars, forms) | The utility layer is smaller/less complete than Tailwind's |
| **Design skill required** | Minimal — components already look good by default | The default look is generic — "every Bootstrap site looks the same" unless you invest in Sass theming |
| **CSS payload** | — | Larger — you ship a lot of component CSS you may not use (a custom Sass build can subset it) |
| **Customization** | Sass variables + CSS custom properties for real theming | Deep, bespoke customization fights the framework's built-in opinions |

## Perspective

Bootstrap optimizes for **speed to a conventional, polished result**: grab pre-built
components and a proven responsive grid, and you have a working, accessible UI in minutes,
with no design system to invent yourself. The cost is a somewhat generic aesthetic unless
you invest in Sass theming, and a heavier CSS payload than a purpose-built utility build. It
is the opposite bet from Tailwind — *give me finished components* vs *give me primitives to
assemble myself* — and the right call whenever consistency and delivery speed matter more
than a fully bespoke brand: internal tools, admin dashboards, prototypes, and teams without
dedicated front-end/design resources.

## Summary / Key Takeaways

- Bootstrap is **component-first**: `btn btn-primary`, `card`, `list-group`, `modal` give
  you finished, accessible, cross-browser UI pieces by class name alone, plus a smaller
  utility layer (`d-flex`, `mb-4`) for spacing/layout tweaks.
- The grid is **container → row → col-\***, a 12-column, mobile-first, flexbox-based
  system: `col-12 col-md-6` is full width on phones and half width at `≥768px`.
- **Static components are CSS-only; interactive ones (modal, dropdown, tooltip, collapse)
  need the JS bundle** (which includes Popper) and are wired declaratively via
  `data-bs-toggle` / `data-bs-target` / `data-bs-dismiss` attributes — no hand-written click
  handlers needed for the common case.
- Real theming happens via **Sass variable overrides** (`$primary`, `$border-radius`)
  recompiled from source, or lighter-weight via **CSS custom properties** (`--bs-primary`)
  at runtime — the same mechanism that drives Bootstrap's built-in `data-bs-theme="dark"`
  dark mode.
- The trade-off is speed and consistency (Bootstrap) vs. a fully custom design with minimal
  shipped CSS (Tailwind, phase 2) — pick based on whether the team has design capacity and
  needs a bespoke brand, or needs a polished result fast.
