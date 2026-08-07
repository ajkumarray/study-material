<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · tailwind](../phase-2-tailwind/NOTES.md)
<!-- /nav -->

# Phase 3 — Bootstrap (Component-Based): Notes

Bootstrap gives you **pre-built, opinionated components** and a **responsive grid**,
styled by class name. You assemble a polished UI fast without designing from scratch. The
`bootstrap-demo/` uses the real Bootstrap 5.3 dist, including a working modal.

## 3.1 — The grid system

- Layout is **container → row → columns** on a **12-column** grid:
  ```html
  <div class="container"><div class="row">
    <div class="col-12 col-md-6">…</div>
    <div class="col-12 col-md-6">…</div>
  </div></div>
  ```
- Columns are responsive: `col-md-6` = half width at ≥768px, full (`col-12`) below —
  mobile-first, like Tailwind. The grid is built on flexbox with `gap`/gutters.
- `container` centers and constrains width per breakpoint; `container-fluid` is full-width.
  This grid is Bootstrap's most enduring contribution — a predictable responsive layout
  system.

## 3.2 — Components & utilities

- **Components** are the headline: `btn btn-primary`, `card`, `list-group`, `navbar`,
  `modal`, `alert`, `dropdown`, `badge`, form controls (`form-control`, `form-label`).
  Add the classes and you get a consistent, accessible, cross-browser component — the
  demo builds the whole expense card from `list-group`, `badge`, `btn`, and a `modal`.
- **Utility classes** also exist (`d-flex`, `justify-content-between`, `mb-4`, `text-bg-
  success`, `fw-semibold`) — a smaller utility layer for spacing/flex/color. So Bootstrap
  is *component-first with utilities*, whereas Tailwind is *utilities only*.
- **Interactive components** (modal, dropdown, collapse, tooltip) need Bootstrap's **JS
  bundle** (which includes Popper for positioning). They're driven by `data-bs-*`
  attributes (the demo's modal opens via `data-bs-toggle="modal"`) or a JS API.

## 3.3 — Customization

- **Theming via Sass:** Bootstrap is built in Sass with variables (`$primary`,
  `$border-radius`, spacing maps). Override the variables and recompile to rebrand
  globally — the supported way to make Bootstrap not look like default Bootstrap.
- **CSS variables:** Bootstrap 5 also exposes CSS custom properties (`--bs-primary`) for
  lighter runtime theming (including its built-in dark mode via `data-bs-theme`).
- **Utilities API:** you can generate custom utility classes from config if needed.

## 3.4 — The trade-off (state it honestly)

- **Pros:** extremely fast to a polished, consistent, accessible UI; huge component set;
  great docs; battle-tested cross-browser; minimal design skill required — ideal for
  internal tools, admin panels, prototypes, and teams without a designer.
- **Cons:** the **"every Bootstrap site looks the same"** problem (generic look unless you
  invest in Sass theming); a **larger CSS payload** (you ship a lot you may not use,
  though you can build a subset); deep customization fights the framework's opinions; the
  utility layer is less complete than Tailwind's.

## Perspective

Bootstrap optimizes for **speed to a conventional, polished result**: grab pre-built
components and a proven grid, and you have a working, responsive, accessible UI in
minutes. The cost is a generic aesthetic and heavier CSS unless you theme via Sass. It's
the opposite bet from Tailwind — *give me components* vs *give me primitives* — and the
right call when consistency and speed matter more than a bespoke brand.
