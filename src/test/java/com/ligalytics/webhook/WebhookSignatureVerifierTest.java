package com.ligalytics.webhook;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.ligalytics.exception.UnauthorizedException;

class WebhookSignatureVerifierTest {

    private static final Instant NOW = Instant.parse("2026-01-10T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final byte[] BODY = "{\"eventId\":\"1\",\"type\":\"GOAL\"}".getBytes(StandardCharsets.UTF_8);

    private final WebhookSignatureVerifier verifier =
            new WebhookSignatureVerifier("secreto-de-prueba", Duration.ofMinutes(5), CLOCK);

    private String ts() {
        return String.valueOf(NOW.getEpochSecond());
    }

    @Test
    void acceptsAValidSignature() {
        String signature = "sha256=" + verifier.sign(NOW.getEpochSecond(), BODY);
        assertDoesNotThrow(() -> verifier.verify(ts(), signature, BODY));
    }

    @Test
    void acceptsTheSignatureWithoutPrefixAndInUpperCase() {
        String signature = verifier.sign(NOW.getEpochSecond(), BODY).toUpperCase();
        assertDoesNotThrow(() -> verifier.verify(ts(), signature, BODY));
    }

    @Test
    void rejectsATamperedBody() {
        String signature = "sha256=" + verifier.sign(NOW.getEpochSecond(), BODY);
        byte[] tampered = "{\"eventId\":\"1\",\"type\":\"RED_CARD\"}".getBytes(StandardCharsets.UTF_8);
        assertThrows(UnauthorizedException.class, () -> verifier.verify(ts(), signature, tampered));
    }

    @Test
    void rejectsASignatureMadeWithAnotherSecret() {
        WebhookSignatureVerifier attacker = new WebhookSignatureVerifier("otro-secreto", Duration.ofMinutes(5), CLOCK);
        String forged = "sha256=" + attacker.sign(NOW.getEpochSecond(), BODY);
        assertThrows(UnauthorizedException.class, () -> verifier.verify(ts(), forged, BODY));
    }

    @Test
    void rejectsAReplayedOldTimestampEvenWithAValidSignature() {
        long old = NOW.minusSeconds(3600).getEpochSecond();
        String signature = "sha256=" + verifier.sign(old, BODY);
        assertThrows(UnauthorizedException.class, () -> verifier.verify(String.valueOf(old), signature, BODY));
    }

    @Test
    void rejectsATimestampFarInTheFuture() {
        long future = NOW.plusSeconds(3600).getEpochSecond();
        String signature = "sha256=" + verifier.sign(future, BODY);
        assertThrows(UnauthorizedException.class, () -> verifier.verify(String.valueOf(future), signature, BODY));
    }

    @Test
    void doesNotAllowReusingASignatureWithADifferentTimestamp() {
        String signature = "sha256=" + verifier.sign(NOW.getEpochSecond(), BODY);
        String otherTs = String.valueOf(NOW.getEpochSecond() + 1);
        assertThrows(UnauthorizedException.class, () -> verifier.verify(otherTs, signature, BODY));
    }

    @Test
    void rejectsMissingOrMalformedHeaders() {
        String signature = "sha256=" + verifier.sign(NOW.getEpochSecond(), BODY);
        assertThrows(UnauthorizedException.class, () -> verifier.verify(null, signature, BODY));
        assertThrows(UnauthorizedException.class, () -> verifier.verify(ts(), null, BODY));
        assertThrows(UnauthorizedException.class, () -> verifier.verify("  ", signature, BODY));
        assertThrows(UnauthorizedException.class, () -> verifier.verify("no-es-un-numero", signature, BODY));
        assertThrows(UnauthorizedException.class, () -> verifier.verify(ts(), "sha256=", BODY));
    }

    @Test
    void failsClosedWhenNoSecretIsConfigured() {
        WebhookSignatureVerifier unconfigured = new WebhookSignatureVerifier("", Duration.ofMinutes(5), CLOCK);
        // Sin secreto no hay forma de verificar nada: ni una firma bien formada de otro secreto ni una vacía.
        String signature = "sha256=" + verifier.sign(NOW.getEpochSecond(), BODY);
        assertThrows(UnauthorizedException.class, () -> unconfigured.verify(ts(), signature, BODY));
    }
}
