// Spring Cache abstraction on the expense-api service layer. The annotations turn
// Redis into a transparent cache — no manual get/set in business code.
// (Illustrative snippet; requires spring-boot-starter-cache + data-redis and
// @EnableCaching on a @Configuration class.)

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class ExpenseService {

    private final ExpenseRepository repo;   // Spring Data JPA (Postgres) — the source of truth

    public ExpenseService(ExpenseRepository repo) { this.repo = repo; }

    // CACHE-ASIDE, automated: on call, Spring checks Redis for key "expenses::<id>".
    // Hit → return cached value (skip the DB). Miss → run the method, store result, return.
    @Cacheable(value = "expenses", key = "#id")
    public Expense findById(Long id) {
        return repo.findById(id).orElseThrow();   // only runs on a cache MISS
    }

    // WRITE-THROUGH-ish: update the DB AND refresh the cache entry in one step.
    @CachePut(value = "expenses", key = "#expense.id")
    public Expense update(Expense expense) {
        return repo.save(expense);
    }

    // INVALIDATION: on delete, evict the cache entry so a stale value can't be served.
    @CacheEvict(value = "expenses", key = "#id")
    public void delete(Long id) {
        repo.deleteById(id);
    }

    // Evict everything in a cache (e.g. after a bulk import).
    @CacheEvict(value = "expenses", allEntries = true)
    public void clearCache() { /* no-op body; the annotation does the work */ }
}
