<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · templates binding ➡](../phase-2-templates-binding/NOTES.md)
<!-- /nav -->

# Phase 1 — Angular & Standalone Components: Interview Q&A

⭐ = asked constantly.

**Q: How is Angular different from React?** ⭐⭐
Angular is a complete, opinionated framework (routing, forms, HTTP, DI, testing,
compiler all first-party); React is a UI library you extend with an ecosystem. Angular
uses class components + decorators, dependency injection, RxJS/Signals, and a CLI;
React uses function components + hooks and leaves architecture to you.

**Q: What is a component in Angular?** ⭐
A TypeScript class decorated with `@Component`, which specifies a selector (its custom
element tag), a template (HTML + Angular syntax), and scoped styles. Class fields hold
the state the template binds to; methods are event handlers.

**Q: What are standalone components?** ⭐⭐
Components that declare their own template dependencies in an `imports` array instead of
being registered in an `NgModule`. They're the modern default (v17+): less boilerplate,
explicit dependencies, better lazy loading and tree-shaking. NgModules still exist for
legacy apps.

**Q: What's the difference between standalone and NgModule-based apps?**
NgModule apps group declarations/imports/providers in `@NgModule` classes; standalone
apps drop that layer — each component imports what it needs, and app-wide providers live
in `app.config.ts` via `bootstrapApplication`. Standalone is simpler and now
recommended.

**Q: How does an Angular app start?** ⭐
`main.ts` calls `bootstrapApplication(RootComponent, appConfig)`, which mounts the root
standalone component. `appConfig.providers` configures app-wide DI (router, HTTP, etc.).
(Legacy apps bootstrap a root `AppModule` instead.)

**Q: What is view encapsulation?**
Component styles are scoped to that component by default (emulated shadow DOM via
attribute rewriting), so a component's CSS doesn't leak globally. You can change the
mode (`Emulated`, `ShadowDom`, `None`) per component.

**Q: How does your Spring/Java background map to Angular?** *nuance*
Directly: decorators ≈ annotations, `@Injectable` services ≈ `@Service` beans, the DI
container is the same Inversion-of-Control idea, and both favor layered, structured
architectures. Angular is often described as "Spring for the frontend," which is why it
appeals to backend-heavy teams.

**Q: What does the Angular CLI do?**
Scaffolds projects and code (`ng new`, `ng generate`), runs a dev server (`ng serve`),
builds for production (`ng build`), and runs tests. It encodes Angular's conventions, so
project structure and tooling are consistent across teams.
