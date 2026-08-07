<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · hooks](../phase-4-hooks/NOTES.md) | [Phase 6 · routing ➡](../phase-6-routing/NOTES.md)
<!-- /nav -->

# Phase 5 — Component Patterns: Interview Q&A

⭐ = asked constantly.

**Q: What does "lifting state up" mean?** ⭐⭐
Moving shared state to the closest common ancestor of the components that need it, then passing it down as props (with callbacks to update it). It lets sibling components share and stay in sync via their parent, keeping a single source of truth.

**Q: Explain one-way data flow.** ⭐⭐
Data flows down through props; events flow up through callbacks. Components never mutate another component's state directly. This unidirectional flow makes state changes traceable and the app predictable — a core React principle.

**Q: Container vs presentational components?** ⭐
Container components manage state/data and coordinate children; presentational components just render props and emit events. Keeping most components presentational maximizes reuse and testability. Custom hooks now hold much container logic, softening the split.

**Q: What is the `children` prop?**
Whatever JSX you nest inside a component's tags, received as `props.children`. It enables composition — building wrappers/layouts (Card, Modal, Layout) that render arbitrary content — favoring composition over configuration.

**Q: What is prop drilling and how do you avoid it?** ⭐
Passing props through many intermediate components that don't use them just to reach a deep child. Avoid with Context (for app-wide data) or a state-management library, or by composing components so the data-owner renders the consumer directly.

**Q: What are render props and HOCs, and are they still used?**
A render prop is a function prop that returns JSX (parent controls child rendering); a Higher-Order Component wraps a component to add behavior. Both shared logic pre-hooks; custom hooks have largely replaced them for logic reuse, though render props still appear for flexible UI composition.

**Q: How local should component state be?**
As local as possible — colocate state with the component that uses it, and lift only when it's genuinely shared. Over-lifting concentrates state at the top and causes broad re-renders; too-local state forces duplication. Balance by where the data is actually needed.

**Q: How would you structure a medium-sized React app?**
Feature-based folders (components, hooks, api per feature), presentational components + hooks for logic, a thin container/route layer, a shared UI kit, and a data layer (API modules or React Query). Keep state local by default; use Context/a store for cross-cutting state.
