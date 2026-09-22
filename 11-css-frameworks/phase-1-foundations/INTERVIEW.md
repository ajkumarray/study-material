<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · tailwind ➡](../phase-2-tailwind/NOTES.md)
<!-- /nav -->

# Phase 1 — CSS Foundations & Philosophies: Interview Q&A

⭐ = asked constantly.

**Q: Explain the CSS box model.** ⭐⭐
Every rendered element is a box made of four layers, from the inside out: **content**
(text/images/children), **padding** (transparent space inside the border), **border**
(a line around the padding), and **margin** (transparent space outside the border,
separating the element from its neighbors). `width`/`height` control the content box's
size by default. Margins between adjacent block-level siblings can **collapse** — two
stacked elements each with `margin: 1rem` end up `1rem` apart total, not `2rem` — which
surprises people who expect margins to simply add.

**Q: What does `box-sizing: border-box` actually change, and why do frameworks set it
globally?** ⭐⭐
By default (`box-sizing: content-box`), `width`/`height` size only the content box, and
padding/border are added *on top* of that — so `width: 200px; padding: 20px; border: 5px`
renders at `250px` total. With `box-sizing: border-box`, `width`/`height` set the **total**
rendered size, and padding/border are carved out of the content area instead of added to
it — the same rule renders at exactly `200px`. Frameworks (Tailwind, Bootstrap) apply
`box-sizing: border-box` to every element globally because it makes sizing predictable:
you never have to mentally add padding and border to figure out how wide something will
actually render.

**Q: What is CSS specificity, and how is it calculated?** ⭐⭐
Specificity is the weight the browser assigns to a selector to decide which of several
conflicting rules wins. From highest to lowest: **inline styles** (`style="..."`) beat
**ID selectors** (`#header`), which beat **class/attribute/pseudo-class selectors**
(`.btn`, `[type=text]`, `:hover`), which beat **element/pseudo-element selectors** (`div`,
`::before`). `!important` overrides normal specificity entirely and should be treated as a
last resort — it's hard to override again later without stacking another `!important`.
When two rules have *equal* specificity, **source order** breaks the tie: whichever rule
appears later in the cascade wins.

```css
p { color: black; }        /* specificity (0,0,1) */
.warning { color: orange; } /* specificity (0,1,0) — wins over the element rule */
#alert { color: red; }      /* specificity (1,0,0) — wins over both */
/* <p id="alert" class="warning">text</p> renders RED */
```

*Follow-up: why do utility-class frameworks like Tailwind avoid ID selectors and nesting?*
Because every utility class resolves to the same flat specificity `(0,1,0)`. That means
conflicts between utilities are always settled by source/cascade order, never by a
specificity fight — you never need `!important` to override a utility, you just remove or
reorder a class. Large hand-written stylesheets that mix IDs, nested selectors, and
`!important` are exactly where "specificity wars" happen in practice.

**Q: Flexbox vs Grid — when do you use each?** ⭐⭐
Flexbox is **one-dimensional**: it lays items out along a single axis (a row *or* a
column) and is built for aligning/distributing space along that one direction — toolbars,
nav bars, button groups, centering a single item. Grid is **two-dimensional**: it lays
items out across rows *and* columns simultaneously, and is built for whole-region layout
— dashboards, photo galleries, forms with aligned label/input columns. They compose freely
(a grid cell's content is very often itself a flex container). Rule of thumb: "am I
aligning a line of things?" → flex; "am I laying out a whole region with both rows and
columns?" → grid.

*Follow-up: is Bootstrap's grid system built on CSS Grid?* No — Bootstrap's 12-column
`row`/`col-*` grid is implemented with **flexbox** and percentage-based widths, for wider
legacy-browser compatibility. It's called a "grid" because of what it produces (rows and
columns of content), not because it uses the CSS `display: grid` property.

