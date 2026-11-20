package com.ligalytics.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.ligalytics.etl.BzzoiroClient;
import com.ligalytics.exception.ExternalServiceException;
import com.ligalytics.exception.ResourceNotFoundException;
import com.ligalytics.model.Match;
import com.ligalytics.repository.MatchRepository;
import com.ligalytics.service.dto.MatchDetailDto;
import com.ligalytics.service.dto.MatchDetailDto.Stat;

/**
 * Detalle y estadísticas de un partido. Los partidos jugados salen de la base de datos; los que están en juego (o
 * acaban de terminar) se piden al proveedor en vivo con una caché corta, porque el proveedor ya cachea 30 s y
 * consultarlo más a menudo no da datos más frescos.
 */
@Service
public class MatchDetailService {

    private static final Duration LIVE_TTL = Duration.ofSeconds(20);

    /** clave, nombre en la API, alternativa en la API, etiqueta. */
    private static final String[][] STAT_DEFS = {
            { "possession", "ball_possession", "possession", "Posesión (%)" },
            { "xg", "xg", "expected_goals", "xG (goles esperados)" },
            { "shots", "total_shots", "shots", "Tiros" },
            { "shotsOnTarget", "shots_on_target", "shots_on_goal", "Tiros a puerta" },
            { "corners", "corner_kicks", "corners", "Córneres" },
            { "fouls", "fouls", "fouls_committed", "Faltas" },
            { "offsides", "offsides", "offside", "Fuera de juego" },
            { "yellowCards", "yellow_cards", "yellow", "Tarjetas amarillas" },
            { "redCards", "red_cards", "red", "Tarjetas rojas" },
            { "saves", "goalkeeper_saves", "saves", "Paradas" },
            { "passes", "total_passes", "passes", "Pases" },
    };

    private record Cached(MatchDetailDto value, Instant expiresAt) {
    }

    private final MatchRepository matchRepository;
    private final BzzoiroClient bzzoiro;
    private final Map<Long, Cached> liveCache = new ConcurrentHashMap<>();

    private final ApiFootballStatsService apiFootballStats;

    public MatchDetailService(MatchRepository matchRepository, BzzoiroClient bzzoiro,
            ApiFootballStatsService apiFootballStats) {
        this.matchRepository = matchRepository;
        this.bzzoiro = bzzoiro;
        this.apiFootballStats = apiFootballStats;
    }

    /** Partido ya jugado y guardado en la base de datos. */
    @Transactional(readOnly = true)
    public MatchDetailDto played(long id) {
        Match m = matchRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado: " + id));
        List<Stat> stats = new ArrayList<>();
        add(stats, "xg", "xG (goles esperados)", m.getHomeXg(), m.getAwayXg());
        add(stats, "shots", "Tiros", m.getHomeShots(), m.getAwayShots());
        add(stats, "shotsOnTarget", "Tiros a puerta", m.getHomeShotsOnTarget(), m.getAwayShotsOnTarget());
        add(stats, "fouls", "Faltas", m.getHomeFouls(), m.getAwayFouls());
        add(stats, "yellowCards", "Tarjetas amarillas", m.getHomeYellowCards(), m.getAwayYellowCards());
        add(stats, "redCards", "Tarjetas rojas", m.getHomeRedCards(), m.getAwayRedCards());
        if (m.getCorners() != null) {
            // La base guarda los córneres solo como total del partido: se muestra sin reparto por equipo.
            stats.add(new Stat("corners", "Córneres (total)", m.getCorners().doubleValue(), null));
        }
        // Si API-Football tiene el partido, sus estadísticas reales (por equipo, con córneres de cada uno) sustituyen
        // a las de la base de datos; si no, se muestran las guardadas.
        List<Stat> exact = apiFootballStats.statsFor(m).orElse(null);
        if (exact != null) {
            stats = exact;
        }
        LocalDateTime date = m.getMatchDate();
        return new MatchDetailDto(m.getId(), null, "FINISHED", null, date, SeasonUtil.label(SeasonUtil.startYear(date)),
                m.getHomeTeam().getId(), m.getHomeTeam().getName(), m.getAwayTeam().getId(), m.getAwayTeam().getName(),
                m.getFullTimeHomeGoals(), m.getFullTimeAwayGoals(), stats);
    }

