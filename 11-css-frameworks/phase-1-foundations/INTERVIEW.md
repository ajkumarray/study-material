<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · tailwind ➡](../phase-2-tailwind/NOTES.md)
<!-- /nav -->

# Phase 1 — CSS Foundations & Philosophies: Interview Q&A

⭐ = asked constantly.

**Q: Flexbox vs Grid?** ⭐⭐
Flexbox is one-dimensional (lay out items in a single row or column, distributing space
along one axis). Grid is two-dimensional (rows and columns together). Use flex for
component-level alignment (toolbars, list rows), grid for page/section layouts. They
compose — grid regions can contain flex.

**Q: What is `box-sizing: border-box`?** ⭐
It makes an element's `width`/`height` include padding and border rather than adding to
them, so sizing is predictable. Frameworks set it globally. Without it (`content-box`),
padding/border expand the box beyond the set width.

**Q: What is CSS specificity?** ⭐
The rule that decides which conflicting style wins: inline > id > class/attribute >
element, with source order breaking ties. Utility frameworks use flat, low-specificity
single classes deliberately, so overrides are predictable and you avoid "specificity
wars" and `!important`.

**Q: What does mobile-first mean?** ⭐
Base styles target small screens and you layer enhancements at larger breakpoints with
`min-width` media queries. Both Tailwind (`sm:`/`md:` prefixes) and Bootstrap (`col-md-*`)
are mobile-first: the unprefixed style is the mobile base.

**Q: Utility-first vs component-based CSS?** ⭐⭐
Utility-first (Tailwind) composes many single-purpose classes in the markup — flexible,
consistent tokens, tiny shipped CSS, but verbose markup. Component-based (Bootstrap)
provides pre-styled components — fastest to a polished result and consistent, but a
generic look and heavier CSS/harder customization.

**Q: Aren't Tailwind utilities just inline styles?** ⭐⭐
No. They map to a constrained **design system** (a fixed spacing/color scale), support
things inline styles can't (`hover:`, `focus:`, responsive `sm:`, dark mode), share one
cacheable stylesheet, and get purged of unused rules. Inline styles have none of that and
can't do pseudo-classes or media queries.

**Q: How do you handle repeated utility classes?** ⭐
Extract a **component** (React/Angular) or a template partial so the utility bundle is
written once and reused — the same DRY solution you'd use for any repeated markup.
Tailwind also offers `@apply` to fold utilities into a custom class, though extracting a
component is usually preferred.

**Q: When would you use plain CSS/CSS Modules instead of a framework?** *nuance*
When you need full control over a bespoke design with no framework opinions, want minimal
dependencies, or have a highly custom/branded UI where a framework fights you. CSS
Modules give scoped class names without a utility/component system. Many teams combine —
Tailwind for layout/spacing plus custom CSS for unique pieces.
