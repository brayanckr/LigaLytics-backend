package com.ligalytics.controller;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligalytics.webhook.MatchEventPayload;
import com.ligalytics.webhook.MatchEventReceived;
import com.ligalytics.webhook.WebhookSignatureVerifier;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Recibe los eventos en vivo que empuja el proveedor de datos deportivos ({@code POST /api/webhooks/matches}).
 *
 * <p>El endpoint es público a nivel de red, así que la autenticidad se garantiza con una firma HMAC-SHA256 del
 * cuerpo y una marca de tiempo (ver {@link WebhookSignatureVerifier}). El cuerpo se recibe como bytes crudos
 * porque la firma se calcula sobre ellos; solo se interpreta como JSON <em>después</em> de verificarla.</p>
 */
@RestController
@RequestMapping(value = "/webhooks", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Webhooks", description = "Eventos en vivo enviados por el proveedor de datos (firmados con HMAC)")
public class WebhookController {

    /** Los eventos de partido son pequeños; un cuerpo mayor es un abuso o un error. */
    static final int MAX_BODY_BYTES = 64 * 1024;

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookSignatureVerifier verifier;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;

    public WebhookController(WebhookSignatureVerifier verifier, ObjectMapper objectMapper, ApplicationEventPublisher events) {
        this.verifier = verifier;
        this.objectMapper = objectMapper;
        this.events = events;
    }

    @PostMapping(value = "/matches", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Recibe un evento en vivo de un partido",
            description = "Exige las cabeceras X-Webhook-Timestamp (segundos epoch) y X-Webhook-Signature "
                    + "(sha256=<HMAC-SHA256 hex de \"<timestamp>.<cuerpo>\"> con el secreto compartido).")
    @ApiResponse(responseCode = "202", description = "Evento aceptado")
    @ApiResponse(responseCode = "400", description = "Cuerpo vacío, demasiado grande o con un evento inválido")
    @ApiResponse(responseCode = "401", description = "Firma ausente, incorrecta o caducada")
    public ResponseEntity<Map<String, String>> receive(
            @Parameter(description = "Segundos desde epoch en que el proveedor firmó")
            @RequestHeader(name = WebhookSignatureVerifier.TIMESTAMP_HEADER, required = false) String timestamp,
            @Parameter(description = "sha256=<hex del HMAC-SHA256>")
            @RequestHeader(name = WebhookSignatureVerifier.SIGNATURE_HEADER, required = false) String signature,
            @RequestBody(required = false) byte[] body) {

        byte[] raw = body == null ? new byte[0] : body;
        if (raw.length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("El cuerpo de la petición es demasiado grande");
        }

        // Autenticar primero: nada del contenido se interpreta hasta saber que lo envió el proveedor.
        verifier.verify(timestamp, signature, raw);

        if (raw.length == 0) {
            throw new IllegalArgumentException("El cuerpo de la petición está vacío");
        }
        MatchEventPayload payload = parse(raw);
        if (!payload.isValid()) {
            throw new IllegalArgumentException("El evento debe incluir eventId y type");
        }

        log.info("Evento en vivo recibido: partido={} tipo={} minuto={}", payload.eventId(), payload.type(), payload.minute());
        events.publishEvent(new MatchEventReceived(payload));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("status", "accepted"));
    }

    private MatchEventPayload parse(byte[] body) {
        try {
            return objectMapper.readValue(body, MatchEventPayload.class);
        } catch (IOException ex) {
            throw new IllegalArgumentException("El cuerpo de la petición no es un evento válido");
        }
    }
}