    /** Partido del proveedor (en vivo, por jugar o recién terminado); respuesta cacheada unos segundos. */
    public MatchDetailDto live(long eventId) {
        Cached cached = liveCache.get(eventId);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.value();
        }
        if (!bzzoiro.isConfigured()) {
            throw new ExternalServiceException("Directo no configurado: define BZZOIRO_API_KEY", null);
        }
        JsonNode event = bzzoiro.json("/events/" + eventId + "/");
        JsonNode node = event.has("event") ? event.path("event") : event;
        if (node.isMissingNode() || node.isEmpty()) {
            throw new ResourceNotFoundException("Partido no encontrado: " + eventId);
        }
        JsonNode stats;
        try {
            stats = bzzoiro.json("/events/" + eventId + "/stats/").path("stats");
        } catch (RuntimeException ex) {
            stats = null; // el partido se muestra igual, sin estadísticas
        }
        MatchDetailDto dto = fromProvider(eventId, node, stats);
        liveCache.put(eventId, new Cached(dto, Instant.now().plus(LIVE_TTL)));
        return dto;
    }

    /** Traduce la respuesta del proveedor; pura (sin E/S) para poder probarla con JSON de ejemplo. */
    static MatchDetailDto fromProvider(long eventId, JsonNode node, JsonNode stats) {
        String status = switch (node.path("status").asText("")) {
            case "inprogress", "penalties" -> "LIVE";
            case "finished" -> "FINISHED";
            case "notstarted", "delayed" -> "SCHEDULED";
            default -> "OTHER";
        };
        LocalDateTime date = null;
        String kickoff = firstText(node, "event_date", "start_time", "kickoff", "date");
        if (kickoff != null) {
            try {
                date = OffsetDateTime.parse(kickoff).atZoneSameInstant(ZoneId.of("Europe/Madrid")).toLocalDateTime();
            } catch (RuntimeException ex) {
                date = null;
            }
        }
        List<Stat> rows = new ArrayList<>();
        if (stats != null && stats.isObject()) {
            JsonNode home = stats.path("home");
            JsonNode away = stats.path("away");
            for (String[] def : STAT_DEFS) {
                Double h = numeric(home, def[1]);
                Double a = numeric(away, def[1]);
                if (h == null || a == null) {
                    h = numeric(home, def[2]);
                    a = numeric(away, def[2]);
                }
                if (h != null && a != null) {
                    rows.add(new Stat(def[0], def[3], h, a));
                }
            }
        }
        return new MatchDetailDto(null, eventId, status, intOrNull(node, "current_minute"), date,
                date == null ? null : SeasonUtil.label(SeasonUtil.startYear(date)), null,
                node.path("home_team").asText(""), null, node.path("away_team").asText(""),
                intOrNull(node, "home_score"), intOrNull(node, "away_score"), rows);
    }

    private static void add(List<Stat> rows, String key, String label, Number home, Number away) {
        if (home != null && away != null) {
            rows.add(new Stat(key, label, home.doubleValue(), away.doubleValue()));
        }
    }

    private static Double numeric(JsonNode team, String field) {
        JsonNode value = team.path(field);
        if (value.isObject()) {
            value = value.path("actual");
        }
        if (value.isNumber()) {
            return value.asDouble();
        }
        if (value.isTextual()) {
            try {
                return Double.parseDouble(value.asText().replace("%", "").trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private static Integer intOrNull(JsonNode node, String field) {
        return node.path(field).isNumber() ? node.path(field).asInt() : null;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.path(field).isTextual() && !node.path(field).asText().isBlank()) {
                return node.path(field).asText();
            }
        }
        return null;
    }
}
