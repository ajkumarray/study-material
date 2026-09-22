<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · testing](../phase-6-testing/NOTES.md) | [Phase 8 · production ➡](../phase-8-production/NOTES.md)
<!-- /nav -->

# Phase 7 — Security: Notes

Fully implemented in `expense-api` — DB-backed users, BCrypt password hashing, a custom JWT filter, stateless session configuration, and register/login endpoints, verified end to end by `SecurityJwtIntegrationTest` (no token → 401; register → 201 + token; that token → 200; a garbage token → 401; wrong login password → 401).

**The pieces, mapped to the code:**
- `user/AppUser` (entity storing a BCrypt **hash**, never plaintext), `user/Role`, `user/UserRepository`
- `security/AppUserDetailsService` — bridges `AppUser` → Spring Security's `UserDetails`
- `security/JwtService` — signs/verifies tokens (JJWT library, HS256)
- `security/JwtAuthFilter` — a `OncePerRequestFilter` that reads the `Bearer` token, validates it, and populates the `SecurityContext`
- `security/SecurityConfig` — the `SecurityFilterChain` bean (stateless, CSRF disabled, public `/api/auth/**`), the `PasswordEncoder` bean, the `AuthenticationManager` bean, and a custom 401 entry point
- `security/AuthService` + `web/AuthController` — `/api/auth/register` and `/api/auth/login`, both returning a signed JWT

## 1. The Spring Security model: authentication vs. authorization

**Definition:** Spring Security is fundamentally a **chain of servlet filters** placed in front of the application — every incoming HTTP request passes through this chain before it ever reaches a `@RestController` method. The chain's job splits into two distinct questions: **authentication** ("who are you?") and **authorization** ("what are you allowed to do?").

**Key concepts:**
- **Authentication** verifies identity — checking a username/password pair, or (as in this project) validating a signed JWT's claims. A successful authentication produces an `Authentication` object, which is stored in the **`SecurityContext`** (a thread-bound holder accessible via `SecurityContextHolder`) for the remainder of that request's processing.
- **Authorization** happens *after* authentication succeeds — checking whether the now-known identity's roles/authorities satisfy whatever rule is attached to the requested endpoint (e.g., "must be authenticated," "must have `ROLE_ADMIN`").
- **Principal**: the authenticated identity itself — in this project, a Spring Security `UserDetails` object built from an `AppUser` row.
- **`GrantedAuthority`**: a single permission/role held by the principal — `AppUserDetailsService` maps `AppUser.role` (an enum, `USER` or `ADMIN`) into a Spring Security role via `.roles(u.getRole().name())`, which Spring Security internally represents as authorities prefixed `ROLE_` (`ROLE_USER`, `ROLE_ADMIN`).
- **Secure by default**: adding `spring-boot-starter-security` to the classpath, with *zero* additional configuration, makes every endpoint require authentication automatically (with a randomly generated password printed to the console at startup) — you then write a `SecurityFilterChain` bean to define your own rules, exactly as `SecurityConfig` does.

**Why it's useful:** conflating authentication and authorization is one of the most common imprecisions in a security discussion — being able to state crisply "authentication establishes who; authorization decides what they're allowed to do, and it only runs after authentication has already succeeded" is table-stakes vocabulary for any security-adjacent interview question.

## 2. Configuring the filter chain: `SecurityConfig`

