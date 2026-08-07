# Capstone — Same UI, Two Philosophies

The CSS track's synthesis: **the exact same expense-list UI, built both ways**, so the
utility-first vs component-based trade-off is something you've *seen*, not just read.

```bash
# Tailwind (real build — verified)
cd 11-css-frameworks/tailwind-demo && npm install && npm run build   # → dist/output.css (~12 KB, tree-shaken)
#   open index.html

# Bootstrap (real dist — verified installed)
cd 11-css-frameworks/bootstrap-demo && npm install
#   open index.html  (includes a working modal)
```

## The same card, two ways

**Tailwind** — style lives in the markup as composed primitives:
```html
<li class="flex items-center justify-between bg-white rounded-lg shadow-sm
           ring-1 ring-slate-200 p-4 hover:shadow-md transition-shadow">
```

**Bootstrap** — style comes from a named pre-built component:
```html
<li class="list-group-item d-flex justify-content-between align-items-center py-3">
```

Same result on screen; opposite bets about where styling knowledge lives.

## When to pick which

| Choose **Tailwind** when… | Choose **Bootstrap** when… |
|---|---|
| you want a **custom/branded** design | you want a **conventional, polished** UI fast |
| you care about **minimal shipped CSS** | design resources are limited |
| you're building a **component library** | building internal tools / admin / prototypes |
| the team has front-end/design capacity | the team is backend-leaning, values speed |

Neither is "better" — it's control-and-customization vs speed-and-consistency. Many real
apps use **Tailwind + a headless component library** (shadcn/ui, Radix) to get pre-built
*behavior* with fully custom *styling* — the best of both.

## Integrating with the framework tracks

- **React (08) / Next (09):** put utility classes on JSX (`className="…"`); extract a
  `<Button>`/`<Card>` component so the utility bundle is written once (solves verbosity).
  Bootstrap works too, but React teams usually prefer Tailwind or a React component lib.
- **Angular (10):** utilities on template elements; component encapsulation keeps them
  contained. Bootstrap integrates via the `dist` CSS or `ng-bootstrap` (Angular-native
  components instead of the jQuery-free JS bundle).
- The **build** (Tailwind's purge, Bootstrap's Sass) is part of the same pipeline the
  **Docker (12)** / **CI-CD (13)** tracks ship — styling compiles into the static assets
  served to users.

## The one-sentence takeaway

Utility-first (Tailwind) colocates styling as constrained primitives and purges the rest
for a tiny, custom result; component-based (Bootstrap) hands you polished components for
speed and consistency at the cost of a generic look and heavier CSS — and the modern
middle path pairs utilities with headless components.
