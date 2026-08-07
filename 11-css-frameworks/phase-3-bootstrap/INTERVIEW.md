<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · tailwind](../phase-2-tailwind/NOTES.md)
<!-- /nav -->

# Phase 3 — Bootstrap: Interview Q&A

⭐ = asked constantly.

**Q: What is Bootstrap and what does it give you?** ⭐⭐
A component-based CSS (and JS) framework: pre-built, opinionated components (buttons,
cards, modals, navbars, forms), a responsive 12-column grid, and a utility layer — styled
by adding class names. It gets you a polished, consistent, accessible UI fast without
designing from scratch.

**Q: How does Bootstrap's grid work?** ⭐⭐
Container → row → columns on a 12-column system. Columns use responsive classes
(`col-12 col-md-6`) that are mobile-first: full width on small screens, adjusting at
breakpoints. It's built on flexbox with gutters, giving predictable responsive layouts.

**Q: Bootstrap vs Tailwind?** ⭐⭐
Bootstrap is component-first: drop in pre-styled components — fastest to a conventional
polished UI, but generic-looking and heavier CSS. Tailwind is utility-first: compose
primitives for a custom design with tiny purged CSS, but verbose markup. Bootstrap gives
you components; Tailwind gives you building blocks.

**Q: Do Bootstrap components need JavaScript?** ⭐
Static components (cards, grid, buttons) are CSS-only. Interactive ones — modal, dropdown,
collapse, tooltip, carousel — need Bootstrap's JS bundle (which includes Popper for
positioning), typically triggered via `data-bs-*` attributes or a JS API.

**Q: How do you customize Bootstrap so it doesn't look generic?** ⭐
Theme via Sass: override Bootstrap's Sass variables (`$primary`, `$border-radius`, spacing
maps) and recompile to rebrand globally. Bootstrap 5 also exposes CSS variables
(`--bs-*`) for lighter runtime theming and has a built-in dark mode (`data-bs-theme`).

**Q: What are the downsides of Bootstrap?** ⭐
The "every Bootstrap site looks the same" generic aesthetic unless you invest in theming,
a larger CSS payload (you ship components you may not use), and friction when deeply
customizing against its opinions. Its utility layer is also smaller than Tailwind's.

**Q: When would you choose Bootstrap over Tailwind?** *nuance*
When speed to a conventional, consistent, accessible UI matters more than a bespoke brand:
internal tools, admin dashboards, prototypes, or teams without dedicated design resources.
Its ready-made accessible components (modals, dropdowns) save real time. Choose Tailwind
when you need a custom design system and minimal shipped CSS.

**Q: Is Bootstrap still relevant with utility-first and component libraries around?**
Yes — it remains widely used for its speed, stability, docs, and accessible components,
especially in enterprise/internal apps and by backend-leaning teams. Newer stacks often
prefer Tailwind + a headless component library (shadcn/ui, Radix) for custom designs, but
Bootstrap is a pragmatic default when consistency and velocity win.
