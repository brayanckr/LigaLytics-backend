package com.ligalytics.service;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligalytics.etl.ApiFootballClient;
import com.ligalytics.etl.TeamNameNormalizer;
import com.ligalytics.model.Match;
import com.ligalytics.model.MatchApiStats;
import com.ligalytics.repository.MatchApiStatsRepository;
import com.ligalytics.service.dto.MatchDetailDto.Stat;

/**
 * Estadísticas reales (no estimadas) de un partido terminado, tomadas de API-Football. Cada partido se pide una sola
 * vez y se guarda en {@code match_api_stats}; así el plan gratuito (100 peticiones al día) alcanza. Un partido sin
 * datos todavía, o un error de la API, no se reintenta durante {@link #RETRY_AFTER}, y si la API avisa de que se
 * agotó la cuota se deja de llamar durante {@link #QUOTA_PAUSE}.
 */
@Service
public class ApiFootballStatsService {

    private static final Logger log = LoggerFactory.getLogger(ApiFootballStatsService.class);
    private static final Duration RETRY_AFTER = Duration.ofHours(1);
    private static final Duration QUOTA_PAUSE = Duration.ofHours(1);
    private static final Duration FIXTURES_TTL = Duration.ofHours(6);

    /** tipo de estadística de API-Football, clave, etiqueta. */
    private static final String[][] STAT_DEFS = {
            { "Ball Possession", "possession", "Posesión (%)" },
            { "expected_goals", "xg", "xG (goles esperados)" },
            { "Total Shots", "shots", "Tiros" },
            { "Shots on Goal", "shotsOnTarget", "Tiros a puerta" },
            { "Shots off Goal", "shotsOffTarget", "Tiros fuera" },
            { "Blocked Shots", "blockedShots", "Tiros bloqueados" },
            { "Corner Kicks", "corners", "Córneres" },
            { "Fouls", "fouls", "Faltas" },
            { "Offsides", "offsides", "Fuera de juego" },
            { "Yellow Cards", "yellowCards", "Tarjetas amarillas" },
            { "Red Cards", "redCards", "Tarjetas rojas" },
            { "Goalkeeper Saves", "saves", "Paradas" },
            { "Total passes", "passes", "Pases" },
            { "Passes %", "passAccuracy", "Precisión de pases (%)" },
    };

    private record CachedFixtures(JsonNode value, Instant expiresAt) {
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final ApiFootballClient client;
    private final MatchApiStatsRepository repository;
    /** Guarda en una transacción propia: quien llama puede estar dentro de una de solo lectura. */
    private final TransactionTemplate writeTx;
    private final Map<Long, Instant> retryAt = new ConcurrentHashMap<>();
    private final Map<LocalDate, CachedFixtures> fixturesByDay = new ConcurrentHashMap<>();
    private volatile Instant pausedUntil = Instant.EPOCH;

    public ApiFootballStatsService(ApiFootballClient client, MatchApiStatsRepository repository,
            PlatformTransactionManager transactionManager) {
        this.client = client;
        this.repository = repository;
        this.writeTx = new TransactionTemplate(transactionManager);
        this.writeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Estadísticas reales del partido: las guardadas o, si faltan y la API está configurada, las que se piden ahora.
     * Vacío si no hay datos (sin clave, partido sin jugar, la API aún no los tiene o falló).
     */
    public Optional<List<Stat>> statsFor(Match match) {
        Optional<MatchApiStats> stored = repository.findById(match.getId());
        if (stored.isPresent()) {
            return parsePayload(stored.get().getPayload());
        }
        if (!client.isConfigured() || match.getFullTimeHomeGoals() == null || match.getFullTimeAwayGoals() == null
                || Instant.now().isBefore(pausedUntil)) {
            return Optional.empty();
        }
        Instant retry = retryAt.get(match.getId());
        if (retry != null && Instant.now().isBefore(retry)) {
            return Optional.empty();
        }
        try {
            Optional<List<Stat>> fetched = fetch(match);
            if (fetched.isEmpty()) {
                retryAt.put(match.getId(), Instant.now().plus(RETRY_AFTER));
            } else {
                retryAt.remove(match.getId());
            }
            return fetched;
        } catch (RuntimeException ex) {
            String message = String.valueOf(ex.getMessage());
            log.warn("API-Football no dio estadisticas del partido {}: {}", match.getId(), message);
            String lower = message.toLowerCase(Locale.ROOT);
            if (lower.contains("limit") || lower.contains("quota")) {
                pausedUntil = Instant.now().plus(QUOTA_PAUSE);
            }
            retryAt.put(match.getId(), Instant.now().plus(RETRY_AFTER));
            return Optional.empty();
        }
    }

    private Optional<List<Stat>> fetch(Match match) {
        LocalDate day = match.getMatchDate().toLocalDate();
        JsonNode fixtures = fixturesOf(day, SeasonUtil.startYear(match.getMatchDate()));
        Optional<JsonNode> fixture = findFixture(fixtures, match.getHomeTeam().getName(), match.getAwayTeam().getName());
        if (fixture.isEmpty()) {
            return Optional.empty();
        }
        long fixtureId = fixture.get().path("fixture").path("id").asLong();
        long homeApiId = fixture.get().path("teams").path("home").path("id").asLong();
        List<Stat> stats = parseStatistics(client.statistics(fixtureId), homeApiId);
        if (stats.isEmpty()) {
            return Optional.empty();
        }
        try {
            String payload = mapper.writeValueAsString(stats);
            writeTx.executeWithoutResult(status -> repository
                    .save(new MatchApiStats(match.getId(), fixtureId, payload, Instant.now())));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudieron serializar las estadisticas", ex);
        }
        return Optional.of(stats);
    }

    /** Fixtures del día, compartidos entre los partidos de ese día para gastar una sola petición. */
    private JsonNode fixturesOf(LocalDate day, int season) {
        CachedFixtures cached = fixturesByDay.get(day);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.value();
        }
        JsonNode fixtures = client.fixturesOn(day, season);
        fixturesByDay.put(day, new CachedFixtures(fixtures, Instant.now().plus(FIXTURES_TTL)));
        return fixtures;
    }

