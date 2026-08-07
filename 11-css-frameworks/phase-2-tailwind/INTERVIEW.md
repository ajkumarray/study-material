<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · foundations](../phase-1-foundations/NOTES.md) | [Phase 3 · bootstrap ➡](../phase-3-bootstrap/NOTES.md)
<!-- /nav -->

# Phase 2 — Tailwind CSS: Interview Q&A

⭐ = asked constantly.

**Q: What is utility-first CSS / Tailwind?** ⭐⭐
An approach where you style by composing many small single-purpose classes (`flex`,
`p-4`, `bg-white`) directly in the markup, instead of writing custom CSS rules. Tailwind
provides these utilities mapped to a constrained design system, so you build any design
from primitives without naming classes or writing CSS.

**Q: How is Tailwind different from inline styles?** ⭐⭐
Utilities pull from a design system (fixed spacing/color scale), support responsive and
state variants (`sm:`, `hover:`, `dark:`) that inline styles can't express, share one
cacheable stylesheet, and are purged when unused. Inline styles are unconstrained, can't
do pseudo-classes/media queries, and aren't reusable.

**Q: How do responsive and state variants work?** ⭐
Prefixes: `sm:`/`md:`/`lg:` apply at min-width breakpoints (mobile-first), and
`hover:`/`focus:`/`active:`/`disabled:` apply in those states. They stack
(`sm:hover:bg-blue-700`). The unprefixed utility is the base/mobile style.

**Q: How does Tailwind keep the CSS small?** ⭐⭐
It scans your source for class names actually used and generates only those utilities.
Unused classes never reach the output, so the shipped CSS is small and doesn't grow with
the app (the demo's build is ~12 KB for exactly the classes on the page).

**Q: What's the point of `@apply`?**
It folds a set of utilities into a single custom class (`.btn { @apply px-4 py-2 rounded }`)
for a repeated pattern. It's handy for things like form controls, but extracting a
component (React/Angular) is usually the better way to reuse a utility bundle.

**Q: How do you customize Tailwind's design system?**
Define theme tokens — colors, spacing, fonts, breakpoints. In v4 that's CSS-first via
`@theme` in your stylesheet (v3 used `tailwind.config.js`). Custom tokens become
utilities (`bg-brand`), and arbitrary values (`bg-[#1da1f2]`) handle one-offs.

**Q: What are the downsides of Tailwind?** ⭐
Verbose markup (long class strings) and a learning curve for the class names, plus
repetition if you don't extract components. The verbosity is the common objection; it's
mitigated by componentizing repeated bundles and by editor tooling/autocomplete.

**Q: How do you avoid repeating the same long class list?** *nuance*
Extract a component (the primary answer — write the utilities once in `<Button>`/an
Angular component) or use `@apply` for a custom class. Since you're already building
components, the utility bundle lives in one place and is reused — the same DRY principle
as any repeated markup.
