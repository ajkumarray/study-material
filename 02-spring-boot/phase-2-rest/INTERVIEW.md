<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · fundamentals](../phase-1-fundamentals/NOTES.md) | [Phase 3 · di ➡](../phase-3-di/NOTES.md)
<!-- /nav -->

# Phase 2 — REST APIs: Interview Q&A

⭐ = asked constantly.

**Q: What makes an API RESTful?** ⭐
Resources identified by URIs (nouns), manipulated via standard HTTP methods (GET/POST/PUT/PATCH/DELETE), statelessness (each request carries all it needs — no server session), a uniform interface, proper status codes, and representations (usually JSON) decoupled from internal storage. Bonus: HATEOAS (hypermedia links) for the strict definition.

**Q: `@Controller` vs `@RestController`?** ⭐
`@RestController` = `@Controller` + `@ResponseBody`, so return values are serialized (to JSON) as the response body. `@Controller` alone resolves return values to view names (server-rendered HTML). REST APIs use `@RestController`.

**Q: `@PathVariable` vs `@RequestParam`?** ⭐
`@PathVariable` binds a value embedded in the URL path (`/expenses/{id}`) — identifies a resource. `@RequestParam` binds a query-string parameter (`?days=7`) — filters/options, and supports defaults/optional.

**Q: PUT vs PATCH vs POST?** ⭐
POST creates (not idempotent — two POSTs make two resources). PUT fully replaces a resource at a known URI (idempotent). PATCH partially updates. Idempotency (same request repeated = same effect) is the key distinction interviewers probe.

**Q: Why use DTOs instead of returning entities?** ⭐⭐
Decouples the API contract from the DB schema (either evolves independently), hides/controls exposed fields (security), prevents clients from setting server-managed fields, avoids lazy-loading/serialization pitfalls with JPA proxies, and lets validation live at the boundary. Returning entities couples your wire format to your tables — a classic mistake.

**Q: What status code for create / delete / not found / validation failure?**
Create → 201 Created (+ Location header). Successful delete with no body → 204 No Content. Missing resource → 404. Failed input validation → 400 Bad Request (or 422). Using correct codes is part of the API contract.

**Q: How does Spring convert objects to/from JSON?**
Jackson (auto-configured by the web starter). `@RequestBody` deserializes JSON → object; a returned object/`@ResponseBody` serializes object → JSON. Customizable via Jackson annotations/config.

**Q: How do you handle a collection response and content negotiation?**
Return a `List<Dto>` — Jackson serializes it to a JSON array. Content negotiation uses the `Accept` header to pick a representation; JSON is the default with the web starter.
