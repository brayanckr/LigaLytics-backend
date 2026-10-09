package com.ligalytics.auth;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private UserRepository users;
    @Autowired
    private AuthSessionRepository sessions;
    @Autowired
    private PasswordHasher hasher;

    @BeforeEach
    void clean() {
        sessions.deleteAll();
        users.deleteAll();
    }

    private String body(Object value) throws Exception {
        return mapper.writeValueAsString(value);
    }

    private String register(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", email, "displayName", "Demo", "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.balance", is(100000)))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).path("token").asText();
    }

    @Test
    void registerGivesTheDemoBalanceAndALoginWorks() throws Exception {
        String token = register("ana@example.com", "clave-segura-1");

        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("ana@example.com")))
                .andExpect(jsonPath("$.balance", is(100000)));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "ANA@example.com", "password", "clave-segura-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()));
    }

    @Test
    void invalidRegistrationsAndWrongPasswordsAreRejected() throws Exception {
        register("ana@example.com", "clave-segura-1");

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "ana@example.com", "displayName", "Otra", "password", "otra-clave-1"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "b@example.com", "displayName", "Bea", "password", "corta"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "no-es-correo", "displayName", "Bea", "password", "clave-segura-1"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "ana@example.com", "password", "incorrecta"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedRoutesNeedAValidSessionAndLogoutClosesIt() throws Exception {
        mockMvc.perform(get("/betting/wallet")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/betting/wallet").header("Authorization", "Bearer inventado"))
                .andExpect(status().isUnauthorized());

        String token = register("ana@example.com", "clave-segura-1");
        mockMvc.perform(get("/betting/wallet").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(100000)))
                .andExpect(jsonPath("$.currency", is("COP (ficticio)")));

        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordsAndTokensAreNotStoredInPlainText() throws Exception {
        String token = register("ana@example.com", "clave-segura-1");

        User user = users.findByEmailIgnoreCase("ana@example.com").orElseThrow();
        assertFalse(user.getPasswordHash().contains("clave-segura-1"));
        assertTrue(user.getPasswordHash().startsWith("pbkdf2$"));
        assertTrue(hasher.matches("clave-segura-1", user.getPasswordHash()));
        assertFalse(hasher.matches("otra", user.getPasswordHash()));

        AuthSession session = sessions.findAll().get(0);
        assertEquals(PasswordHasher.sha256(token), session.getTokenHash());
        assertFalse(session.getTokenHash().contains(token));
    }

    @Test
    void fiveFailedLoginsLockTheAccountTemporarily() throws Exception {
        register("ana@example.com", "clave-segura-1");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("email", "ana@example.com", "password", "mala-" + i))))
                    .andExpect(status().isUnauthorized());
        }
        // Aunque la contraseña ahora sea correcta, el bloqueo sigue activo.
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "ana@example.com", "password", "clave-segura-1"))))
                .andExpect(status().isUnauthorized());
    }
}