    private Optional<List<Stat>> parsePayload(String payload) {
        try {
            return Optional.of(mapper.readValue(payload, new TypeReference<List<Stat>>() {
            }));
        } catch (JsonProcessingException ex) {
            log.warn("Estadisticas guardadas ilegibles: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /** El fixture de la lista cuyo local y visitante coinciden (sin tildes ni alias) con los del partido. */
    static Optional<JsonNode> findFixture(JsonNode fixtures, String homeName, String awayName) {
        if (fixtures == null || !fixtures.isArray()) {
            return Optional.empty();
        }
        String home = comparable(homeName);
        String away = comparable(awayName);
        for (JsonNode fixture : fixtures) {
            if (home.equals(comparable(fixture.path("teams").path("home").path("name").asText("")))
                    && away.equals(comparable(fixture.path("teams").path("away").path("name").asText("")))) {
                return Optional.of(fixture);
            }
        }
        return Optional.empty();
    }

    /**
     * Traduce la respuesta de estadísticas (un elemento por equipo) a filas local vs visitante. Un valor nulo cuenta
     * como 0 (la API no informa los ceros) siempre que el otro equipo sí tenga dato; sin dato en ninguno, la fila se
     * omite. Pura (sin E/S) para poder probarla con JSON de ejemplo.
     */
    static List<Stat> parseStatistics(JsonNode response, long homeApiTeamId) {
        JsonNode home = null;
        JsonNode away = null;
        if (response != null && response.isArray()) {
            for (JsonNode entry : response) {
                if (entry.path("team").path("id").asLong() == homeApiTeamId) {
                    home = entry;
                } else {
                    away = entry;
                }
            }
        }
        List<Stat> rows = new ArrayList<>();
        if (home == null || away == null) {
            return rows;
        }
        for (String[] def : STAT_DEFS) {
            Double h = value(home, def[0]);
            Double a = value(away, def[0]);
            if (h == null && a == null) {
                continue;
            }
            rows.add(new Stat(def[1], def[2], h == null ? 0.0 : h, a == null ? 0.0 : a));
        }
        return rows;
    }

    private static Double value(JsonNode team, String type) {
        for (JsonNode stat : team.path("statistics")) {
            if (type.equals(stat.path("type").asText())) {
                JsonNode raw = stat.path("value");
                if (raw.isNumber()) {
                    return raw.asDouble();
                }
                if (raw.isTextual()) {
                    try {
                        return Double.parseDouble(raw.asText().replace("%", "").trim());
                    } catch (NumberFormatException ex) {
                        return null;
                    }
                }
                return null;
            }
        }
        return null;
    }

    /** Nombre comparable: alias canónico de LaLiga, sin tildes y en minúsculas. */
    private static String comparable(String name) {
        String canonical = TeamNameNormalizer.canonical(name);
        return Normalizer.normalize(canonical == null ? "" : canonical, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim();
    }
}
