package com.ligalytics.etl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.ligalytics.etl.dto.EtlSummary;
import com.ligalytics.etl.dto.RawMatch;
import com.ligalytics.etl.dto.RawTeamMarketValue;
import com.ligalytics.etl.dto.RawXg;
import com.ligalytics.etl.parser.FBrefHtmlParser;
import com.ligalytics.etl.parser.FootballDataCsvParser;
import com.ligalytics.etl.parser.TransfermarktHtmlParser;
import com.ligalytics.etl.parser.UnderstatHtmlParser;
import com.ligalytics.model.Match;
import com.ligalytics.model.Team;
import com.ligalytics.patterns.builder.MatchAnalysis;
import com.ligalytics.patterns.builder.AdvancedStats;
import com.ligalytics.patterns.observer.ETLSubject;
import com.ligalytics.patterns.observer.EtlEvent;
import com.ligalytics.patterns.proxy.FBrefProxy;
import com.ligalytics.patterns.proxy.FootballDataProxy;
import com.ligalytics.patterns.proxy.TransfermarktProxy;
import com.ligalytics.patterns.proxy.UnderstatProxy;
import com.ligalytics.repository.MatchRepository;
import com.ligalytics.repository.TeamRepository;

@Service
public class EtlService {

    private static final Logger log = LoggerFactory.getLogger(EtlService.class);

    private final FootballDataProxy footballDataProxy;
    private final UnderstatProxy understatProxy;
    private final FBrefProxy fbRefProxy;
    private final TransfermarktProxy transfermarktProxy;
    private final FootballDataCsvParser footballDataParser;
    private final UnderstatHtmlParser understatParser;
    private final FBrefHtmlParser fbRefParser;
    private final TransfermarktHtmlParser transfermarktParser;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final EtlPersistenceService persistenceService;
    private final ETLSubject etlSubject;
    private final BzzoiroClient bzzoiroClient;

    public EtlService(FootballDataProxy footballDataProxy,
            UnderstatProxy understatProxy,
            FBrefProxy fbRefProxy,
            TransfermarktProxy transfermarktProxy,
            FootballDataCsvParser footballDataParser,
            UnderstatHtmlParser understatParser,
            FBrefHtmlParser fbRefParser,
            TransfermarktHtmlParser transfermarktParser,
            TeamRepository teamRepository,
            MatchRepository matchRepository,
            EtlPersistenceService persistenceService,
            ETLSubject etlSubject,
            BzzoiroClient bzzoiroClient) {
        this.footballDataProxy = footballDataProxy;
        this.understatProxy = understatProxy;
        this.fbRefProxy = fbRefProxy;
        this.transfermarktProxy = transfermarktProxy;
        this.footballDataParser = footballDataParser;
        this.understatParser = understatParser;
        this.fbRefParser = fbRefParser;
        this.transfermarktParser = transfermarktParser;
        this.teamRepository = teamRepository;
        this.matchRepository = matchRepository;
        this.persistenceService = persistenceService;
        this.etlSubject = etlSubject;
        this.bzzoiroClient = bzzoiroClient;
    }

    @Transactional
    public EtlSummary ingestFootballData(String seasonCode, String divisionCode) {
        String location = footballDataProxy.seasonUrl(seasonCode, divisionCode);
        String csv = footballDataProxy.fetch(location);
        List<RawMatch> rawMatches = footballDataParser.parse(csv);
        return persistMatches("football-data", divisionCode + "-" + seasonCode, seasonCode, location, rawMatches);
    }

    /**
     * Ingesta un CSV de football-data ya descargado (subido por el usuario o leido
     * de una carpeta). La temporada se deduce de la fecha mas antigua del archivo.
     */
    @Transactional
    public EtlSummary ingestFootballDataCsv(String csv, String fileName) {
        List<RawMatch> rawMatches = footballDataParser.parse(csv);
        String season = rawMatches.stream()
                .map(RawMatch::matchDate)
                .filter(java.util.Objects::nonNull)
                .min(java.util.Comparator.naturalOrder())
                .map(date -> com.ligalytics.service.SeasonUtil.code(com.ligalytics.service.SeasonUtil.startYear(date)))
                .orElse(null);
        return persistMatches("football-data-csv", fileName, season, "archivo:" + fileName, rawMatches);
    }

    /**
     * Ingesta partidos desde un CSV local (fixture) en lugar de descargarlos en
     * vivo. Se usa para sembrar la base de datos sin depender de la red.
     */
    @Transactional
    public EtlSummary ingestLocalFootballData(String csv, String reference, String season) {
        List<RawMatch> rawMatches = footballDataParser.parse(csv);
        return persistMatches("football-data-local", reference, season, "classpath:fixtures", rawMatches);
    }

