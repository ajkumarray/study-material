package dev.ajay.expenseapi.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import dev.ajay.expenseapi.security.JwtAuthFilter;
import dev.ajay.expenseapi.security.SecurityConfig;

import dev.ajay.expenseapi.domain.Category;
import dev.ajay.expenseapi.domain.Expense;
import dev.ajay.expenseapi.service.ExpenseNotFoundException;
import dev.ajay.expenseapi.service.ExpenseService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-slice test (Phase 6.2). {@code @WebMvcTest} loads ONLY the web layer
 * (controller + JSON + validation + the exception advice) — fast, no DB, no
 * full context. The service is a Mockito mock ({@code @MockitoBean}), so we
 * test the controller's HTTP behavior in isolation. {@code MockMvc} drives
 * fake HTTP requests without starting a real server.
 */
// This slice tests the CONTROLLER in isolation (security is verified separately
// in SecurityJwtIntegrationTest). addFilters=false bypasses the filter chain;
// excludeFilters keeps our custom SecurityConfig/JwtAuthFilter (which need
// JwtService, a non-web bean) out of the web slice context.
@WebMvcTest(controllers = ExpenseController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
class ExpenseControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    ExpenseService service;

    @Test
    void listReturnsJsonArray() throws Exception {
        given(service.all()).willReturn(List.of(
                new Expense("coffee", new BigDecimal("150"), Category.FOOD, LocalDate.now())));

        mvc.perform(get("/api/expenses"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
           .andExpect(jsonPath("$[0].description").value("coffee"))
           .andExpect(jsonPath("$[0].category").value("FOOD"));
    }

    @Test
    void getMissingReturns404() throws Exception {
        given(service.byId(anyLong())).willThrow(new ExpenseNotFoundException(99));

        mvc.perform(get("/api/expenses/99"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.detail").value("expense not found: 99"));
    }

    @Test
    void createValidReturns201() throws Exception {
        given(service.add(any(), any(), any(), any()))
                .willReturn(new Expense("book", new BigDecimal("599"), Category.OTHER, LocalDate.now()));

        String json = """
                {"description":"book","amount":599,"category":"OTHER","spentOn":"2026-07-15"}""";

        mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON).content(json))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.description").value("book"));
    }

    @Test
    void createInvalidReturns400WithFieldErrors() throws Exception {
        // amount negative + blank description -> validation should reject BEFORE the service
        String json = """
                {"description":"","amount":-5,"category":"OTHER","spentOn":"2026-07-15"}""";

        mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON).content(json))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.detail").value("validation failed"))
           .andExpect(jsonPath("$.errors.amount").exists())
           .andExpect(jsonPath("$.errors.description").exists());
    }

    @Test
    void deleteReturns204() throws Exception {
        mvc.perform(delete("/api/expenses/1"))
           .andExpect(status().isNoContent());
    }

    @Test
    void deleteMissingReturns404() throws Exception {
        doThrow(new ExpenseNotFoundException(7)).when(service).delete(7);

        mvc.perform(delete("/api/expenses/7"))
           .andExpect(status().isNotFound());
    }
}
