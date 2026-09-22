<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · tailwind ➡](../phase-2-tailwind/NOTES.md)
<!-- /nav -->

# Phase 1 — CSS Foundations & the Two Philosophies: Notes

CSS frameworks (Tailwind, Bootstrap, or anything else) are built **on top of** ordinary
CSS — flexbox, grid, the box model, specificity. You cannot use a framework well if you
don't understand what it's generating underneath. This phase is a deep refresher on
those fundamentals, and then the central question the rest of the track answers: **where
should styling knowledge live** — in hand-written rules, in tiny composable utilities, or
in pre-built components?

## The Box Model

Every element CSS renders is a rectangular box made of four concentric layers. Understanding
this model is the single most important prerequisite for reasoning about layout, sizing,
and spacing bugs.

- **Content**: the actual text/image/inner elements. Its size is controlled by `width`/
  `height` (or the intrinsic size of the content).
- **Padding**: transparent space *inside* the border, between the border and the content.
  Set with `padding` (or `padding-top/right/bottom/left`).
- **Border**: a line drawn around the padding. Set with `border` (width, style, color).
- **Margin**: transparent space *outside* the border, separating this box from its
  neighbors. Set with `margin`. Margins can collapse between adjacent block elements
  (two stacked `<p>`s with `margin: 1rem` end up `1rem` apart, not `2rem`) — a common
  source of "why isn't my spacing what I expected" bugs.
- **`box-sizing`**: controls what `width`/`height` actually measure.
  - `content-box` (the CSS default): `width` sets *only* the content box. Padding and
    border are added on top, so the element's rendered width is
    `width + padding-left + padding-right + border-left + border-right`.
  - `border-box`: `width` sets the *total* rendered width (content shrinks to make room
    for padding/border). This is far more predictable and is what every serious
    framework (Tailwind, Bootstrap) and most style guides set globally.

```css
/* content-box (default) */
.box-a {
  box-sizing: content-box;
  width: 200px;
  padding: 20px;
  border: 5px solid black;
}
/* Rendered width = 200 (content) + 40 (padding L+R) + 10 (border L+R) = 250px */

/* border-box */
.box-b {
  box-sizing: border-box;
  width: 200px;
  padding: 20px;
  border: 5px solid black;
}
/* Rendered width = 200px total. Content area shrinks to 200 - 40 - 10 = 150px. */
```

```css
/* The near-universal reset every framework applies (this is exactly
   what Tailwind's base layer emits — see tailwind-demo/dist/output.css): */
*, *::before, *::after {
  box-sizing: border-box;
}
```

In this example: with `content-box`, two `200px`-wide boxes with different padding/border
render at *different* total widths even though you set the same `width` — you have to
mentally do addition every time you touch padding or border. With `border-box`, `width:
200px` always means 200px on screen, full stop; padding and border eat into the content
area instead of adding to the outside. That's why `box-sizing: border-box` is one of the
first rules in almost every production stylesheet.

**Why it's useful:** almost every "my flex item is wider than I expected" or "my grid
column overflowed" bug traces back to forgetting which box-sizing mode is active, or
forgetting that margin isn't included in either mode (margin always adds space *outside*
the border-box).

## The Cascade & Specificity

**The cascade** is the algorithm the browser uses to decide which CSS rule "wins" when
multiple rules target the same element and property. **Specificity** is the main input to
that decision — a numeric weight computed from a selector's parts.

- **Specificity order (highest to lowest)**: inline `style="..."` attribute > ID selector
  (`#header`) > class / attribute / pseudo-class selector (`.btn`, `[type="text"]`,
  `:hover`) > element / pseudo-element selector (`div`, `::before`). Higher wins,
  regardless of source order.
