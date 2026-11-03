package com.ligalytics.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    private static final String AJAX = "X-Requested-With";

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

    /** Valor de la cookie de sesión que el servidor dejó en la cabecera Set-Cookie. */
    private static String sessionToken(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie, "la respuesta debe traer Set-Cookie");
        String prefix = AuthCookies.SESSION_COOKIE + "=";
        assertTrue(setCookie.startsWith(prefix), setCookie);
        return setCookie.substring(prefix.length(), setCookie.indexOf(';'));
    }

    private static Cookie session(String token) {
        return new Cookie(AuthCookies.SESSION_COOKIE, token);
    }

    private MvcResult registerResult(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", email, "displayName", "Demo", "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.balance", is(100000)))
                .andReturn();
    }

    private String register(String email, String password) throws Exception {
        return sessionToken(registerResult(email, password));
    }

    @Test
    void registerGivesTheDemoBalanceAndALoginWorks() throws Exception {
        String token = register("ana@example.com", "clave-segura-1");

        mockMvc.perform(get("/auth/me").cookie(session(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("ana@example.com")))
                .andExpect(jsonPath("$.balance", is(100000)));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "ANA@example.com", "password", "clave-segura-1"))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(AuthCookies.SESSION_COOKIE + "=")));
    }

    @Test
    void theTokenNeverAppearsInTheResponseBody() throws Exception {
        MvcResult registered = registerResult("ana@example.com", "clave-segura-1");
        String token = sessionToken(registered);

        assertFalse(registered.getResponse().getContentAsString().contains(token));
        assertFalse(registered.getResponse().getContentAsString().contains("\"token\""));
    }

    @Test
    void theSessionCookieIsHttpOnlySecureAndSameSite() throws Exception {
        String setCookie = registerResult("ana@example.com", "clave-segura-1").getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertTrue(setCookie.contains("HttpOnly"), setCookie);
        assertTrue(setCookie.contains("Secure"), setCookie);
        assertTrue(setCookie.contains("SameSite=Lax"), setCookie);
        assertTrue(setCookie.contains("Path=/"), setCookie);
        assertTrue(setCookie.contains("Max-Age=604800"), setCookie);
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
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void protectedRoutesNeedAValidSessionCookie() throws Exception {
        mockMvc.perform(get("/betting/wallet")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/betting/wallet").cookie(session("inventado"))).andExpect(status().isUnauthorized());

        String token = register("ana@example.com", "clave-segura-1");
        mockMvc.perform(get("/betting/wallet").cookie(session(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(100000)))
                .andExpect(jsonPath("$.currency", is("COP (ficticio)")));
    }

    @Test
    void theAuthorizationHeaderIsIgnoredEvenWithAValidToken() throws Exception {
        String token = register("ana@example.com", "clave-segura-1");

        mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/betting/wallet").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aCookieThatNoLongerMatchesASessionIsRejectedAndTheBrowserIsToldToDeleteIt() throws Exception {
        mockMvc.perform(get("/auth/me").cookie(session("caducada-o-inventada")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(AuthCookies.SESSION_COOKIE + "=;")));
        // Sin cookie no hay nada que borrar.
        mockMvc.perform(get("/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void aValidSessionLeavesTheUserIdInTheRequestAttribute() throws Exception {
        String token = register("ana@example.com", "clave-segura-1");
        Long id = users.findByEmailIgnoreCase("ana@example.com").orElseThrow().getId();

        MvcResult result = mockMvc.perform(get("/auth/me").cookie(session(token))).andExpect(status().isOk()).andReturn();

        assertEquals(id, result.getRequest().getAttribute("userId"));
    }

    @Test
    void changingRequestsAlsoNeedTheAjaxHeaderBecauseTheBrowserSendsCookiesOnItsOwn() throws Exception {
        String token = register("ana@example.com", "clave-segura-1");

        // Con sesión válida pero sin la cabecera (lo que haría un formulario de otro sitio): 403 y la sesión sigue viva.
        mockMvc.perform(post("/auth/logout").cookie(session(token))).andExpect(status().isForbidden());
        mockMvc.perform(get("/auth/me").cookie(session(token))).andExpect(status().isOk());

        // Sin sesión, el 401 va primero.
        mockMvc.perform(post("/auth/logout").header(AJAX, "XMLHttpRequest")).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutClosesTheSessionAndClearsTheCookie() throws Exception {
        String token = register("ana@example.com", "clave-segura-1");

        mockMvc.perform(post("/auth/logout").cookie(session(token)).header(AJAX, "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        mockMvc.perform(get("/auth/me").cookie(session(token))).andExpect(status().isUnauthorized());
        assertTrue(sessions.findAll().isEmpty());
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
        // Aunque la contraseña ahora sea correcta, el bloqueo sigue activo y no se abre sesión (ni cookie).
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "ana@example.com", "password", "clave-segura-1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }
}
