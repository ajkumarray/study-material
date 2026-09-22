<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · tailwind](../phase-2-tailwind/NOTES.md)
<!-- /nav -->

# Phase 3 — Bootstrap: Interview Q&A

⭐ = asked constantly.

**Q: What is Bootstrap and what does it give you?** ⭐⭐
A component-based CSS (and JS) framework: pre-built, opinionated, accessible components —
buttons, cards, list groups, navbars, modals, alerts, dropdowns, forms — plus a responsive
12-column grid, all applied by class name alone. It also ships a smaller utility layer
(`d-flex`, `mb-4`, `text-secondary`) for the spacing/layout tweaks around those components.
The pitch is speed: you assemble a polished, consistent, cross-browser UI without designing
components or writing CSS yourself.

**Q: How does Bootstrap's grid system work?** ⭐⭐
A `container` (or `container-fluid` for full-width) wraps a `row`, and `row` wraps
`col-*` columns that claim units out of a conceptual 12-column grid — `col-6` is half
width, `col-4` is a third. Responsive column classes like `col-md-6` are mobile-first: the
column is full width by default (or whatever smaller-breakpoint class applies) and becomes
6/12 width starting at the `md` breakpoint (768px) and up. The grid is implemented with
flexbox internally (not CSS Grid), with negative margins on `row` offsetting column padding
to create consistent gutters between columns.

```html
<div class="container"><div class="row">
  <div class="col-12 col-md-6">stacks full-width on phone, half-width at md+</div>
  <div class="col-12 col-md-6">…</div>
</div></div>
```

*Follow-up: is Bootstrap's grid built on CSS Grid?* No — it's flexbox-based with
percentage widths, for broader legacy-browser compatibility; "grid" describes the visual
result (rows/columns of content), not the underlying `display` property.

**Q: Bootstrap vs Tailwind — how do they differ, and when would you choose each?** ⭐⭐
Bootstrap is **component-first**: you drop in finished, pre-styled components (`btn
btn-primary`, `card`, `modal`) — fastest path to a conventional, polished, accessible UI,
but with a more generic look and a heavier CSS payload since you ship the whole component
library. Tailwind is **utility-first**: you compose small single-purpose classes to build
your own components from scratch — a fully custom design with a small, tree-shaken CSS
bundle, at the cost of more verbose markup and more upfront design decisions. Choose
Bootstrap when speed and consistency matter more than a bespoke brand — internal tools,
admin panels, prototypes, teams without dedicated design resources. Choose Tailwind when you
need a custom, on-brand design system and want to keep shipped CSS minimal.

**Q: Do Bootstrap components need JavaScript to work?** ⭐
Static components — cards, the grid, plain buttons, badges, alerts without a dismiss
button — are pure CSS and need nothing else. **Interactive** components — modal, dropdown,
collapse, tooltip, carousel, toast — need Bootstrap's **JS bundle**, which also bundles
**Popper** for positioning (so dropdowns/tooltips place themselves correctly relative to
their trigger and flip when they'd overflow the viewport). They're wired up declaratively
via `data-bs-*` attributes with no hand-written click handlers needed for the common case.

```html
<button data-bs-toggle="modal" data-bs-target="#addModal">+ Add expense</button>
<div class="modal" id="addModal">...
  <button data-bs-dismiss="modal">Cancel</button>
</div>
<script src=".../bootstrap.bundle.min.js"></script>
```

*Follow-up: what does `data-bs-toggle="modal"` combined with `data-bs-target="#addModal"`
actually do, mechanically?* Once the JS bundle loads, it scans the page for elements with
`data-bs-toggle` attributes and attaches the appropriate behavior; clicking this button
tells Bootstrap's Modal component to show the element whose `id` matches the `data-bs-
target` selector — adding the classes/ARIA attributes that make it visible, trap focus, and
respond to `Esc`/backdrop clicks to close.

**Q: How do you customize Bootstrap so it doesn't look like default, generic Bootstrap?** ⭐
The supported, thorough way is **theming via Sass**: Bootstrap is authored with Sass
variables (`$primary`, `$secondary`, `$border-radius`, spacing maps), and you override
those variables in your own Sass file before importing Bootstrap's source, then recompile —
this regenerates every component's CSS from your values, so the rebrand is consistent
everywhere, not just wherever you remembered to add an override. A lighter alternative is
overriding Bootstrap 5's **CSS custom properties** (`--bs-primary`, `--bs-border-radius`,
...) at runtime without a Sass build step. That same CSS-variable mechanism also powers
Bootstrap's **built-in dark mode**: setting `data-bs-theme="dark"` swaps the variables to
dark-appropriate values.

**Q: What are the downsides of Bootstrap?** ⭐
The "every Bootstrap site looks the same" generic aesthetic if you don't invest in theming;
a larger CSS payload since you ship a broad component library whether or not you use all of
it (a custom Sass build can subset this); and friction when you need deep, bespoke
customization that fights the framework's built-in opinions about how components should
look and behave. Its own utility class layer, while real, is also smaller and less complete
than Tailwind's.

**Q: When would you choose Bootstrap over Tailwind in a real project?** *nuance*
When delivery speed to a conventional, consistent, accessible UI matters more than a
bespoke brand — internal tools, admin dashboards, prototypes, MVPs, or teams that are
backend-leaning and don't have dedicated front-end/design capacity. Bootstrap's ready-made,
already-accessible interactive components (modal focus-trapping, dropdown positioning,
keyboard dismissal) save real, non-trivial engineering time that you'd otherwise spend
building and testing that behavior yourself even with Tailwind.

*Follow-up: what would you choose for a public-facing marketing site that needs a
distinctive brand?* Most likely Tailwind (or heavily Sass-themed Bootstrap) — a marketing
site's whole point is visual differentiation, which fights against Bootstrap's default,
recognizable look unless you commit to full theming.

**Q: Is Bootstrap still relevant given utility-first CSS and headless component
libraries?**
Yes — it remains widely used for its speed, stability, documentation, and genuinely
accessible pre-built components, especially in enterprise/internal apps and on
backend-leaning teams. Newer stacks increasingly prefer Tailwind paired with a headless
component library (shadcn/ui, Radix, Headless UI) to get pre-built interactive *behavior*
(focus management, positioning) with fully custom *styling* — arguably the best of both
worlds — but Bootstrap is still a pragmatic, fast default when consistency and velocity
matter more than a fully custom design.
