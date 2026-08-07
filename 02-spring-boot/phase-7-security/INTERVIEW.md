<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · testing](../phase-6-testing/NOTES.md) | [Phase 8 · production ➡](../phase-8-production/NOTES.md)
<!-- /nav -->

# Phase 7 — Security: Interview Q&A

⭐ = asked constantly.

**Q: Authentication vs authorization?** ⭐⭐
Authentication verifies *who you are* (identity — credentials, token). Authorization decides *what you may do* (permissions — roles/authorities against resource rules). AuthN happens first; AuthZ follows.

**Q: How does Spring Security work at a high level?** ⭐
A chain of servlet filters intercepts every request before it reaches a controller. Filters perform authentication (building an `Authentication` in the `SecurityContext`) and authorization (checking rules). You configure a `SecurityFilterChain` bean to define which endpoints need what.

**Q: How should passwords be stored?** ⭐⭐
Hashed with a slow, salted adaptive algorithm — **BCrypt** (or Argon2/scrypt) via a `PasswordEncoder`. Never plaintext, never fast hashes like MD5/SHA-1. The encoder both hashes on registration and verifies on login (comparing hashes, never decrypting).

**Q: What is JWT and why use it for APIs?** ⭐⭐
A JSON Web Token — a signed, self-contained token (header.payload.signature) carrying claims (user, roles, expiry). The client sends it as `Authorization: Bearer <token>`; the server validates the signature and trusts the claims — no server-side session, so it scales horizontally. Ideal for stateless REST APIs and SPAs.

**Q: Session-based vs token-based auth?** ⭐
Session: server stores session state, client holds a session cookie — stateful, easy to revoke, needs sticky sessions/shared store when scaled. Token (JWT): stateless, self-contained, scales freely, but revocation before expiry is hard (use short TTL + refresh tokens). REST APIs typically go stateless/token.

**Q: What's a downside of JWT and how do you mitigate it?**
Can't easily revoke a valid token before it expires (logout/compromise). Mitigations: short access-token lifetimes with refresh tokens, a token denylist for critical revocations, and rotating signing keys.

**Q: What is CSRF and does it apply to a stateless JWT API?**
CSRF tricks a browser into sending an authenticated request using its cookies. It applies to cookie/session auth; a stateless API using `Authorization: Bearer` headers (not cookies) isn't cookie-driven, so CSRF protection is typically disabled for it — but enabled for cookie-based flows.

**Q: How do you authorize at the method level?**
`@EnableMethodSecurity` + `@PreAuthorize("hasRole('ADMIN')")` / `@PostAuthorize` / `@Secured` on service or controller methods — fine-grained checks beyond URL-pattern rules.
