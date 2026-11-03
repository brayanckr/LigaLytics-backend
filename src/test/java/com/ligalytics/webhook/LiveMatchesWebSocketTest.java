package com.ligalytics.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.ligalytics.betting.BzzoiroOddsProvider;
import com.ligalytics.etl.BzzoiroClient;

/**
 * Extremo a extremo con un servidor real en un puerto aleatorio: webhook firmado por HTTP → mensaje STOMP en
 * {@code /topic/live-matches}. También comprueba que nadie puede falsear marcadores enviando al tópico y que el
 * handshake solo se acepta desde los orígenes permitidos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // El application.properties de test sustituye al principal: se repite el prefijo para probar las URLs reales.
        "server.servlet.context-path=/api",
        "ligalytics.webhook.secret=secreto-e2e",
        "ligalytics.cors.allowed-origins=http://localhost:5173"
})
class LiveMatchesWebSocketTest {

    private static final String SECRET = "secreto-e2e";
    private static final String TOPIC = "/topic/live-matches";
    private static final String BODY =
            "{\"eventId\":\"700\",\"type\":\"GOAL\",\"homeTeam\":\"Real Madrid\",\"awayTeam\":\"FC Barcelona\","
                    + "\"minute\":34,\"homeScore\":1,\"awayScore\":0}";

    @MockBean
    private BzzoiroOddsProvider oddsProvider;
    @MockBean
    private BzzoiroClient bzzoiroClient;

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate rest;

    private WebSocketStompClient stompClient;
    private final BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @AfterEach
    void tearDown() {
        stompClient.stop();
    }

    private StompSession connect(String origin) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin(origin);
        return stompClient.connectAsync("ws://localhost:" + port + "/api/ws", headers, new StompSessionHandlerAdapter() {
        }).get(5, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    private void subscribe(StompSession session) throws Exception {
        session.subscribe(TOPIC, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((Map<String, Object>) payload);
            }
        });
        // El broker simple no confirma las suscripciones: se da un margen para que quede registrada antes de emitir.
        Thread.sleep(700);
    }

    private static String sign(String timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
    }

    private ResponseEntity<String> postWebhook(String body, boolean signed) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (signed) {
            String ts = String.valueOf(System.currentTimeMillis() / 1000);
            headers.set("X-Webhook-Timestamp", ts);
            headers.set("X-Webhook-Signature", sign(ts, body));
        }
        // TestRestTemplate ya antepone el context-path (/api): la URL completa es /api/webhooks/matches.
        return rest.postForEntity("/webhooks/matches", new HttpEntity<>(body, headers), String.class);
    }

    @Test
    void aSignedWebhookIsBroadcastToSubscribers() throws Exception {
        StompSession session = connect("http://localhost:5173");
        subscribe(session);

        ResponseEntity<String> response = postWebhook(BODY, true);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        Map<String, Object> message = received.poll(5, TimeUnit.SECONDS);
        assertNotNull(message, "el evento no llegó al tópico");
        assertEquals("700", message.get("eventId"));
        assertEquals("GOAL", message.get("type"));
        assertEquals("Real Madrid", message.get("homeTeam"));
        assertEquals(34, message.get("minute"));
        assertEquals(1, message.get("homeScore"));
        assertEquals(0, message.get("awayScore"));
    }

    @Test
    void aRejectedWebhookIsNotBroadcast() throws Exception {
        StompSession session = connect("http://localhost:5173");
        subscribe(session);

        ResponseEntity<String> response = postWebhook(BODY, false);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNull(received.poll(1500, TimeUnit.MILLISECONDS), "un webhook rechazado no debe emitirse");
    }

    @Test
    void aClientCannotForgeAScoreByPublishingToTheTopic() throws Exception {
        StompSession listener = connect("http://localhost:5173");
        subscribe(listener);
        StompSession attacker = connect("http://localhost:5173");

        attacker.send(TOPIC, Map.of("eventId", "700", "type", "GOAL", "homeScore", 9, "awayScore", 0));

        assertNull(received.poll(1500, TimeUnit.MILLISECONDS), "un cliente no debe poder emitir en el tópico");
    }

    @Test
    void theHandshakeFromAnUnlistedOriginIsRefused() {
        ExecutionException error = assertThrows(ExecutionException.class, () -> connect("http://sitio-malicioso.example"));
        // 403 y no otro error (un 404 también lanzaría excepción y ocultaría que el endpoint no existe).
        assertTrue(String.valueOf(error.getCause().getMessage()).contains("403"), String.valueOf(error.getCause()));
    }

    @Test
    void theHandshakeFromTheAllowedOriginIsAccepted() throws Exception {
        assertTrue(connect("http://localhost:5173").isConnected());
    }
}
