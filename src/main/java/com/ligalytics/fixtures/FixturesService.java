package com.ligalytics.fixtures;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ligalytics.etl.TeamNameNormalizer;
import com.ligalytics.model.Team;
import com.ligalytics.repository.TeamRepository;

/**
 * Calendario y resultados en vivo. Descarga la temporada completa en una sola
 * petición y la guarda en memoria: 1 minuto si hay partidos en juego o a punto
 * de empezar, 15 minutos si no (respeta los límites de la API gratuita). Si la
 * API falla se sirve la última copia que se tenga.
 */
@Service
public class FixturesService {

    private static final Logger log = LoggerFactory.getLogger(FixturesService.class);
    private static final Duration LIVE_TTL = Duration.ofMinutes(1);
    private static final Duration IDLE_TTL = Duration.ofMinutes(15);

    private final FixturesProvider provider;
    private final TeamRepository teamRepository;

    private List<Fixture> cached = List.of();
    private Instant fetchedAt = Instant.EPOCH;

    public FixturesService(FixturesProvider provider, TeamRepository teamRepository) {
        this.provider = provider;
        this.teamRepository = teamRepository;
    }

    public boolean isConfigured() {
        return provider.isConfigured();
    }

    public String providerName() {
        return provider.name();
    }

    /** Partidos entre dos fechas (inclusive), en la zona horaria indicada. */
    public List<FixtureDto> between(LocalDate from, LocalDate to, ZoneId zone) {
        Map<String, Team> teams = teamsByName();
        return load().stream()
                .filter(f -> {
                    LocalDate day = f.utcDate().atZone(zone).toLocalDate();
                    return !day.isBefore(from) && !day.isAfter(to);
                })
                .map(f -> toDto(f, teams))
                .toList();
    }

    public List<FixtureDto> live() {
        Map<String, Team> teams = teamsByName();
        return load().stream().filter(f -> "LIVE".equals(f.status())).map(f -> toDto(f, teams)).toList();
    }

    private synchronized List<Fixture> load() {
        if (Duration.between(fetchedAt, Instant.now()).compareTo(ttl()) < 0) {
            return cached;
        }
        try {
            cached = provider.fetchSeason();
            fetchedAt = Instant.now();
        } catch (RuntimeException ex) {
            log.warn("No se pudo actualizar el calendario ({}): {}", provider.name(), ex.getMessage());
            if (cached.isEmpty()) {
                throw ex;
            }
            // Se sirve la copia anterior y se reintenta en unos segundos.
            fetchedAt = Instant.now().minus(IDLE_TTL).plus(Duration.ofSeconds(30));
        }
        return cached;
    }

    private Duration ttl() {
        Instant now = Instant.now();
        boolean active = cached.stream().anyMatch(f -> "LIVE".equals(f.status())
                || (f.utcDate().isAfter(now.minus(Duration.ofHours(3)))
                        && f.utcDate().isBefore(now.plus(Duration.ofMinutes(15)))));
        return active ? LIVE_TTL : IDLE_TTL;
    }

    private Map<String, Team> teamsByName() {
        Map<String, Team> byName = new HashMap<>();
        for (Team team : teamRepository.findAll()) {
            byName.put(team.getName().toLowerCase(Locale.ROOT), team);
        }
        return byName;
    }

    private FixtureDto toDto(Fixture f, Map<String, Team> teams) {
        Team home = teams.get(String.valueOf(TeamNameNormalizer.canonical(f.homeName())).toLowerCase(Locale.ROOT));
        Team away = teams.get(String.valueOf(TeamNameNormalizer.canonical(f.awayName())).toLowerCase(Locale.ROOT));
        return new FixtureDto(f.id(), f.utcDate(), f.status(), f.matchday(),
                home != null ? home.getName() : f.homeName(), away != null ? away.getName() : f.awayName(),
                home != null ? home.getId() : null, away != null ? away.getId() : null,
                f.homeCrest(), f.awayCrest(), f.homeGoals(), f.awayGoals());
    }
}
