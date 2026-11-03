package com.ligalytics.webhook;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ligalytics.exception.UnauthorizedException;

/**
 * Comprueba que un webhook lo firmó el proveedor de datos, que es quien conoce el secreto compartido.
 *
 * <p>El proveedor envía dos cabeceras:</p>
 * <ul>
 *   <li>{@code X-Webhook-Timestamp}: segundos desde epoch en que firmó.</li>
 *   <li>{@code X-Webhook-Signature}: {@code sha256=<hex>} con el HMAC-SHA256 de
 *       {@code "<timestamp>.<cuerpo crudo>"} usando el secreto compartido.</li>
 * </ul>
 *
 * <p>La firma cubre el cuerpo tal cual llega (no el JSON reinterpretado) y la marca de tiempo, y esta
 * debe estar dentro de la tolerancia, de modo que una captura antigua no se pueda reenviar (replay).
 * La comparación es en tiempo constante. Si no hay secreto configurado se rechaza todo: nunca se
 * acepta un webhook sin poder verificarlo. Todos los fallos devuelven el mismo mensaje genérico.</p>
 */
@Component
public class WebhookSignatureVerifier {

    public static final String SIGNATURE_HEADER = "X-Webhook-Signature";
    public static final String TIMESTAMP_HEADER = "X-Webhook-Timestamp";

    private static final String ALGORITHM = "HmacSHA256";
    private static final String PREFIX = "sha256=";
    private static final String REJECTED = "Firma del webhook no válida";
    private static final Logger log = LoggerFactory.getLogger(WebhookSignatureVerifier.class);

    private final byte[] secret;
    private final Duration tolerance;
    private final Clock clock;

    @Autowired
    public WebhookSignatureVerifier(
            @Value("${ligalytics.webhook.secret:}") String secret,
            @Value("${ligalytics.webhook.tolerance:5m}") Duration tolerance) {
        this(secret, tolerance, Clock.systemUTC());
    }

    WebhookSignatureVerifier(String secret, Duration tolerance, Clock clock) {
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        this.tolerance = tolerance;
        this.clock = clock;
    }

    /** Lanza {@link UnauthorizedException} (HTTP 401) si la firma falta, no coincide o está caducada. */
    public void verify(String timestampHeader, String signatureHeader, byte[] body) {
        if (secret.length == 0) {
            log.warn("Webhook rechazado: ligalytics.webhook.secret (WEBHOOK_SECRET) no está configurado");
            throw new UnauthorizedException(REJECTED);
        }
        if (isBlank(timestampHeader) || isBlank(signatureHeader)) {
            throw reject("faltan cabeceras de firma");
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException ex) {
            throw reject("marca de tiempo ilegible");
        }
        long skew = Math.abs(clock.instant().getEpochSecond() - timestamp);
        if (skew > tolerance.toSeconds()) {
            throw reject("marca de tiempo fuera de tolerancia");
        }

        String provided = signatureHeader.trim();
        if (provided.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            provided = provided.substring(PREFIX.length());
        }
        byte[] expected = sign(timestamp, body).getBytes(StandardCharsets.US_ASCII);
        byte[] received = provided.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, received)) {
            throw reject("la firma no coincide");
        }
    }

    /** HMAC-SHA256 en hexadecimal de {@code "<timestamp>.<cuerpo>"}; también lo usan los tests para firmar. */
    String sign(long timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            mac.update((timestamp + ".").getBytes(StandardCharsets.US_ASCII));
            mac.update(body);
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("No se pudo calcular el HMAC del webhook", ex);
        }
    }

    private static UnauthorizedException reject(String reason) {
        // El motivo solo se registra: al llamante se le devuelve siempre el mismo mensaje.
        log.warn("Webhook rechazado: {}", reason);
        return new UnauthorizedException(REJECTED);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
