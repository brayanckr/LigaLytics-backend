package com.ligalytics.etl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.ligalytics.exception.ExternalServiceException;
import com.sun.net.httpserver.HttpServer;

/** Una respuesta que no llega no puede dejar la petición del usuario colgada: el cliente debe fallar rápido. */
class BzzoiroClientTimeoutTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String serve(long delayMillis, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            } catch (IOException clientGaveUp) {
                // el cliente ya cortó por el tiempo límite
            }
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort();
    }

    @Test
    void aSlowProviderFailsFastWithAnExternalServiceError() throws Exception {
        BzzoiroClient client = new BzzoiroClient("clave", serve(4000, "{\"results\":[],\"next\":null}"), 3,
                Duration.ofMillis(400));

        long start = System.nanoTime();
        assertThrows(ExternalServiceException.class,
                () -> client.upcomingEvents(LocalDate.now(), LocalDate.now().plusDays(7)));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsedMs < 2500, "debía cortar por el tiempo límite de 400 ms y tardó " + elapsedMs + " ms");
    }

    @Test
    void aRefusedConnectionAlsoFailsFast() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        BzzoiroClient client = new BzzoiroClient("clave", "http://localhost:" + closedPort, 3, Duration.ofSeconds(5));

        assertThrows(ExternalServiceException.class, () -> client.oddsRows(1));
    }

    @Test
    void aProviderErrorStatusLikeCloudflare526IsReportedAsAnExternalServiceError() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = "error code: 526".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(526, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        BzzoiroClient client = new BzzoiroClient("clave", "http://localhost:" + server.getAddress().getPort(), 3,
                Duration.ofSeconds(5));

        ExternalServiceException error = assertThrows(ExternalServiceException.class,
                () -> client.upcomingEvents(LocalDate.now(), LocalDate.now().plusDays(7)));

        assertTrue(error.getMessage().contains("526"));
    }

    @Test
    void aNormalResponseIsStillRead() throws Exception {
        BzzoiroClient client = new BzzoiroClient("clave",
                serve(0, "{\"results\":[{\"id\":7,\"home_team\":\"Real Madrid\"}],\"next\":null}"), 3, Duration.ofSeconds(5));

        List<JsonNode> events = client.upcomingEvents(LocalDate.now(), LocalDate.now().plusDays(7));

        assertEquals(1, events.size());
        assertEquals(7, events.get(0).path("id").asInt());
    }
}