- **`!important`**: overrides normal specificity entirely — the highest-priority escape
  hatch. Considered a last resort because it breaks the normal cascade and is hard to
  override again later (you'd need another `!important` with equal-or-later source order).
- **Source order (the tiebreaker)**: when two rules have *equal* specificity, the one that
  appears **later** in the stylesheet (or later `<link>`/`<style>`) wins.
- **Inheritance**: some properties (`color`, `font-family`, `line-height`) inherit from
  parent to child by default; others (`margin`, `border`, `width`) don't and must be set
  per-element.

```css
/* Specificity: element selector = (0,0,1) */
p { color: black; }

/* Specificity: class selector = (0,1,0) — wins over the element rule */
.warning { color: orange; }

/* Specificity: ID selector = (1,0,0) — wins over both above */
#alert { color: red; }

/* Inline style — always wins over any selector in a stylesheet */
/* <p id="alert" class="warning" style="color: blue;">Text</p> renders BLUE */
```

```css
/* Same specificity (0,1,0) twice — source order decides. */
.btn { background: gray; }
.btn { background: blue; }   /* This wins — it comes later. */
```

In this example: a `<p id="alert" class="warning">` matches three rules of increasing
specificity (element → class → ID); the ID rule wins, so the text is red — unless an
inline `style` is present, which beats even the ID rule. In the second example, both rules
have identical specificity, so the *later* one in the file wins, not the "more specific"
one — this is a very common source of "I changed the CSS and nothing happened" bugs when
someone edits the earlier rule instead of the later one.

**Why utility frameworks care about this:** utility-class frameworks (Tailwind) deliberately
keep every utility as a **single flat class selector** (specificity `(0,1,0)`, always).
There are no `#id` rules, no nested selectors, no `!important` (mostly). That means two
utilities always resolve by **source/cascade order**, which is predictable, and you almost
never fight specificity — you just add or remove a class. This is one of utility-first
CSS's biggest practical wins over large hand-written stylesheets, where specificity wars
(`.card .header .title { ... }` vs `#page .title { ... }`) are a constant maintenance tax.

## Flexbox (One-Dimensional Layout)

**Flexbox** (`display: flex`) lays out children along a **single axis** — a row or a
column — and is built for distributing space and aligning items along that one dimension.
It's the workhorse for toolbars, navbars, button groups, and any "row of things that need
to align or space out."

- **Container property `display: flex`**: turns an element into a flex container; its
  direct children become flex items.
- **`flex-direction`**: `row` (default, main axis = horizontal) or `column` (main axis =
  vertical). This choice determines what "main axis" and "cross axis" mean below.
- **`justify-content`**: aligns items along the **main axis** — `flex-start`, `center`,
  `flex-end`, `space-between`, `space-around`, `space-evenly`.
- **`align-items`**: aligns items along the **cross axis** (perpendicular to main) —
  `flex-start`, `center`, `flex-end`, `stretch` (default).
- **`gap`**: space between flex items, without needing margin hacks on each child.
- **`flex-grow` / `flex-shrink` / `flex-basis`** (shorthand `flex`): how an individual
  item grows to fill extra space, shrinks under pressure, and its starting size before
  growing/shrinking.
- **`flex-wrap`**: `nowrap` (default — items overflow or shrink) vs `wrap` (items flow to
  a new line when they don't fit).

```css
.toolbar {
  display: flex;
  flex-direction: row;           /* main axis = horizontal */
  justify-content: space-between; /* push first/last item to the edges */
  align-items: center;            /* vertically center items on the cross axis */
  gap: 1rem;
}
```

This is exactly the pattern the Tailwind demo's header uses:
`class="flex items-center justify-between mb-4"` (`11-css-frameworks/tailwind-demo/index.html`)
— `flex` = `display: flex`, `items-center` = `align-items: center`, `justify-between` =
`justify-content: space-between`. Each Tailwind utility is a one-to-one mapping onto a
single CSS declaration; there is no new flexbox concept to learn once you know the raw
CSS.

In this example: `.toolbar` lays its children out in a horizontal row, pushes the first
item to the left edge and the last to the right edge (`space-between`), and vertically
centers every item regardless of its height (`align-items: center`). This exact
combination — "row, space title on the left and a badge on the right, all vertically
centered" — is one of the most common UI patterns in existence, which is why it has its own
three-word Tailwind shorthand (`flex items-center justify-between`).

**Why it's useful:** flexbox is the right tool whenever you're aligning or distributing
items along **one direction** — nav bars, button rows, form rows, card headers,
"icon + text" pairs, centering a single item both ways (`justify-content: center` +
`align-items: center` on a flex container centers its one child perfectly).

## CSS Grid (Two-Dimensional Layout)

**Grid** (`display: grid`) lays out children across **rows and columns simultaneously** —
a true 2-D layout system, unlike flexbox's single axis. It's the right tool for page-level
and section-level layouts (a dashboard, a photo gallery, a form with aligned labels and
inputs across multiple rows).

- **`display: grid`**: turns an element into a grid container.
- **`grid-template-columns`** / **`grid-template-rows`**: define the size of each column/
  row track. The **`fr`** unit ("fraction") divides remaining space proportionally:
  `grid-template-columns: 1fr 2fr;` gives the second column twice the width of the first.
- **`gap`** (`row-gap` / `column-gap`): space between grid cells — the grid equivalent of
  flexbox's `gap`, no margin hacks needed.
- **`grid-template-areas`**: name regions of the grid with strings and place children into
  them by name (`grid-area: header;`) — a very readable way to describe a page layout.
- **`repeat()`**: shorthand for repeated tracks — `grid-template-columns: repeat(12, 1fr);`
  is a full 12-column grid in one line (this is conceptually what Bootstrap's grid
  compiles down to, though Bootstrap actually implements its grid with flexbox + percentage
  widths for older-browser support).

```css
.page-layout {
  display: grid;
  grid-template-columns: 200px 1fr;   /* fixed sidebar, flexible main content */
  grid-template-rows: auto 1fr auto;  /* header, content, footer */
  grid-template-areas:
    "sidebar header"
    "sidebar main"
    "sidebar footer";
  gap: 1rem;
}
.sidebar { grid-area: sidebar; }
.header  { grid-area: header; }
```

In this example: the page is split into a fixed `200px` sidebar and a flexible main region
(`1fr` = "take all remaining width"), with three named regions stacked vertically next to
the sidebar. Because the areas are named strings, the layout structure is readable directly
from the CSS — you can see the shape of the page without mentally tracing row/column
numbers.

### Flexbox vs Grid

| | Flexbox | Grid |
|---|---|---|
| **Dimensions** | One axis at a time (row *or* column) | Two axes at once (rows *and* columns) |
| **Best for** | Aligning/distributing items in a line — toolbars, nav bars, button groups, centering one item | Overall page/section layout — dashboards, galleries, forms with aligned columns |
| **Sizing driven by** | Content size (items grow/shrink to fit) | Explicit track definitions (`grid-template-columns`) |
| **Can they combine?** | Yes — a grid cell can itself be a flex container, and vice versa | Yes — same |
| **Framework mapping** | Tailwind: `flex`, `items-*`, `justify-*`. Bootstrap: `d-flex`, `justify-content-*` | Tailwind: `grid`, `grid-cols-*`. Bootstrap's `row`/`col-*` grid is actually flexbox-based, not CSS Grid |

**Why it's useful:** knowing which one to reach for is a very common interview and
real-world decision. Rule of thumb: **"am I aligning things in a line?" → flex. "Am I
laying out a whole region with both rows and columns?" → grid.** They compose freely — a
grid region's content is very often a flex container internally.

## Responsive Design & Mobile-First

**Media queries** let a stylesheet apply different rules depending on viewport
characteristics (most commonly width), which is how a single site adapts from a phone
screen to a desktop monitor.

- **Syntax**: `@media (min-width: 640px) { .card { padding: 2rem; } }` — the rules inside
  only apply when the condition is true.
- **`min-width` vs `max-width`**: `min-width` queries match "this width **or wider**"
  (used for mobile-first, adding enhancements as the screen grows); `max-width` queries
  match "this width **or narrower**" (used for desktop-first, stripping things away as the
  screen shrinks).
- **Mobile-first** (the modern default, and what both Tailwind and Bootstrap use): write
  the **base/unprefixed** styles for the smallest screen, then layer on `min-width` media
  queries to *add* or *change* styles as the viewport grows. This means the base CSS that
  ships to every device (including phones, which are often on slower connections) is the
  leanest version.
- **Breakpoints**: named width thresholds a framework standardizes on, so "small/medium/
  large" means the same pixel width everywhere in the codebase. Tailwind's defaults: `sm`
  640px, `md` 768px, `lg` 1024px, `xl` 1280px, `2xl` 1536px. Bootstrap's: `sm` 576px, `md`
  768px, `lg` 992px, `xl` 1200px, `xxl` 1400px.

```css
/* Mobile-first: base rule targets the smallest screens with no media query at all. */
.button {
  width: 100%;       /* full width by default (phone) */
}

@media (min-width: 640px) {
  .button {
    width: auto;      /* auto width once the viewport is 640px or wider */
  }
}
```

This is precisely what the Tailwind demo's button does with
`class="mt-6 w-full sm:w-auto ..."` (`11-css-frameworks/tailwind-demo/index.html`):
`w-full` (`width: 100%`) is the unprefixed, mobile base; `sm:w-auto` only takes effect at
`min-width: 640px` and overrides it to `width: auto`. Bootstrap's grid columns follow the
identical philosophy: `class="col-12 col-md-6"` is full-width (`col-12`, all 12 grid
columns) on phones, and becomes half-width (`col-md-6`) only at `md` (768px) and up.

In this example: on a phone, the button spans the full width of its container (easy to
tap); once the viewport crosses 640px, the media query kicks in and the button shrinks to
its natural content width, since a full-width button on a wide desktop screen would look
odd. No JavaScript, no separate mobile site — one stylesheet describing both states.

**Why it's useful:** mobile-first is the industry-standard approach because it forces you
to design the constrained case (small screen, limited space) first, and *add* complexity
for larger screens, rather than designing for desktop and then trying to cram everything
into a phone as an afterthought. It also means unsupported/old browsers that don't
understand a later media query still render a reasonable base layout.

## Three Ways to Style: Plain CSS, Utility-First, Component-Based

This is the framing question the rest of the track (phases 2 and 3) answers in depth: once
you know the CSS fundamentals above, you still have to decide **where the styling
knowledge for your app lives**.

| Approach | How it works | Strength | Weakness |
|---|---|---|---|
| **Plain CSS / CSS Modules** | Hand-write rules, one class per component/element (`.card { ... }`) | Full control, zero dependencies, exactly the CSS you need | Naming is hard (BEM etc. exist to cope), duplication across similar components, styles drift out of sync with markup over time, you build every pattern (grid, spacing scale) yourself |
| **Utility-first (Tailwind)** | Compose many small single-purpose classes directly in markup (`flex p-4 bg-white`) | Fast, a built-in consistent design system (fixed spacing/color scale), tiny shipped CSS (unused utilities are never generated), no naming decisions ever | Verbose/busy-looking markup (long `class` strings), a real learning curve for the class vocabulary |
| **Component-based (Bootstrap)** | Import pre-built, pre-styled components by class name (`btn btn-primary`, `card`, `modal`) | Fastest path to a polished, consistent, accessible result; huge included component set | Generic "every Bootstrap site looks the same" look unless themed; heavier CSS payload; deep customization fights the framework's opinions |

- **The core philosophical tension: separation of concerns vs. colocation.** Classic CSS
  practice says styles belong in their own `.css` file, separate from markup/behavior
  ("separation of concerns"). Utility-first CSS bets the opposite: for **component-based**
  UIs (React, Angular, Vue), the real unit of reuse is the *component*, not the file type —
  so a component's structure (HTML/JSX) and its style (utility classes) belong physically
  next to each other ("colocation"), the same argument React makes for JSX colocating
  markup and logic in one file instead of splitting templates from controllers.
- **Utilities are not the same as inline styles**, even though a long `class="flex p-4
  bg-white"` string looks similar to `style="display:flex; padding:1rem; background:white"`
  at first glance. Utilities differ in every way that matters: they draw from a
  **constrained design system** (a fixed spacing/color scale, so `p-4` always means the
  same `1rem` everywhere in the app, whereas inline styles let anyone type any value);
  they support **variants inline styles simply cannot express** (`hover:`, `focus:`,
  `sm:`, `dark:` — there is no such thing as an inline `hover` style); they compile into
  **one shared, cacheable stylesheet** instead of duplicating declarations inline on every
  element; and unused utilities are **purged** from the final build (phase 2 covers this in
  depth).

## Summary / Key Takeaways

- The **box model** is content → padding → border → margin; set `box-sizing: border-box`
  globally (every framework does) so `width` means total rendered width, not just content.
- **Specificity** (inline > id > class > element) plus **source order** as the tiebreaker
  decides which CSS rule wins; utility frameworks deliberately keep every class at flat,
  equal specificity so conflicts resolve by cascade order, not specificity wars.
- **Flexbox is one-dimensional** (a row or a column — toolbars, nav bars, centering);
  **Grid is two-dimensional** (rows and columns together — page/section layout). They
  compose; pick based on "am I aligning a line of things, or laying out a whole region?"
- **Mobile-first** means the unprefixed/base style targets the smallest screen, and
  `min-width` media queries layer on enhancements as the viewport grows — the approach
  both Tailwind (`sm:`/`md:`) and Bootstrap (`col-md-*`) use.
- There is no universally "right" way to style an app — it's a genuine trade-off between
  **control** (plain CSS), **speed + a built-in design system with minimal shipped CSS**
  (utility-first / Tailwind), and **speed to a polished, conventional result**
  (component-based / Bootstrap). Utilities are not inline styles: they carry a design
  system, variants, caching, and purging that inline styles can't offer.
