package dev.ajay.expenseapi.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full end-to-end security test (Phase 7). Boots the whole app and drives the
 * real filter chain via MockMvc, proving the JWT flow works:
 *   1. protected endpoint without a token -> 401
 *   2. register -> receive a token
 *   3. same endpoint WITH the token -> 200
 *   4. login with a wrong password -> 401
 * {@code @ActiveProfiles("test")} disables the DataSeeder for a clean context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityJwtIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void protectedEndpointRequiresToken() throws Exception {
        mvc.perform(get("/api/expenses"))
           .andExpect(status().isUnauthorized());          // 401 — no token
    }

    @Test
    void registerThenAccessWithToken() throws Exception {
        // 2. register -> 201 + token
        String body = """
                {"username":"ajay_test","password":"secret123"}""";
        String response = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();

        String token = json.readTree(response).get("token").asText();

        // 3. protected endpoint WITH the bearer token -> 200
        mvc.perform(get("/api/expenses").header("Authorization", "Bearer " + token))
           .andExpect(status().isOk());

        // a garbage token is rejected -> 401
        mvc.perform(get("/api/expenses").header("Authorization", "Bearer not.a.jwt"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithWrongPasswordIs401() throws Exception {
        // register first
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"loginuser\",\"password\":\"rightpass\"}"))
           .andExpect(status().isCreated());

        // wrong password -> 401 via BadCredentials handler
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"loginuser\",\"password\":\"WRONGpass\"}"))
           .andExpect(status().isUnauthorized());

        // right password -> 200 + token
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"loginuser\",\"password\":\"rightpass\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void authEndpointsArePublic() throws Exception {
        // duplicate registration -> 409 (proves the endpoint is reachable without a token)
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"dupe\",\"password\":\"secret123\"}"))
           .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"dupe\",\"password\":\"secret123\"}"))
           .andExpect(status().isConflict());
    }
}
