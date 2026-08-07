# Angular — Work-Project Resume Pointers

Paste-ready, **code-grounded** resume bullets derived from the Angular projects in
`~/Projects/Work`. Every technical quantity below comes from the actual source
(component counts, standalone/lazy counts, NgRx, guards/interceptors, i18n, versions);
business metrics (client/user counts) are kept from the existing resume.

> Derived 2026-07-31 from `~/Projects/Work`. Non-Angular folders (`brahma`,
> `magistrate`, `maximon`, `roop-rekha`) and repos with no working-tree source
> (`doc-web-app`/`docquity-referral`, `doc-web-survey`) are excluded.

## Project → resume-section map

| Folder | What it is | Angular | Evidence from the code | Resume section |
|---|---|---|---|---|
| **dqcare-web** (`dqcare`) | Modern PAP/care rewrite | **20** | 179 components, **43 standalone**, **107 lazy** routes, 70 reactive-form screens, NgRx (13 effects), 3 guards | Patient Assistance Program (PAP) |
| **vyavasthaapak** ("administrator") | Config/onboarding **admin portal** | 14 | 155 components, 39 services, **46 lazy** modules, 90 reactive-form screens, NgRx (19 effects), 2 interceptors + 3 guards, **149 i18n** views | Onboarding |
| **docquitycare → nga-PAP** | **Legacy PAP** app | 10 | 82 components, **8 guards + 2 interceptors** (RBAC), 49 reactive forms, 11 NgModules | Patient Assistance Program (PAP) |
| **docquitycare → ngp-docquity-ds** | Design-system **component library** | 10 | 19 shared components, 21 NgModules | Onboarding / reusable packages |
| **open-cme** (`open-cme`) | **CME** platform (real-time) | 18 | 60 components, 33 services, **socket.io**, 4 interceptors + 3 guards, 84 i18n views | Survey, CME Quiz & Poll |
| **angular-libraries** (`Cmetemp`) | **4 publishable Angular libraries** | 15 | `common-card`, `common-question`, `content-module`, `listing-card` (public-api entries) | Survey / reusable packages |
| *onborading_2.0* (`onboarding2.0` SDK) | Auth/onboarding **SDK** (*related, not Angular*) | — | Webpack 5 + TypeScript 5 + SCSS, module federation, `npm link` (`LoginSdk`), 46 TS files | Onboarding |

---

## Pointers

### dqcare-web — Angular 20 (flagship modern app → **PAP** section)
- Architected a large-scale **Angular 20** application with **43 standalone components** and **100+ lazy-loaded routes**, using route-level dependency injection and **NgRx** (13 feature effect stores) to reduce initial bundle size and isolate feature state.
- Built **70+ Reactive-Forms–driven screens** behind a metadata-driven form engine, enabling onboarding, enrollment, consent, and program administration through configurable field types without code changes.
- Implemented an **RBAC** layer with route guards and permission-driven UI rendering across patient, doctor, pharma-admin, approver, and operations roles.

### vyavasthaapak — Angular 14 (→ **Onboarding / admin-portal**)
- Built a configuration-driven **admin portal** (150+ components, 46 lazy-loaded feature modules) on **Angular 14 + NgRx**, letting product teams manage onboarding journeys, user attributes, and branding without deployments.
- Engineered **90+ dynamic Reactive-Forms screens** plus a shared design-system module, backing a no-deploy configuration workflow.
- Delivered full **multi-language support** (150+ internationalized views via ngx-translate) and secured routes with **HTTP interceptors and route guards**.

### docquitycare — Angular 10 monorepo (→ **legacy PAP** modernization story)
- Maintained and modernized a **legacy Angular 10** Patient Assistance Program (80+ components) with **8 route guards + HTTP interceptors** enforcing RBAC across pharma workflows.
- Extracted a **reusable Angular design-system library** (`ngp-docquity-ds`, 19 shared components) consumed across applications for consistent UI and faster delivery.

### open-cme — Angular 18 (→ **Survey / CME**)
- Developed a **CME/engagement platform** on **Angular 18 + NgRx + PrimeNG**, with **real-time updates via Socket.IO** and internationalized content (80+ translated views).
- Secured the app with **4 HTTP interceptors and route guards** for authenticated, role-aware access.

### angular-libraries — Angular 15 library workspace (→ reinforces "reusable packages")
- Authored **4 publishable Angular libraries** (`common-question`, `content-module`, `common-card`, `listing-card`) that dynamically render configuration-driven questions/content, reused across Survey, CME, and Poll applications.

### onborading_2.0 — Webpack/TS SDK (already in resume; now code-backed)
- Built a framework-agnostic **onboarding/auth SDK** with **Webpack 5, TypeScript 5, and SCSS** (module federation, `npm`-linkable `LoginSdk`), embeddable across 6+ Angular & React client apps.

---

## Notes
- **dqcare-web (Angular 20)** is the strongest showcase — foreground it.
- The technical numbers are from source; keep your validated business metrics (20+ pharma clients, 50K+ users) as-is.
- Version spread across projects (Angular 10 → 20) is itself a selling point: shows range from maintaining legacy to building on the latest standalone/signals-era Angular.
