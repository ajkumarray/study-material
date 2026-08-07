package dev.ajay.expenseapi.web;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.ajay.expenseapi.domain.Category;
import dev.ajay.expenseapi.domain.Expense;
import dev.ajay.expenseapi.service.ExpenseService;
import dev.ajay.expenseapi.web.dto.CreateExpenseRequest;
import dev.ajay.expenseapi.web.dto.ExpenseResponse;

import jakarta.validation.Valid;

/**
 * REST controller (Phase 2). {@code @RestController} = {@code @Controller} +
 * {@code @ResponseBody}: every method's return value is serialized to JSON
 * (by Jackson, from the web starter) instead of resolving a view.
 *
 * <p>{@code @RequestMapping("/api/expenses")} sets the base path; the verb
 * annotations ({@code @GetMapping} etc.) map HTTP methods to handlers. The
 * service is constructor-injected, same as in the service layer.
 */
@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService service;

    public ExpenseController(ExpenseService service) {
        this.service = service;
    }

    // GET /api/expenses            -> 200 + list
    // GET /api/expenses?category=FOOD  (optional query param filters — n/a here, kept simple)
    @GetMapping
    public List<ExpenseResponse> list() {
        return service.all().stream().map(ExpenseResponse::from).toList();
    }

    // GET /api/expenses/{id}       -> 200, or 404 if missing (via the advice)
    @GetMapping("/{id}")
    public ExpenseResponse get(@PathVariable long id) {
        return ExpenseResponse.from(service.byId(id));
    }

    // GET /api/expenses/recent?days=7   -> query param with a default
    @GetMapping("/recent")
    public List<ExpenseResponse> recent(@RequestParam(defaultValue = "7") int days) {
        return service.recent(days).stream().map(ExpenseResponse::from).toList();
    }

    // GET /api/expenses/report     -> aggregation
    @GetMapping("/report")
    public Map<Category, BigDecimal> report() {
        return service.totalsByCategory();
    }

    // POST /api/expenses  {json}   -> 201 Created + Location header + body
    // @Valid triggers Bean Validation on the request body (Phase 5).
    @PostMapping
    public ResponseEntity<ExpenseResponse> create(@Valid @RequestBody CreateExpenseRequest req) {
        Expense saved = service.add(req.description(), req.amount(), req.category(), req.spentOn());
        ExpenseResponse body = ExpenseResponse.from(saved);
        // 201 with a Location header pointing at the new resource — REST convention.
        return ResponseEntity
                .created(URI.create("/api/expenses/" + saved.getId()))
                .body(body);
    }

    // DELETE /api/expenses/{id}    -> 204 No Content, or 404
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
