package com.ligalytics.etl;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligalytics.exception.ExternalServiceException;

/**
 * Cliente de API-Football (api-sports.io, v3). La clave se define con {@code API_FOOTBALL_KEY}. El plan gratuito da
 * 100 peticiones al día: este cliente solo hace las dos que hacen falta para un partido (fixtures de un día y
 * estadísticas del partido) y devuelve el nodo {@code response}; los errores de la API (cuota, plan, temporada) llegan
 * con HTTP 200 en el campo {@code errors} y se convierten en {@link ExternalServiceException}.
 */
@Component
public class ApiFootballClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final String baseUrl;
    private final int leagueId;

    public ApiFootballClient(
            @Value("${ligalytics.apifootball.api-key:}") String apiKey,
            @Value("${ligalytics.apifootball.base-url:https://v3.football.api-sports.io}") String baseUrl,
            @Value("${ligalytics.apifootball.league-id:140}") int leagueId) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl;
        this.leagueId = leagueId;
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    /** Partidos de LaLiga de un día (hora de Madrid) de una temporada (año de inicio). */
    public JsonNode fixturesOn(LocalDate date, int season) {
        return get("/fixtures?league=" + leagueId + "&season=" + season + "&date=" + date
                + "&timezone=Europe/Madrid");
    }

    /** Estadísticas de un partido: un elemento por equipo con su lista {@code statistics}. */
    public JsonNode statistics(long fixtureId) {
        return get("/fixtures/statistics?fixture=" + fixtureId);
    }

    private JsonNode get(String path) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(READ_TIMEOUT);
        RestClient client = RestClient.builder().requestFactory(factory).baseUrl(baseUrl)
                .defaultHeader("x-apisports-key", apiKey).build();
        try {
            byte[] body = client.get().uri(path).retrieve().body(byte[].class);
            JsonNode root = mapper.readTree(new String(body, StandardCharsets.UTF_8));
            JsonNode errors = root.path("errors");
            if (errors.isObject() && errors.size() > 0) {
                StringBuilder text = new StringBuilder();
                errors.fields().forEachRemaining(e -> text.append(e.getValue().asText()).append(' '));
                throw new ExternalServiceException("API-Football: " + text.toString().trim(), null);
            }
            if (errors.isArray() && errors.size() > 0) {
                throw new ExternalServiceException("API-Football: " + errors.get(0).asText(), null);
            }
            return root.path("response");
        } catch (ResourceAccessException ex) {
            throw new ExternalServiceException("API-Football no responde", ex);
        } catch (RestClientResponseException ex) {
            throw new ExternalServiceException("API-Football respondió " + ex.getStatusCode().value(), ex);
        } catch (java.io.IOException ex) {
            throw new ExternalServiceException("Respuesta ilegible de API-Football", ex);
        }
    }
}
