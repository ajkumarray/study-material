<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · rest](../phase-2-rest/NOTES.md) | [Phase 4 · data jpa ➡](../phase-4-data-jpa/NOTES.md)
<!-- /nav -->

# Phase 3 — Dependency Injection: Interview Q&A

⭐ = asked constantly.

**Q: What is a Spring bean?**
An object instantiated, assembled, and managed by the Spring IoC container. You declare beans via stereotype annotations or `@Bean` methods; the container handles their lifecycle and injects them where needed.

**Q: `@Component` vs `@Service` vs `@Repository` vs `@Controller`?** ⭐
All register a bean; they differ in semantic intent (and some add behavior). `@Component` is generic; `@Service` marks business logic; `@Repository` marks persistence *and* enables exception translation to `DataAccessException`; `@Controller`/`@RestController` mark web handlers. Layer your app with the right stereotype for readability and tooling.

**Q: Which injection type is best and why?** ⭐⭐
Constructor injection. Dependencies become `final` (immutable, always initialized), the object can't exist in an invalid half-wired state, it's testable without Spring (just call `new`), and circular dependencies fail fast at startup. Field injection hides dependencies and needs reflection to test; setter injection allows partially-constructed objects. Modern Spring: a single constructor is auto-wired, no annotation needed.

**Q: What is the default bean scope? What are the implications?** ⭐
Singleton — one shared instance per container. Implication: beans must be **stateless / thread-safe**, since the same instance serves all concurrent requests. Other scopes: prototype (new per injection), request, session.

**Q: `@Autowired` — how does resolution work? What if there are multiple candidates?**
Spring injects by type. If multiple beans match, disambiguate with `@Qualifier("name")` or mark one `@Primary`; otherwise startup fails with `NoUniqueBeanDefinitionException`. `@Autowired(required=false)` or `Optional<T>`/`ObjectProvider` handle "may be absent."

**Q: `@Value` vs `@ConfigurationProperties`?**
`@Value("${key}")` injects one property (supports SpEL). `@ConfigurationProperties(prefix=...)` binds a group of related properties to a typed POJO — better for structured config, with relaxed binding and validation support.

**Q: What are Spring profiles?** ⭐
Named groups of beans/config activated per environment (`dev`, `test`, `prod`) via `spring.profiles.active` or `@Profile`. Enable environment-specific beans (e.g., a data seeder only outside prod, an embedded DB in tests) from a single artifact.

**Q: How does `@Bean` differ from `@Component`?**
`@Component` is class-level auto-detection via component scanning (you own the class). `@Bean` is a method in a `@Configuration` class that explicitly constructs and returns a bean — used for third-party types you can't annotate, or when construction needs logic.