**Definition:** A `SecurityFilterChain` bean is where an application declares its actual access rules — which endpoints are public, which require authentication, how sessions are (or aren't) managed, and which custom filters run in the chain.

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/h2-console/**").permitAll()
                    .anyRequest().authenticated())
            .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedEntryPoint()))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }
}
```

**Key concepts, matched to each line:**
- **`.csrf(csrf -> csrf.disable())`** — CSRF (Cross-Site Request Forgery) protection defends against a browser being tricked into submitting an authenticated request *using its own stored cookies*. Because this API is stateless and authenticates via an `Authorization: Bearer <token>` header (not a cookie the browser attaches automatically), there's no cookie-based session for a forged cross-site request to piggyback on — CSRF protection doesn't defend against anything relevant here, so it's explicitly disabled. (Contrast: a cookie/session-based app should generally leave CSRF protection *enabled*.)
- **`.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))`** — tells Spring Security to never create or use an `HttpSession`. Every request must independently prove who it is (via its bearer token); nothing about "being logged in" is remembered server-side between requests.
- **`.authorizeHttpRequests(...)`** declares the actual access rules, evaluated in order: `/api/auth/**` (register/login) and `/actuator/health` are `permitAll()` — reachable with no token at all, which has to be true, since you need to call `/api/auth/login` *before* you have a token to present anywhere else. `/h2-console/**` is also public, a dev-only convenience. `.anyRequest().authenticated()` is the catch-all: everything not explicitly listed above requires a valid authenticated principal — this is what protects `/api/expenses/**`.
- **`.exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedEntryPoint()))`** — see Section 4 below (the 401 vs. 403 fix).
- **`.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)`** — inserts the custom `JwtAuthFilter` (Section 3) into the filter chain *before* Spring Security's own standard username/password filter, so that by the time any later filter or the controller runs, the request has already been authenticated from its token if one was present and valid.
- **`PasswordEncoder` bean** — `BCryptPasswordEncoder`, a slow, salted, adaptive hashing algorithm. It's used both to hash a password on registration and to verify a password on login (by re-hashing the submitted password and comparing hashes — plaintext is never stored or compared directly).
- **`AuthenticationManager` bean** — pulled from Spring Security's `AuthenticationConfiguration`, exposed as a bean specifically so `AuthService.login` can call `authManager.authenticate(...)` to verify a submitted username/password pair against the configured `UserDetailsService` + `PasswordEncoder`.

**Why it's useful:** every one of these lines is answering a specific, concrete security question ("should this request be checked for CSRF," "should the server remember this client between requests," "which URLs need a token") — reading `SecurityConfig` top to bottom is effectively reading the application's entire access-control policy in one place.

## 3. Bridging the user model: `UserDetailsService` and `JwtAuthFilter`

**Definition:** Spring Security's authentication machinery doesn't know about your application's own user entity (`AppUser`) — it operates entirely in terms of its own `UserDetails` interface. `AppUserDetailsService` is the adapter that bridges the two; `JwtAuthFilter` is the custom filter that authenticates each request from its bearer token.

```java
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AppUserDetailsService(UserRepository users) { this.users = users; }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return users.findByUsername(username)
                .map(u -> User.withUsername(u.getUsername())
                        .password(u.getPassword())            // the BCrypt hash
                        .roles(u.getRole().name())             // -> ROLE_USER / ROLE_ADMIN
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("user not found: " + username));
    }
}
```

```java
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);        // no token -> continue unauthenticated
            return;
        }

        String token = header.substring(7);           // strip "Bearer "
        if (jwt.isValid(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            String username = jwt.extractUsername(token);
            UserDetails user = userDetailsService.loadUserByUsername(username);

            var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(request, response);
    }
}
```

**Key concepts:**
- **`UserDetailsService.loadUserByUsername`** is the single method Spring Security calls whenever it needs to look up a user by name — during login (via the `AuthenticationManager`) and, in this project's case, also from inside `JwtAuthFilter` to rebuild a `UserDetails` from a validated token's username claim. `AppUserDetailsService` implements it by delegating to `UserRepository.findByUsername` (a Spring Data derived query, Phase 4.2) and mapping the result into Spring Security's own `User` builder — note `.password(u.getPassword())` supplies the stored **BCrypt hash**, not a plaintext password (Section 4 covers this precisely).
- **`OncePerRequestFilter`** is a Spring base class guaranteeing a filter's logic runs exactly once per request, even in environments (like some servlet dispatch/forward scenarios) where a naive filter might otherwise run twice — the standard base class for a custom authentication filter.
- **The flow inside `doFilterInternal`**: read the `Authorization` header; if it's missing or doesn't start with `Bearer `, simply continue the filter chain unauthenticated (letting the later authorization rules decide whether that's acceptable for this URL — a public `/api/auth/**` endpoint is fine with no token; a protected one will be rejected downstream). If a bearer token *is* present, strip the `"Bearer "` prefix, validate the token's signature and expiry (`jwt.isValid(token)`), and — only if it's valid and nothing has already authenticated this request — extract the username claim, load the corresponding `UserDetails`, and build a `UsernamePasswordAuthenticationToken` carrying that principal and its authorities, storing it in the `SecurityContextHolder`. From that point in the filter chain onward (and inside the controller), the request is "logged in" for the current thread only — no session, no server-side state is created; the next request has to authenticate itself all over again from its own token.
- **`chain.doFilter(request, response)`** at the end (called unconditionally, in every branch) passes control to the next filter in the chain — this custom filter's job is only to *optionally* populate the `SecurityContext*`; it never itself decides to reject a request. That decision belongs to the authorization rules configured in `SecurityConfig`.

**Why it's useful:** understanding this filter precisely is what lets you correctly explain a subtle but important design point — the filter itself doesn't block anything on a missing or bad token; it just does (or doesn't) authenticate the request. Whether an unauthenticated or wrongly-authenticated request is actually *rejected* is a separate concern, decided later by `.authorizeHttpRequests(...)`'s `anyRequest().authenticated()` rule.

## 4. Passwords: hashing with `PasswordEncoder`/BCrypt

**Definition:** Passwords must never be stored, logged, or compared in plaintext. `PasswordEncoder` is Spring Security's abstraction for password hashing; `BCryptPasswordEncoder` is the standard, production-appropriate implementation used in this project.

```java
@Bean
PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```
```java
// AuthService.register:
users.save(new AppUser(username, encoder.encode(rawPassword), Role.USER));   // stores the HASH
```

**Key concepts:**
- **Never store plaintext, never use a fast general-purpose hash (MD5, SHA-1, plain SHA-256) for passwords.** BCrypt is deliberately slow and computationally expensive per hash — a property that's *harmful* for hashing large data quickly, but exactly the property you want for password hashing, because it makes brute-force/dictionary attacks against a stolen password database orders of magnitude more expensive.
- **BCrypt is salted automatically**: each call to `encoder.encode(rawPassword)` generates a fresh random salt and embeds it directly in the output hash string, so two users with the identical password get two completely different stored hash values, and the same password hashed twice never produces the same output — defeating precomputed rainbow-table attacks.
- **Verification never decrypts**: BCrypt hashing is one-way — there's no "decrypt the hash back to the password." Login verification (via the `AuthenticationManager`, ultimately powered by `PasswordEncoder.matches(rawPassword, storedHash)`) re-hashes the submitted password using the salt embedded in the stored hash and compares the two hash values for equality.

**Why it's useful:** "how should you store passwords" is close to a universal security-interview question, and the precise, correct answer is specifically "a slow, salted, adaptive hashing algorithm like BCrypt via a `PasswordEncoder`" — not merely "hash them," which a naive `MD5(password)` answer would also (incorrectly) satisfy.

## 5. Stateless authentication for REST APIs: JWT

**Definition:** A REST API that authenticates every request independently, with no server-side session, needs a way for the client to *prove* its identity on every single call — JSON Web Tokens (JWT) are the standard mechanism: a signed, self-contained token the client presents on every request.

```java
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                       @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());   // key must be >= 256 bits for HS256
        this.expirationMs = expirationMs;
    }

    public String generateToken(String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public boolean isValid(String token) {
        try {
            return parse(token).getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;             // bad signature, malformed, expired, etc.
        }
    }
}
```

**The end-to-end flow:**
1. **Client authenticates once**: `POST /api/auth/login` with `{"username": "...", "password": "..."}`. `AuthService.login` calls `authManager.authenticate(new UsernamePasswordAuthenticationToken(username, rawPassword))`, which verifies the password against the stored BCrypt hash via `AppUserDetailsService` + `PasswordEncoder`, throwing `BadCredentialsException` (→ 401, Phase 5.2) on a mismatch.
2. **Server issues a signed JWT**: `JwtService.generateToken(username)` builds a token whose payload includes the username (as the `subject` claim), an issued-at timestamp, and an expiration timestamp, then signs the whole thing with an HMAC-SHA key (`signWith(key)`) derived from the configured secret.
3. **Client stores the token and sends it on every subsequent request**: `Authorization: Bearer <token>`.
4. **`JwtAuthFilter` validates the token on each incoming request** (Section 3) — checks the signature (proves it hasn't been tampered with, since only the server holding the secret could have produced a valid signature) and the expiry, and if valid, builds an `Authentication` for that request. No server-side session store is ever consulted or created.

**Key concepts:**
- **JWT structure**: `header.payload.signature`, each part base64url-encoded and dot-separated. The payload (claims) is only *encoded*, not encrypted — anyone can decode and read a JWT's payload without the secret, so **nothing sensitive should ever go in the payload** (this project's token payload is just a username, an issued-at time, and an expiry — no password, no sensitive PII). The signature is what makes the token tamper-proof: change any character of the header or payload and the signature no longer verifies, because recomputing a valid signature requires the secret key, which only the server holds.
- **HS256 and key length**: `Keys.hmacShaKeyFor(secret.getBytes())` requires a secret of at least 256 bits (32+ bytes) for the HS256 (HMAC-SHA256) algorithm — passing a shorter secret throws a `WeakKeyException` at startup, a deliberate fail-fast guard against an accidentally weak signing key. `application.yml`'s default fallback secret is intentionally long enough to satisfy this, while still being an obviously-fake placeholder meant to be overridden by a real `JWT_SECRET` environment variable in any real deployment.
- **Trade-offs vs. session-based auth**: JWTs are stateless and scale horizontally with zero coordination — any server instance can validate any token independently, with no shared session store needed, since all the information needed to verify identity is self-contained in the token itself. The cost is that a JWT **can't be easily revoked** before its natural expiry — once issued, it remains valid (as far as any server can tell) until it expires, even if the user "logs out" or their access should be revoked immediately. Standard mitigations: keep access-token lifetimes short (this project's `app.jwt.expiration-ms: 3600000` is one hour) combined with a separate, longer-lived refresh token flow (not implemented here, but the standard production pattern), or maintain a server-side denylist for the rare case of urgent, immediate revocation.

**Why it's useful:** this is exactly the authentication model a decoupled frontend (a React SPA, a mobile app) needs to talk to a stateless backend API — no cookies, no server-side session affinity required, which is also precisely why it's the model this project's future frontend integration (the React track) is built to consume.

## 6. The 401 vs. 403 fix

**Definition:** HTTP status codes distinguish two different authorization failures precisely: `401 Unauthorized` means "you haven't proven who you are (no/invalid credentials)"; `403 Forbidden` means "you've proven who you are, but you're not allowed to do this (wrong role/permissions)." Spring Security's out-of-the-box default for an unauthenticated request to a protected endpoint is actually `403` — a real correctness issue this project explicitly fixes.

```java
.exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedEntryPoint()))
// ...
private AuthenticationEntryPoint unauthorizedEntryPoint() {
    return (request, response, authException) ->
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "authentication required");
}
```

**Key concepts:**
- **Why Spring Security's default is arguably wrong for a REST API**: historically, `403` was the default response for any request denied by the authorization layer, regardless of *why* it was denied — whether the request had no credentials at all, or had valid credentials that simply lacked the right role. For a REST API, this conflates two very different situations a client needs to distinguish and handle differently (redirect to login vs. show "you don't have permission").
- **The fix**: registering a custom `AuthenticationEntryPoint` (invoked specifically when an unauthenticated request is denied, *before* any role/permission check would even run) that explicitly sends `401` instead of letting the default `403` behavior apply. `SecurityJwtIntegrationTest.protectedEndpointRequiresToken` asserts exactly this: `GET /api/expenses` with no token returns `status().isUnauthorized()` (401), not 403.
- **`403` is reserved for genuinely authenticated-but-forbidden cases** — this project doesn't currently have a role-restricted endpoint demonstrating that path (both roles it defines, `USER`/`ADMIN`, aren't yet differentiated by endpoint access), but the principle is: if a valid, authenticated `ROLE_USER` token tried to hit an endpoint restricted to `ROLE_ADMIN`, *that* request should return 403 — "I know exactly who you are, and the answer is still no."

**Why it's useful:** this specific fix is a great interview story because it demonstrates you understand HTTP semantics precisely enough to notice and correct a framework's default behavior, not just accept whatever Spring Security ships with — and the 401-vs-403 distinction itself is asked about directly, independent of any specific framework, in almost every backend security discussion.

## 7. Method-level authorization

**Definition:** Beyond URL-pattern-based rules in `SecurityConfig`'s `authorizeHttpRequests`, Spring Security also supports fine-grained authorization checks directly on individual service or controller methods.

```java
// Not currently used in expense-api (no role-restricted endpoints yet), but the
// standard mechanism once you need per-method role checks:
@PreAuthorize("hasRole('ADMIN')")
public void deleteAnyUsersExpense(long expenseId) { ... }
```

- **`@EnableMethodSecurity`** (on a `@Configuration` class) turns this on.
- **`@PreAuthorize("SpEL expression")`** evaluates an authorization expression *before* the method runs, throwing `AccessDeniedException` (→ 403, via Spring Security's own handling) if it evaluates false — the expression language supports role checks (`hasRole('ADMIN')`), authenticated checks, and even referencing method parameters.
- **`@PostAuthorize`** evaluates after the method runs, useful when the authorization decision depends on the method's return value (e.g., "only allow this if the returned expense actually belongs to the caller").
- **`@Secured`** is an older, simpler alternative supporting only role-name checks, without SpEL's flexibility.

**Why it's useful:** URL-pattern rules (`SecurityConfig`) are coarse — "this whole path prefix needs authentication." Method-level security lets you express finer-grained rules ("only an admin can delete *any* user's expense; a regular user can only delete their own") that don't map cleanly onto a URL pattern alone, and is the standard next step once an application's authorization model grows past simple all-or-nothing endpoint gating.

**Summary:**
- Authentication = who you are; authorization = what you're allowed to do — authorization only runs after authentication succeeds.
- `SecurityFilterChain` (`SecurityConfig`) declares access rules, session policy, and custom filter ordering; secure-by-default means every endpoint requires auth unless explicitly `permitAll()`'d.
- `AppUserDetailsService` bridges the app's own `AppUser` entity to Spring Security's `UserDetails`; `JwtAuthFilter` (a `OncePerRequestFilter`) authenticates each request from its bearer token, but never itself rejects a request — that's the authorization rules' job.
- Passwords are always hashed with a slow, salted, adaptive algorithm (BCrypt) via `PasswordEncoder` — never stored or compared in plaintext, never a fast general-purpose hash.
- JWT = signed, self-contained, stateless tokens (`header.payload.signature`); payload is encoded, not encrypted (never put secrets in it); the signature (not encryption) is what prevents tampering; trade-off is that revocation before expiry is hard — mitigate with short lifetimes + refresh tokens.
- 401 = no/invalid credentials ("who are you?"); 403 = authenticated but insufficient permissions ("I know you, but no") — Spring Security's raw default conflates these, and this project fixes it with a custom `AuthenticationEntryPoint`.
- CSRF protection defends cookie/session-based auth flows; it's correctly disabled for a stateless bearer-token API.
