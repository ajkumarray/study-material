<!-- nav -->
**[🏠 Repo Home](../README.md)**
<!-- /nav -->

# 11 — CSS Frameworks: Tailwind & Bootstrap

The two dominant approaches to styling a web app, taught side by side on the same UI (an
expense list) so the **philosophical contrast is concrete**:

- **Tailwind CSS** — *utility-first*: compose tiny single-purpose classes (`flex`,
  `p-4`, `bg-white`) directly in markup. You build your own components from primitives.
- **Bootstrap** — *component-based*: drop in pre-built, opinionated components
  (`btn btn-primary`, `card`, `modal`) and a 12-column grid. Fast, consistent, less bespoke.

Taught for someone who knows the **React (08)/Angular (10)** component model — because in
practice these frameworks style *components*, and the "where do styles live" question
directly shapes how you write components.

## The runnable demos

Both are **real and verifiable** — the same expense UI, styled two ways:

| Demo | What's real |
|---|---|
| `tailwind-demo/` | Tailwind **v4** installed; `npm run build` compiles `src/input.css` → `dist/output.css` (**verified**, ~12 KB, only the utilities actually used — tree-shaken). Open `index.html`. |
| `bootstrap-demo/` | Bootstrap **5.3** installed; `index.html` references the real `dist/` CSS + JS bundle (a working modal). Open `index.html`. |

```bash
# Tailwind — compile the stylesheet, then open the page
cd 11-css-frameworks/tailwind-demo && npm install && npm run build   # → dist/output.css
#   then open index.html in a browser

# Bootstrap — install, then open the page (no build step)
cd 11-css-frameworks/bootstrap-demo && npm install
#   then open index.html in a browser
```

## Curriculum

### Phase 1 — CSS foundations & the two philosophies ✅
- [x] 1.1 The box model, flexbox, grid, specificity, the cascade (quick refresher)
- [x] 1.2 Responsive design & mobile-first; media queries / breakpoints
- [x] 1.3 Utility-first vs component-based vs plain CSS/CSS-Modules — the trade-off
- NOTES · INTERVIEW

### Phase 2 — Tailwind CSS (utility-first) ✅
- [x] 2.1 The utility-first idea; composing primitives; why not "inline styles"
- [x] 2.2 Responsive prefixes (`sm:`), state variants (`hover:`/`focus:`), dark mode
- [x] 2.3 The design system: theme tokens, config, `@apply`, arbitrary values
- [x] 2.4 Tree-shaking / the build; pros, cons, and the "ugly markup" objection
- NOTES · INTERVIEW

### Phase 3 — Bootstrap (component-based) ✅
- [x] 3.1 The 12-column grid & container/row/col responsive layout
- [x] 3.2 Components (buttons, cards, list-group, modal) & utility classes
- [x] 3.3 Customization via Sass variables/theming; the JS bundle (Popper)
- [x] 3.4 Pros, cons, and the "every Bootstrap site looks the same" objection
- NOTES · INTERVIEW

### Capstone — choosing & integrating ✅
- [x] Same UI both ways; when to pick which; integrating with React/Angular; combining
  with component libraries (shadcn/ui, MUI). See `CAPSTONE.md`.

## How this connects

- **↔ React (08) / Angular (10):** utility classes go on JSX/template elements; the
  "repeated utilities" problem is solved by extracting a **component** (which you already
  know how to do) — the frameworks and the component model are complementary.
- **← the whole frontend:** styling is the last layer over the HTML that React/Angular/
  Next render; these are the two industry-standard ways to do it.

<!-- phases-nav -->
## 📂 Phase files

- **Phase 1 · foundations** — [Notes](phase-1-foundations/NOTES.md) · [Interview](phase-1-foundations/INTERVIEW.md)
- **Phase 2 · tailwind** — [Notes](phase-2-tailwind/NOTES.md) · [Interview](phase-2-tailwind/INTERVIEW.md)
- **Phase 3 · bootstrap** — [Notes](phase-3-bootstrap/NOTES.md) · [Interview](phase-3-bootstrap/INTERVIEW.md)
<!-- /phases-nav -->
