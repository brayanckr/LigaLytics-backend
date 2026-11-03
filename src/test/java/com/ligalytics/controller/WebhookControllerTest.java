package com.ligalytics.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ligalytics.webhook.MatchEventReceived;
import com.ligalytics.webhook.WebhookSignatureVerifier;

/** Prueba el endpoint sin levantar el contexto completo: controlador real, verificador real, manejador global real. */
class WebhookControllerTest {

    private static final String SECRET = "secreto-de-prueba";
    private static final String BODY =
            "{\"eventId\":\"512\",\"type\":\"GOAL\",\"homeTeam\":\"Real Madrid\",\"awayTeam\":\"FC Barcelona\","
                    + "\"minute\":34,\"homeScore\":1,\"awayScore\":0,\"campoDesconocido\":true}";

    private ApplicationEventPublisher events;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        events = mock(ApplicationEventPublisher.class);
        WebhookSignatureVerifier verifier = new WebhookSignatureVerifier(SECRET, Duration.ofMinutes(5));
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(new WebhookController(verifier, mapper, events))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /** Firma independiente de la implementación: así se comprueba el contrato con el proveedor. */
    private static String sign(String secret, String timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
    }

    private static String now() {
        return String.valueOf(System.currentTimeMillis() / 1000);
    }

    @Test
    void acceptsAndPublishesAProperlySignedEvent() throws Exception {
        String ts = now();
        mockMvc.perform(post("/webhooks/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Timestamp", ts)
                        .header("X-Webhook-Signature", sign(SECRET, ts, BODY))
                        .content(BODY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"));

        ArgumentCaptor<MatchEventReceived> captor = ArgumentCaptor.forClass(MatchEventReceived.class);
        verify(events).publishEvent(captor.capture());
        assertEquals("512", captor.getValue().payload().eventId());
        assertEquals("GOAL", captor.getValue().payload().type());
    }

    @Test
    void rejectsARequestWithoutSignatureHeaders() throws Exception {
        mockMvc.perform(post("/webhooks/matches").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void rejectsASignatureFromSomeoneWhoDoesNotKnowTheSecret() throws Exception {
        String ts = now();
        mockMvc.perform(post("/webhooks/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Timestamp", ts)
                        .header("X-Webhook-Signature", sign("secreto-del-atacante", ts, BODY))
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Firma del webhook no válida"));
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void rejectsABodyModifiedAfterSigning() throws Exception {
        String ts = now();
        String signature = sign(SECRET, ts, BODY);
        mockMvc.perform(post("/webhooks/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Timestamp", ts)
                        .header("X-Webhook-Signature", signature)
                        .content(BODY.replace("GOAL", "RED_CARD")))
                .andExpect(status().isUnauthorized());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void rejectsAStaleTimestamp() throws Exception {
        String old = String.valueOf(System.currentTimeMillis() / 1000 - 3600);
        mockMvc.perform(post("/webhooks/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Timestamp", old)
                        .header("X-Webhook-Signature", sign(SECRET, old, BODY))
                        .content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatesBeforeParsingSoGarbageWithoutSignatureIsA401NotA400() throws Exception {
        mockMvc.perform(post("/webhooks/matches").contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returns400ForAValidlySignedButInvalidEvent() throws Exception {
        String ts = now();
        String body = "{\"homeTeam\":\"Real Madrid\"}";
        mockMvc.perform(post("/webhooks/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Timestamp", ts)
                        .header("X-Webhook-Signature", sign(SECRET, ts, body))
                        .content(body))
                .andExpect(status().isBadRequest());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void returns400ForAValidlySignedButUnreadableBody() throws Exception {
        String ts = now();
        String body = "{no es json";
        mockMvc.perform(post("/webhooks/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Timestamp", ts)
                        .header("X-Webhook-Signature", sign(SECRET, ts, body))
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsEverythingWhenNoSecretIsConfigured() throws Exception {
        WebhookSignatureVerifier unconfigured = new WebhookSignatureVerifier("", Duration.ofMinutes(5));
        MockMvc open = MockMvcBuilders.standaloneSetup(new WebhookController(unconfigured, new ObjectMapper(), events))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        String ts = now();
        open.perform(post("/webhooks/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Timestamp", ts)
                        .header("X-Webhook-Signature", sign(SECRET, ts, BODY))
                        .content(BODY))
                .andExpect(status().isUnauthorized());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void rejectsAnOversizedBodyWithoutProcessingIt() throws Exception {
        String huge = "x".repeat(WebhookController.MAX_BODY_BYTES + 1);
        mockMvc.perform(post("/webhooks/matches").contentType(MediaType.APPLICATION_JSON).content(huge))
                .andExpect(status().isBadRequest());
    }
}
