<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · testing](../phase-6-testing/NOTES.md) | [Phase 8 · production ➡](../phase-8-production/NOTES.md)
<!-- /nav -->

# Phase 7 — Security: Notes

*Now fully implemented in `expense-api` — DB-backed users, BCrypt, a JWT filter,
stateless config, and register/login endpoints, verified by
`SecurityJwtIntegrationTest` (the full flow: 401 without token → register → 200
with token → 401 on a bad token → login credential checks).*

**The pieces (map to the code):**
- `user/AppUser` (entity, stores a BCrypt **hash**), `user/Role`, `user/UserRepository`
- `security/AppUserDetailsService` — bridges `AppUser` → Spring `UserDetails`
- `security/JwtService` — sign/verify tokens (JJWT, HS256)
- `security/JwtAuthFilter` — `OncePerRequestFilter`: reads `Bearer` token, validates, sets the `SecurityContext`
- `security/SecurityConfig` — `SecurityFilterChain` (stateless, CSRF off, public `/api/auth/**`), `PasswordEncoder`, `AuthenticationManager`, and a 401 entry point
- `security/AuthService` + `web/AuthController` — `/api/auth/register` & `/login` returning a JWT

**401 vs 403 (a real fix we made):** Spring Security's default for an
unauthenticated request is 403. For a REST API, unauthenticated should be
**401** ("who are you?" — no/invalid credentials), reserving **403** for
authenticated-but-forbidden ("I know you, wrong role"). We set an
`AuthenticationEntryPoint` to send 401.

## 7.1 — The Spring Security model

Spring Security is a **chain of servlet filters** in front of your app. Every request passes through the filter chain before reaching a controller. Core concepts:
- **Authentication** — *who are you?* Verify identity (username/password, token, OAuth). Produces an `Authentication` stored in the `SecurityContext`.
- **Authorization** — *what may you do?* Check roles/authorities against endpoint rules.
- **Principal** — the authenticated user; **`GrantedAuthority`**/roles — permissions.

Add `spring-boot-starter-security` and, by default, *every* endpoint requires authentication (with a generated password) — secure-by-default. You then configure a `SecurityFilterChain` bean to define the rules.

## 7.2 — Securing endpoints

A `SecurityFilterChain` bean declares access rules:
```java
http.authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/expenses/**").authenticated()
        .requestMatchers("/actuator/health").permitAll()
        .anyRequest().denyAll());
```
- **Users**: an in-memory `UserDetailsService` for demos, or a DB-backed one (load users/roles from a repository) for real apps.
- **Passwords**: always stored hashed — inject a **`PasswordEncoder`** (BCrypt is standard); never store or compare plaintext.
- **Method security**: `@PreAuthorize("hasRole('ADMIN')")` guards individual methods.

## 7.3 — Stateless JWT auth for APIs

REST APIs are **stateless** — no server session. The standard is **JWT (JSON Web Token)**:
1. Client authenticates once (`POST /login` with credentials) → server returns a signed JWT.
2. Client sends `Authorization: Bearer <token>` on every request.
3. A filter validates the token's signature and expiry, builds the `Authentication`, and lets the request through — no server-side session store.

JWT structure: header.payload.signature (base64url), signed with a secret/key so it can't be tampered with. Trade-offs: scalable and stateless, but tokens can't be easily revoked before expiry (mitigate with short lifetimes + refresh tokens). This is the model the React frontend (track 08) will use to talk to this API.