    @Transactional
    public EtlSummary ingestFBref(String path) {
        String location = fbRefProxy.pageUrl(path);
        String html = fbRefProxy.fetch(location);
        List<RawMatch> rawMatches = fbRefParser.parse(html);
        return persistMatches("fbref", path, null, location, rawMatches);
    }

    @Transactional
    public EtlSummary ingestUnderstat(String leagueCode, String season) {
        String location = understatProxy.leagueUrl(leagueCode, season);
        String html = understatProxy.fetch(location);
        return applyUnderstat(html, leagueCode + "-" + season, season, location);
    }

    /** Aplica el xG de una pagina de Understat guardada a mano (HTML); la temporada se deduce de las fechas. */
    @Transactional
    public EtlSummary ingestUnderstatHtml(String html, String fileName) {
        String season = understatParser.parse(html).stream()
                .map(RawXg::matchDate)
                .filter(java.util.Objects::nonNull)
                .min(java.util.Comparator.naturalOrder())
                .map(date -> com.ligalytics.service.SeasonUtil.code(com.ligalytics.service.SeasonUtil.startYear(date)))
                .orElse(null);
        return applyUnderstat(html, fileName, season, "archivo:" + fileName);
    }

    /**
     * Aplica xG desde un HTML local (fixture) de Understat sobre los partidos ya
     * cargados.
     */
    @Transactional
    public EtlSummary ingestLocalUnderstat(String html, String reference, String season) {
        return applyUnderstat(html, reference, season, "classpath:fixtures");
    }

    private EtlSummary applyUnderstat(String html, String reference, String season, String location) {
        List<RawXg> rawXg = understatParser.parse(html);

        int updated = 0;
        int skipped = 0;
        Set<Long> affectedTeamIds = new HashSet<>();
        for (RawXg xg : rawXg) {
            Optional<Match> match = findMatch(xg.homeTeam(), xg.awayTeam(), xg.matchDate());
            if (match.isEmpty()) {
                skipped++;
                continue;
            }
            Match entity = match.get();
            if (xg.homeXg() != null) {
                entity.setHomeXg(xg.homeXg());
            }
            if (xg.awayXg() != null) {
                entity.setAwayXg(xg.awayXg());
            }
            matchRepository.save(entity);
            affectedTeamIds.add(entity.getHomeTeam().getId());
            affectedTeamIds.add(entity.getAwayTeam().getId());
            updated++;
        }
        publishEtlEvent("understat", reference, season, affectedTeamIds, 0, updated, skipped, location);
        return new EtlSummary("understat", reference, rawXg.size(), rawXg.size(), 0, updated, skipped, location);
    }

    @Transactional
    public EtlSummary ingestTransfermarktMarketValues(String path) {
        String location = transfermarktProxy.pageUrl(path);
        String html = transfermarktProxy.fetch(location);
        return applyTransfermarkt(html, path, location);
    }

    /**
     * Aplica valores de mercado desde un HTML local (fixture) de Transfermarkt.
     */
    @Transactional
    public EtlSummary ingestLocalTransfermarkt(String html, String reference) {
        return applyTransfermarkt(html, reference, "classpath:fixtures");
    }

    private EtlSummary applyTransfermarkt(String html, String reference, String location) {
        List<RawTeamMarketValue> values = transfermarktParser.parse(html);

        int updated = 0;
        int skipped = 0;
        for (RawTeamMarketValue value : values) {
            Optional<Team> team = teamRepository.findByNameIgnoreCase(canonicalName(value.teamName()));
            if (team.isEmpty() || value.marketValue() == null) {
                skipped++;
                continue;
            }
            Team entity = team.get();
            entity.setMarketValue(value.marketValue());
            teamRepository.save(entity);
            updated++;
        }
        return new EtlSummary("transfermarkt", reference, values.size(), values.size(), 0, updated, skipped, location);
    }

