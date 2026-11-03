package com.ligalytics.config;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Protección por defecto: cualquier ruta exige sesión salvo la lista de públicas de {@link WebMvcConfig}.
 * Estas pruebas van como anónimo a propósito (no usan {@code AuthenticatedMockMvcConfig}).
 */
@SpringBootTest
@AutoConfigureMockMvc
class WebMvcConfigTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;

    @Test
    void everyBusinessRouteIsProtectedByDefaultNotJustTheBettingOnes() throws Exception {
        for (String path : new String[] {
                "/betting/wallet", "/teams", "/ranking", "/seasons", "/fixtures?from=2026-01-01&to=2026-01-08",
                "/predict/history", "/model/report", "/admin/status", "/auth/me", "/una-ruta-que-aun-no-existe" }) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Inicia sesión para continuar"));
        }
    }

    @Test
    void anAnonymousLogoutIsA401NotAServerError() throws Exception {
        // Si /auth/** estuviera excluida entera, /auth/me y /auth/logout fallarían con un 500 por falta de atributo.
        mockMvc.perform(post("/auth/logout").header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginAndRegisterStayPublic() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "email", "publica@example.com", "displayName", "Publica", "password", "clave-segura-1"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", "publica@example.com", "password", "mala"))))
                // 401 por contraseña errónea (lo decide el servicio), no por falta de sesión.
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Correo o contraseña incorrectos"));
    }

    @Test
    void theWebhookIsNotBehindTheSessionBecauseItAuthenticatesWithItsSignature() throws Exception {
        mockMvc.perform(post("/webhooks/matches").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", not(containsString("Inicia sesión"))))
                .andExpect(jsonPath("$.message").value("Firma del webhook no válida"));
    }

    @Test
    void theHealthcheckStaysOpenSoDeploysDoNotLoopOnRestart() throws Exception {
        mockMvc.perform(get("/health")).andExpect(status().isOk());
    }
}
