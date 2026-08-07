<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · tailwind ➡](../phase-2-tailwind/NOTES.md)
<!-- /nav -->

# Phase 1 — CSS Foundations & the Two Philosophies: Notes

Frameworks sit on top of CSS fundamentals; you still need them to use a framework well.
A quick refresher, then the central question this track answers: **where should styles
live** — in tiny utilities, in pre-built components, or in hand-written CSS?

## 1.1 — The fundamentals frameworks build on

- **Box model:** every element is a box of `content` + `padding` + `border` + `margin`.
  `box-sizing: border-box` (frameworks set this globally) makes `width` include padding/
  border — far more predictable.
- **Flexbox** — 1-D layout (a row or column): `display: flex`, `justify-content` (main
  axis), `align-items` (cross axis), `gap`. The demos' rows use flex utilities
  (`flex items-center justify-between`).
- **Grid** — 2-D layout (rows *and* columns): `display: grid`, `grid-template-columns`,
  `gap`. Bootstrap's 12-column grid is a grid/flex abstraction over this.
- **Specificity & the cascade:** which rule wins is decided by specificity (inline >
  id > class > element) then source order. Utility frameworks deliberately use **low,
  flat class specificity** so utilities are predictable and easy to override — no
  specificity wars.

## 1.2 — Responsive & mobile-first

- **Media queries** apply styles at viewport breakpoints:
  `@media (min-width: 640px) { … }`.
- **Mobile-first** = base styles target small screens; `min-width` breakpoints layer on
  larger-screen overrides. Both frameworks are mobile-first: Tailwind's unprefixed
  utility is the base and `sm:`/`md:`/`lg:` add at breakpoints; Bootstrap's `col` is
  base and `col-md-6` etc. adjust upward. Design small, enhance up.

## 1.3 — Three ways to style (the trade-off)

| Approach | How | Strength | Weakness |
|---|---|---|---|
| **Plain CSS / CSS Modules** | write rules, class per component | full control, no deps | naming, duplication, drift; you build everything |
| **Utility-first (Tailwind)** | compose primitives in markup | fast, consistent design tokens, tiny shipped CSS, no naming | verbose markup, learning the classes |
| **Component-based (Bootstrap)** | drop in pre-styled components | fastest to a polished result, consistent | generic look, heavier CSS, harder deep customization |

- **The core tension:** *separation of concerns* (styles in a `.css` file, classic CSS)
  vs *colocation* (styles next to markup, utility-first). Tailwind bets that for
  component-based UIs, **colocation wins** — the "concern" is the component, and its
  structure + style belong together (the same argument React makes for JSX colocating
  markup + logic).
- **Not inline styles:** utilities look like inline styles but aren't — they give you the
  design system (a constrained scale of spacing/colors), responsive/state variants
  (`hover:`, `sm:` — impossible inline), caching (one shared stylesheet), and purging of
  unused CSS.

## Perspective

There's no universally right answer — it's a trade-off between control, speed, and
consistency. This track teaches both dominant frameworks so you can justify a choice:
**Tailwind** when you want a custom design and a design system with minimal shipped CSS;
**Bootstrap** when you want a conventional, polished UI fast (internal tools, prototypes,
teams without a designer). The rest of the track goes deep on each.
