<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · rendering ➡](../phase-2-rendering/NOTES.md)
<!-- /nav -->

# Phase 1 — App Router Model: Interview Q&A

⭐ = asked constantly.

**Q: How is Next.js different from React?** ⭐⭐
React is a UI *library*; Next is a *framework* built on it that adds file-based
routing, server rendering (SSR/SSG/streaming), server-side data fetching, API route
handlers, build tooling, and optimizations. React leaves those choices to you; Next
provides an opinionated, production-ready whole — including a server.

**Q: App Router vs Pages Router?** ⭐⭐
Pages Router (`pages/`, `getServerSideProps`/`getStaticProps`) is the original,
client-component model. App Router (`app/`) is the current model built on React Server
Components, with nested layouts, streaming, and server actions. New projects use the
App Router; Pages Router remains supported for legacy apps.

**Q: What files are special in the `app/` directory?** ⭐
`layout` (shared wrapper, state-preserving), `page` (route UI), `loading` (Suspense
fallback), `error` (error boundary), `not-found`, `route` (API handler), and
`template` (re-mounting layout). Folders define URL segments; a folder needs a `page`
to be routable.

**Q: Why is the root layout required?** ⭐
It's the top of the tree that renders the `<html>` and `<body>` tags for every route
and holds app-wide chrome/providers. There's exactly one, and Next renders it once and
keeps it mounted across navigations.

**Q: What's the difference between `layout` and `template`?**
Both wrap children, but a `layout` persists across navigation (state/scroll preserved,
doesn't re-render), while a `template` creates a **new instance** on each navigation
(state reset, effects re-run). Use `template` when you need per-navigation reset
(e.g. enter animations).

**Q: How does `<Link>` differ from `<a>`?** ⭐
`<Link>` does client-side navigation: it prefetches the destination and swaps only the
changed route segments (no full reload, preserved layout state), making transitions
instant. A plain `<a>` triggers a full document navigation.

**Q: What is partial rendering?**
On navigation, Next re-renders only the route segments that changed; shared parent
layouts stay mounted. This preserves their state and avoids re-fetching/re-rendering
unchanged UI, which is a key performance property of the App Router.

**Q: Is Next.js only for SSR?** *nuance*
No — per route you can choose static generation (SSG), server rendering per request
(SSR), incremental regeneration (ISR), or fully client-rendered islands. The App
Router makes the rendering strategy a per-segment decision rather than an app-wide one.
