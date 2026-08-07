<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · state events ➡](../phase-2-state-events/NOTES.md)
<!-- /nav -->

# Phase 1 — Fundamentals: Interview Q&A

⭐ = asked constantly.

**Q: What is React and what problem does it solve?** ⭐⭐
A library for building UIs from composable components. Its model is UI = f(state): you declare what the UI should look like for the current state, and React efficiently updates the DOM to match — no manual DOM manipulation or keeping UI and data in sync by hand.

**Q: What is JSX?** ⭐
An HTML-like syntax extension that compiles to `React.createElement` calls. It lets you write markup in JavaScript; `{ }` embeds JS expressions. It's not HTML — `className` not `class`, camelCase events (`onClick`), one root element (or a Fragment).

**Q: What are props?** ⭐⭐
A component's read-only inputs, passed from parent to child like attributes. Props are immutable within the child — data flows down. To send data up, a parent passes a callback prop the child invokes (e.g., `onDelete`).

**Q: What is the virtual DOM and reconciliation?** ⭐⭐
The virtual DOM is an in-memory tree representation of the UI. On a state change React builds a new virtual tree, diffs it against the old one (reconciliation), and applies only the minimal real-DOM updates. This makes the "re-render on every change" model performant.

**Q: Why does React use keys in lists?** ⭐
Keys give list items a stable identity so the reconciler can match them across re-renders (reuse/reorder rather than recreate). Use a stable unique id, not the array index (index breaks on reorder/insert/delete).

**Q: Function components vs class components?**
Function components + hooks are the modern standard — simpler, composable stateful logic. Class components use lifecycle methods and `this`; still found in legacy code but not written today. Hooks replaced them.

**Q: What is composition in React?**
Building complex UIs by nesting and combining small components, and passing content via props/`children` — rather than inheritance. It's React's primary reuse mechanism (composition over inheritance).

**Q: What's the difference between a presentational and a container component?**
Presentational components just render props (no state/side effects) — reusable and easy to test. Container components own state/data-fetching and pass it down. A common separation of concerns (see Phase 5).

**Q: Is the virtual DOM always faster than direct DOM manipulation?** *nuance*
Not intrinsically — hand-optimized direct DOM updates can beat it. The virtual DOM's value is a simple programming model (declare UI from state) with *good enough* performance via batched, minimal updates. It trades a little raw speed for a lot of maintainability.