    private EtlSummary persistMatches(String source,
            String reference,
            String season,
            String location,
            List<RawMatch> rawMatches) {
        int created = 0;
        int skipped = 0;
        Set<Long> affectedTeamIds = new HashSet<>();
        for (RawMatch raw : rawMatches) {
            if (raw.matchDate() == null || !StringUtils.hasText(raw.homeTeam()) || !StringUtils.hasText(raw.awayTeam())) {
                skipped++;
                continue;
            }
            Team homeTeam = persistenceService.resolveTeam(canonicalName(raw.homeTeam()));
            Team awayTeam = persistenceService.resolveTeam(canonicalName(raw.awayTeam()));
            LocalDateTime from = raw.matchDate().toLocalDate().atStartOfDay();
            LocalDateTime to = raw.matchDate().toLocalDate().atTime(LocalTime.MAX);

            if (persistenceService.matchExists(homeTeam.getId(), awayTeam.getId(), from, to)) {
                skipped++;
                continue;
            }

            MatchAnalysis analysis = MatchAnalysis.builder()
                    .teams(homeTeam.getName(), awayTeam.getName())
                    .matchDate(raw.matchDate())
                    .score(raw.homeGoals(), raw.awayGoals())
                    .expectedGoals(raw.homeXg(), raw.awayXg())
                    .corners(raw.corners())
                    .cards(raw.yellowCards(), raw.redCards())
                    .teamCards(raw.homeYellowCards(), raw.awayYellowCards(), raw.homeRedCards(), raw.awayRedCards())
                    .advanced(AdvancedStats.HOME_SHOTS, toDouble(raw.homeShots()))
                    .advanced(AdvancedStats.AWAY_SHOTS, toDouble(raw.awayShots()))
                    .advanced(AdvancedStats.HOME_SHOTS_ON_TARGET, toDouble(raw.homeShotsOnTarget()))
                    .advanced(AdvancedStats.AWAY_SHOTS_ON_TARGET, toDouble(raw.awayShotsOnTarget()))
                    .advanced(AdvancedStats.HOME_FOULS, toDouble(raw.homeFouls()))
                    .advanced(AdvancedStats.AWAY_FOULS, toDouble(raw.awayFouls()))
                    .build();
            persistenceService.saveAnalysis(analysis, homeTeam, awayTeam);
            affectedTeamIds.add(homeTeam.getId());
            affectedTeamIds.add(awayTeam.getId());
            created++;
        }
        log.info("[{}] partidos creados={} omitidos={} (total parseado={})", source, created, skipped, rawMatches.size());
        publishEtlEvent(source, reference, season, affectedTeamIds, created, 0, skipped, location);
        return new EtlSummary(source, reference, rawMatches.size(), rawMatches.size(), created, 0, skipped, location);
    }

    private void publishEtlEvent(String source,
            String reference,
            String season,
            Set<Long> teamIds,
            int created,
            int updated,
            int skipped,
            String location) {
        etlSubject.notifyObservers(EtlEvent.of(source, reference, season, teamIds, created, updated, skipped, location));
    }

    /**
     * Completa el xG real de los partidos de una temporada (año de inicio, p. ej. 2023)
     * desde Bzzoiro Sports Data. Solo pide el detalle de los partidos que la API marca
     * con xG y que aun no lo tienen (idempotente y ahorra peticiones del limite diario).
     */
    @Transactional
    public EtlSummary ingestBzzoiroXg(int seasonYear) {
        if (!bzzoiroClient.isConfigured()) {
            throw new IllegalStateException("Bzzoiro no configurado: define BZZOIRO_API_KEY");
        }
        LocalDateTime start = com.ligalytics.service.SeasonUtil.start(seasonYear);
        List<com.fasterxml.jackson.databind.JsonNode> events = bzzoiroClient.finishedEvents(
                start.toLocalDate(), com.ligalytics.service.SeasonUtil.endExclusive(seasonYear).toLocalDate());

        int updated = 0;
        int skipped = 0;
        Set<Long> affectedTeamIds = new HashSet<>();
        for (com.fasterxml.jackson.databind.JsonNode event : events) {
            if (!event.path("has_xg").asBoolean(false)) {
                skipped++;
                continue;
            }
            Optional<Match> match = findMatchAround(event.path("home_team").asText(null),
                    event.path("away_team").asText(null), event.path("event_date").asText(null));
            if (match.isEmpty() || match.get().getHomeXg() != null) {
                skipped++;
                continue;
            }
            java.util.OptionalDouble[] xg = bzzoiroClient.xg(event.path("id").asLong());
            if (xg[0].isEmpty() || xg[1].isEmpty()) {
                skipped++;
                continue;
            }
            Match entity = match.get();
            entity.setHomeXg(xg[0].getAsDouble());
            entity.setAwayXg(xg[1].getAsDouble());
            matchRepository.save(entity);
            affectedTeamIds.add(entity.getHomeTeam().getId());
            affectedTeamIds.add(entity.getAwayTeam().getId());
            updated++;
        }
        String reference = "bzzoiro-" + seasonYear;
        publishEtlEvent("bzzoiro", reference, com.ligalytics.service.SeasonUtil.code(seasonYear), affectedTeamIds, 0,
                updated, skipped, "sports.bzzoiro.com");
        return new EtlSummary("bzzoiro", reference, events.size(), events.size(), 0, updated, skipped,
                "sports.bzzoiro.com");
    }