**Q: What do `justify-content` and `align-items` control in flexbox?** ⭐
`justify-content` aligns items along the **main axis** (the direction set by
`flex-direction`, horizontal by default) — values like `flex-start`, `center`,
`space-between`, `space-around`. `align-items` aligns items along the **cross axis**
(perpendicular to main) — `flex-start`, `center`, `stretch` (the default). A very common
combination is `display: flex; justify-content: space-between; align-items: center;` —
pushes the first/last child to opposite edges while vertically centering everything, e.g. a
page header with a title on the left and a badge on the right (exactly
`flex items-center justify-between` in the Tailwind demo's header).

**Q: What does `mobile-first` responsive design mean, and how do `min-width` and
`max-width` differ?** ⭐⭐
Mobile-first means you write the **base, unprefixed** CSS for the smallest/most
constrained screen first, then use `min-width` media queries to layer on enhancements as
the viewport grows — you're *adding* capability, not stripping it away. `min-width: 640px`
matches "640px or wider"; `max-width: 640px` matches "640px or narrower" (used for the
opposite, desktop-first approach, which is less common today). Both Tailwind (unprefixed
class = base, `sm:`/`md:`/`lg:` add overrides at breakpoints) and Bootstrap (`col-12` base,
`col-md-6` adjusts upward) are mobile-first frameworks.

```css
.button { width: 100%; }                 /* base: full width on phones */
@media (min-width: 640px) {
  .button { width: auto; }                /* only kicks in at 640px+ */
}
```

**Q: What's the difference between utility-first and component-based CSS?** ⭐⭐
Utility-first (Tailwind) styles elements by composing many small, single-purpose classes
directly in the markup (`flex items-center justify-between p-4 bg-white rounded-lg`) —
fast, draws from a consistent design-token scale, and ships a tiny CSS file because unused
utilities are never generated. The trade-off is verbose, busy-looking markup and a
vocabulary of class names to learn. Component-based (Bootstrap) styles elements by
applying named, pre-built component classes (`btn btn-primary`, `card`, `list-group`) —
the fastest path to a polished, consistent UI, but with a more generic look and a heavier
CSS payload since you ship the whole component library's styles.

**Q: Aren't utility classes just inline styles with extra steps?** ⭐⭐
No, and this is a common but incorrect objection. Utilities pull values from a
**constrained design system** — a fixed spacing/color/typography scale — so `p-4` means
the exact same `1rem` everywhere in the app, whereas inline styles let anyone type any
arbitrary value with zero consistency enforcement. Utilities support **responsive and
state variants** (`hover:`, `focus:`, `sm:`, `dark:`) that inline styles structurally
cannot express — there's no such thing as an inline `:hover` rule. Utility classes compile
into **one shared, browser-cacheable stylesheet**, while inline styles duplicate
declarations on every element and can't be cached separately from the HTML. And unused
utilities are **purged** from the production build, while inline styles always ship
exactly what's written, everywhere they're written.

**Q: What is the "separation of concerns vs. colocation" debate in CSS?** *nuance*
Classic CSS practice treats "concerns" as separate *by file type*: markup in HTML,
behavior in JS, style in a `.css` file — separation of concerns. Utility-first CSS
(and component frameworks like React/Angular more broadly) argues the real unit that
should be kept together is the *component*: a `<Card>`'s structure and its styling are one
concern, not two, so they belong colocated in the same file/markup rather than split across
a component file and a stylesheet that has to be kept in sync by hand. Tailwind is a bet
that for component-based UIs, colocation wins; plain CSS/CSS Modules is a bet that
file-based separation is worth preserving.

**Q: When would you reach for plain CSS or CSS Modules instead of a framework?** *nuance*
When you need full control over a bespoke, highly custom/branded design that a framework's
opinions would fight against, want zero external dependencies, or are building something
small enough that a framework's overhead isn't worth it. CSS Modules give you automatically
scoped class names (no global naming collisions) without adopting a utility or component
system. In practice many real codebases combine approaches — e.g., Tailwind for layout and
spacing plus a few hand-written CSS rules for one genuinely unique visual element.