    /**
     * Guarda los partidos ya finalizados de un calendario externo (football-data.org) que aun no estan en la
     * base de datos: sirve para mantener al dia la temporada en curso con los goles. La fecha se guarda en
     * hora de Madrid para coincidir con los CSV de football-data; la comprobacion de existencia admite un dia
     * de margen para no duplicar partidos al subir despues el CSV.
     */
    @Transactional
    public EtlSummary ingestFinishedFixtures(List<com.ligalytics.fixtures.Fixture> fixtures) {
        int created = 0;
        int skipped = 0;
        Set<Long> affectedTeamIds = new HashSet<>();
        for (com.ligalytics.fixtures.Fixture fixture : fixtures) {
            if (!"FINISHED".equals(fixture.status()) || fixture.homeGoals() == null || fixture.awayGoals() == null) {
                skipped++;
                continue;
            }
            LocalDateTime date = LocalDateTime.ofInstant(fixture.utcDate(), java.time.ZoneId.of("Europe/Madrid"));
            Team homeTeam = persistenceService.resolveTeam(canonicalName(fixture.homeName()));
            Team awayTeam = persistenceService.resolveTeam(canonicalName(fixture.awayName()));
            LocalDate day = date.toLocalDate();
            if (persistenceService.matchExists(homeTeam.getId(), awayTeam.getId(),
                    day.minusDays(1).atStartOfDay(), day.plusDays(1).atTime(LocalTime.MAX))) {
                skipped++;
                continue;
            }
            MatchAnalysis analysis = MatchAnalysis.builder()
                    .teams(homeTeam.getName(), awayTeam.getName())
                    .matchDate(date)
                    .score(fixture.homeGoals(), fixture.awayGoals())
                    .build();
            persistenceService.saveAnalysis(analysis, homeTeam, awayTeam);
            affectedTeamIds.add(homeTeam.getId());
            affectedTeamIds.add(awayTeam.getId());
            created++;
        }
        String season = fixtures.stream().map(com.ligalytics.fixtures.Fixture::utcDate).max(java.util.Comparator.naturalOrder())
                .map(i -> com.ligalytics.service.SeasonUtil.code(com.ligalytics.service.SeasonUtil.startYear(
                        LocalDateTime.ofInstant(i, java.time.ZoneId.of("Europe/Madrid")))))
                .orElse(null);
        publishEtlEvent("football-data-org", "calendario", season, affectedTeamIds, created, 0, skipped, "football-data.org");
        return new EtlSummary("football-data-org", "calendario", fixtures.size(), fixtures.size(), created, 0, skipped,
                "football-data.org");
    }

    /** Busca el partido por equipos y fecha con un dia de margen (la API usa UTC y los CSV hora local). */
    private Optional<Match> findMatchAround(String homeName, String awayName, String isoDate) {
        if (homeName == null || awayName == null || isoDate == null) {
            return Optional.empty();
        }
        Optional<Team> home = teamRepository.findByNameIgnoreCase(canonicalName(homeName));
        Optional<Team> away = teamRepository.findByNameIgnoreCase(canonicalName(awayName));
        if (home.isEmpty() || away.isEmpty()) {
            return Optional.empty();
        }
        LocalDate day = java.time.OffsetDateTime.parse(isoDate).toLocalDate();
        return matchRepository.findFirstByHomeTeamIdAndAwayTeamIdAndMatchDateBetween(home.get().getId(),
                away.get().getId(), day.minusDays(1).atStartOfDay(), day.plusDays(1).atTime(LocalTime.MAX));
    }

    private Optional<Match> findMatch(String homeTeamName, String awayTeamName, LocalDateTime date) {
        if (date == null) {
            return Optional.empty();
        }
        Optional<Team> homeTeam = teamRepository.findByNameIgnoreCase(canonicalName(homeTeamName));
        Optional<Team> awayTeam = teamRepository.findByNameIgnoreCase(canonicalName(awayTeamName));
        if (homeTeam.isEmpty() || awayTeam.isEmpty()) {
            return Optional.empty();
        }
        LocalDate day = date.toLocalDate();
        return matchRepository.findFirstByHomeTeamIdAndAwayTeamIdAndMatchDateBetween(
                homeTeam.get().getId(), awayTeam.get().getId(), day.atStartOfDay(), day.atTime(LocalTime.MAX));
    }

    private static Double toDouble(Integer value) {
        return value == null ? null : value.doubleValue();
    }

    public String canonicalName(String rawName) {
        return TeamNameNormalizer.canonical(rawName);
    }
}
