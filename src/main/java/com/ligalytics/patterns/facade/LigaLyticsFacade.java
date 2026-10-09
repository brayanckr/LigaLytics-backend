package com.ligalytics.patterns.facade;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligalytics.ai.MatchDatasetConverter;
import com.ligalytics.ai.TeamFormTracker;
import com.ligalytics.etl.EtlService;
import com.ligalytics.etl.dto.EtlSummary;
import com.ligalytics.exception.ResourceNotFoundException;
import com.ligalytics.model.Match;
import com.ligalytics.model.Team;
import com.ligalytics.patterns.builder.MatchAnalysis;
import com.ligalytics.patterns.decorator.PartidoBase;
import com.ligalytics.patterns.decorator.PartidoComponent;
import com.ligalytics.patterns.decorator.TransfermarktDecorator;
import com.ligalytics.patterns.decorator.UnderstatDecorator;
import com.ligalytics.patterns.factory.PredictionStrategyResolver;
import com.ligalytics.patterns.factory.PredictorType;
import com.ligalytics.patterns.strategy.PoissonStrategy;
import com.ligalytics.patterns.strategy.Prediction;
import com.ligalytics.patterns.strategy.PredictionStrategy;
import com.ligalytics.repository.MatchRepository;
import com.ligalytics.repository.TeamRepository;
import com.ligalytics.service.PredictionCache;
import com.ligalytics.service.PredictionLogService;
import com.ligalytics.service.SeasonUtil;
import com.ligalytics.service.StandingsHeap;
import com.ligalytics.service.StandingsHeap.Standing;
import com.ligalytics.service.dto.DataStatusDto;
import com.ligalytics.service.dto.EtlSeasonResult;
import com.ligalytics.service.dto.MatchSummaryDto;
import com.ligalytics.service.dto.PredictionResponseDto;
import com.ligalytics.service.dto.RankingEntryDto;
import com.ligalytics.service.dto.SeasonDto;
import com.ligalytics.service.dto.TeamDto;
import com.ligalytics.service.dto.TeamStatsDto;

/**
 * Patrón <b>Facade</b>: orquestador central de LigaLytics. Simplifica para los
 * controladores la interacción entre la base de datos JPA, el motor de análisis
 * (Builder), la caché de predicciones, el heap de la clasificación y las
 * estrategias/fábrica de predictores.
 */
@Service
public class LigaLyticsFacade {

    private static final Logger log = LoggerFactory.getLogger(LigaLyticsFacade.class);

    private static final int FORM_MATCHES = 5;
    private static final int RECENT_MATCHES = 10;
    private static final double DEFAULT_RED_SHARE = 0.04;

    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final PredictionCache predictionCache;
    private final PredictionStrategyResolver strategyResolver;
    private final EtlService etlService;
    private final MatchDatasetConverter datasetConverter;
    private final PredictionLogService predictionLogService;

    public LigaLyticsFacade(TeamRepository teamRepository,
            MatchRepository matchRepository,
            PredictionCache predictionCache,
            PredictionStrategyResolver strategyResolver,
            EtlService etlService,
            MatchDatasetConverter datasetConverter,
            PredictionLogService predictionLogService) {
        this.teamRepository = teamRepository;
        this.matchRepository = matchRepository;
        this.predictionCache = predictionCache;
        this.strategyResolver = strategyResolver;
        this.etlService = etlService;
        this.datasetConverter = datasetConverter;
        this.predictionLogService = predictionLogService;
    }

    /**
     * Lista los equipos de la temporada más reciente (los 20 de LaLiga),
     * opcionalmente filtrados por nombre.
     */
    @Transactional(readOnly = true)
    public List<TeamDto> listTeams(String query) {
        Optional<Integer> latest = latestSeasonYear();
        Collection<Team> teams = latest.isPresent()
                ? teamsInSeason(latest.get())
                : teamRepository.findAll();
        String filter = query == null ? "" : query.trim().toLowerCase();
        return teams.stream()
                .filter(team -> filter.isEmpty() || team.getName().toLowerCase().contains(filter))
                .map(this::toTeamDto)
                .sorted(Comparator.comparing(TeamDto::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Temporadas con datos, de la más reciente a la más antigua. */
    @Transactional(readOnly = true)
    public List<SeasonDto> seasons() {
        LocalDateTime first = matchRepository.findEarliestMatchDate();
        LocalDateTime last = matchRepository.findLatestMatchDate();
        List<SeasonDto> seasons = new ArrayList<>();
        if (first == null || last == null) {
            return seasons;
        }
        for (int year = SeasonUtil.startYear(last); year >= SeasonUtil.startYear(first); year--) {
            long matches = matchRepository.findBetweenWithTeams(SeasonUtil.start(year), SeasonUtil.endExclusive(year))
                    .size();
            seasons.add(new SeasonDto(year, SeasonUtil.label(year), SeasonUtil.code(year), matches));
        }
        return seasons;
    }

    /**
     * Estadísticas e historial de un equipo en una temporada (por defecto la
     * más reciente en la que jugó).
     */
    @Transactional(readOnly = true)
    public TeamStatsDto getTeamStats(Long teamId, Integer seasonYear) {
        Team team = findTeam(teamId);
        List<Match> allMatches = matchRepository.findByHomeTeamIdOrAwayTeamId(teamId, teamId);

        Integer year = seasonYear != null ? seasonYear : allMatches.stream()
                .filter(match -> match.getMatchDate() != null)
                .map(match -> SeasonUtil.startYear(match.getMatchDate()))
                .max(Integer::compare)
                .orElse(null);
        List<Match> matches = year == null ? allMatches : allMatches.stream()
                .filter(match -> match.getMatchDate() != null && SeasonUtil.startYear(match.getMatchDate()) == year)
                .toList();

        Aggregate aggregate = aggregate(teamId, matches);
        List<Match> newestFirst = matches.stream()
                .sorted(Comparator.comparing(Match::getMatchDate,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
        List<MatchSummaryDto> recentMatches = newestFirst.stream()
                .limit(RECENT_MATCHES)
                .map(this::toMatchSummary)
                .toList();

        return new TeamStatsDto(team.getId(), team.getName(), team.getStadium(), team.getMarketValue(),
                aggregate.played(), aggregate.wins(), aggregate.draws(), aggregate.losses(),
                aggregate.goalsFor(), aggregate.goalsAgainst(), aggregate.points(),
                aggregate.averageGoalsFor(), aggregate.averageGoalsAgainst(),
                aggregate.averageCorners(), aggregate.averageYellowCards(), recentMatches,
                year == null ? null : SeasonUtil.label(year), recentForm(teamId, newestFirst));
    }

    /**
     * Predicción completa de un partido usando las estrategias creadas por la
     * fábrica. Las variables se calculan con los datos conocidos hasta el último
     * partido cargado. El resultado se cachea, se guarda en la base de datos y
     * se invalida cuando el ETL ingiere datos.
     */
    @Transactional(readOnly = true)
    public PredictionResponseDto predict(Long homeTeamId, Long awayTeamId) {
        if (homeTeamId == null || awayTeamId == null) {
            throw new IllegalArgumentException("Los identificadores de ambos equipos son obligatorios");
        }
        if (homeTeamId.equals(awayTeamId)) {
            throw new IllegalArgumentException("El equipo local y el visitante deben ser distintos");
        }

        Team home = findTeam(homeTeamId);
        Team away = findTeam(awayTeamId);

        String cacheKey = cacheKey(homeTeamId, awayTeamId);
        Optional<PredictionResponseDto> cached = predictionCache.get(cacheKey);
        if (cached.isPresent()) {
            return cached.get().asCached();
        }

        MatchAnalysis analysis = buildAnalysis(home, away);

        PredictionStrategy ownResultStrategy = strategyResolver.resolveStrategy(PredictorType.RESULTADO);
        // Ganador: modelo externo (Football Charts) si tiene el partido; si no, el modelo propio.
        PredictionStrategy resultStrategy = strategyResolver.resolveExternalResultStrategy(analysis)
                .orElse(ownResultStrategy);
        boolean external = resultStrategy != ownResultStrategy;
        PredictionStrategy goalsStrategy = strategyResolver.resolveStrategy(PredictorType.GOLES);
        PredictionStrategy cornersStrategy = strategyResolver.resolveStrategy(PredictorType.CORNERES);
        PredictionStrategy cardsStrategy = strategyResolver.resolveStrategy(PredictorType.TARJETAS);

        Prediction result = strategyResolver.predictorFor(PredictorType.RESULTADO, resultStrategy).predict(analysis);
        Prediction ownResult = external
                ? strategyResolver.predictorFor(PredictorType.RESULTADO, ownResultStrategy).predict(analysis)
                : result;
        Prediction goals = strategyResolver.predictorFor(PredictorType.GOLES, goalsStrategy).predict(analysis);
        Prediction corners = strategyResolver.predictorFor(PredictorType.CORNERES, cornersStrategy).predict(analysis);
        Prediction cards = strategyResolver.predictorFor(PredictorType.TARJETAS, cardsStrategy).predict(analysis);

        double homeProbability = result.homeValue();
        double awayProbability = result.awayValue();
        double drawProbability = Math.max(0.0, 1.0 - homeProbability - awayProbability);
        double ownHome = ownResult.homeValue();
        double ownAway = ownResult.awayValue();
        double ownDraw = Math.max(0.0, 1.0 - ownHome - ownAway);

        CardsBreakdown cardsBreakdown = CardsBreakdown.of(cards, analysis);
        Map<String, String> strategies = new LinkedHashMap<>();
        strategies.put("resultado", resultStrategy.name());
        strategies.put("goles", goalsStrategy.name());
        strategies.put("corneres", cornersStrategy.name());
        strategies.put("tarjetas", cardsStrategy.name());

        PredictionResponseDto response = new PredictionResponseDto(
                home.getId(), home.getName(), away.getId(), away.getName(),
                result.outcome(), homeProbability, drawProbability, awayProbability,
                round(goals.homeValue()), round(goals.awayValue()), goals.outcome(),
                PoissonStrategy.mostLikelyScore(goals.homeValue(), goals.awayValue()),
                round(corners.total()), corners.outcome(),
                round(cards.total()), cards.outcome(),
                cardsBreakdown.homeYellow(), cardsBreakdown.awayYellow(),
                cardsBreakdown.homeRed(), cardsBreakdown.awayRed(),
                strategies, analysis.getSeason(),
                external ? resultStrategy.name() : "modelo-propio", ownHome, ownDraw, ownAway,
                false, java.time.Instant.now());

        predictionCache.put(cacheKey, response);
        predictionLogService.save(response);
        return response;
    }

    /**
     * Tabla de posiciones de una temporada (por defecto la más reciente). El
     * orden lo calcula un heap ({@link StandingsHeap}).
     */
    @Transactional(readOnly = true)
    public List<RankingEntryDto> ranking(Integer seasonYear) {
        Integer year = seasonYear != null ? seasonYear : latestSeasonYear().orElse(null);
        if (year == null) {
            return List.of();
        }
        List<Match> matches = matchRepository.findBetweenWithTeams(SeasonUtil.start(year), SeasonUtil.endExclusive(year));

        Map<Long, Team> teams = new LinkedHashMap<>();
        for (Match match : matches) {
            teams.putIfAbsent(match.getHomeTeam().getId(), match.getHomeTeam());
            teams.putIfAbsent(match.getAwayTeam().getId(), match.getAwayTeam());
        }

        Map<Long, Aggregate> aggregates = new LinkedHashMap<>();
        List<Standing> standings = new ArrayList<>();
        for (Team team : teams.values()) {
            Aggregate aggregate = aggregate(team.getId(), matches.stream()
                    .filter(match -> match.getHomeTeam().getId().equals(team.getId())
                            || match.getAwayTeam().getId().equals(team.getId()))
                    .toList());
            aggregates.put(team.getId(), aggregate);
            standings.add(new Standing(team.getId(), team.getName(), aggregate.points(),
                    aggregate.goalsFor() - aggregate.goalsAgainst(), aggregate.goalsFor()));
        }

        List<RankingEntryDto> ranked = new ArrayList<>(standings.size());
        int position = 1;
        for (Standing standing : StandingsHeap.rank(standings)) {
            Aggregate aggregate = aggregates.get(standing.teamId());
            ranked.add(new RankingEntryDto(position++, standing.teamId(), standing.teamName(), aggregate.played(),
                    aggregate.wins(), aggregate.draws(), aggregate.losses(), aggregate.goalsFor(),
                    aggregate.goalsAgainst(), standing.goalDifference(), aggregate.points()));
        }
        return ranked;
    }

    /**
     * Fuerza la descarga e ingesta de temporadas de football-data. Cada
     * temporada se procesa en su propia transacción (la abre {@link EtlService});
     * si una falla se registra el error y se continúa con las demás. Al terminar
     * cada temporada, el ETL notifica a los observadores (invalidación de caché,
     * estadísticas de equipos, reentrenamiento).
     *
     * <p>Este método no es transaccional a propósito: así un fallo en una
     * temporada no revierte las ya cargadas.</p>
     */
    public List<EtlSeasonResult> refreshFootballData(List<String> seasonCodes, String divisionCode) {
        List<EtlSeasonResult> results = new ArrayList<>();
        for (String season : seasonCodes) {
            try {
                EtlSummary summary = etlService.ingestFootballData(season, divisionCode);
                log.info("Temporada {} ({}) cargada: {} partidos nuevos, {} omitidos",
                        season, divisionCode, summary.created(), summary.skipped());
                results.add(EtlSeasonResult.ok(season, summary));
            } catch (RuntimeException ex) {
                log.error("Falló la carga de la temporada {} ({})", season, divisionCode, ex);
                results.add(EtlSeasonResult.failed(season, ex.getMessage()));
            }
        }
        return results;
    }

    /**
     * Descarga el xG de Understat para cada temporada (año de inicio, p. ej. 2023)
     * y lo aplica a los partidos ya cargados de football-data.
     */
    public List<EtlSeasonResult> refreshUnderstat(String leagueCode, List<String> seasonStartYears) {
        List<EtlSeasonResult> results = new ArrayList<>();
        for (String season : seasonStartYears) {
            try {
                EtlSummary summary = etlService.ingestUnderstat(leagueCode, season);
                results.add(EtlSeasonResult.ok(season, summary));
            } catch (RuntimeException ex) {
                log.warn("Falló la carga de Understat {} {}: {}", leagueCode, season, ex.getMessage());
                results.add(EtlSeasonResult.failed(season, ex.getMessage()));
            }
        }
        return results;
    }

    /** Importa CSV de football-data (nombre, contenido); cada archivo en su transaccion. */
    public List<EtlSeasonResult> importFootballDataCsv(java.util.Map<String, String> filesByName) {
        List<EtlSeasonResult> results = new ArrayList<>();
        filesByName.forEach((name, csv) -> {
            try {
                results.add(EtlSeasonResult.ok(name, etlService.ingestFootballDataCsv(csv, name)));
            } catch (RuntimeException ex) {
                log.error("Fallo al importar {}", name, ex);
                results.add(EtlSeasonResult.failed(name, ex.getMessage()));
            }
        });
        return results;
    }

    /** Importa paginas de Understat guardadas como HTML (nombre, contenido). */
    public List<EtlSeasonResult> importUnderstatHtml(java.util.Map<String, String> filesByName) {
        List<EtlSeasonResult> results = new ArrayList<>();
        filesByName.forEach((name, html) -> {
            try {
                results.add(EtlSeasonResult.ok(name, etlService.ingestUnderstatHtml(html, name)));
            } catch (RuntimeException ex) {
                results.add(EtlSeasonResult.failed(name, ex.getMessage()));
            }
        });
        return results;
    }

    /** Completa el xG real desde Bzzoiro para las temporadas indicadas (año de inicio). */
    public List<EtlSeasonResult> refreshBzzoiroXg(List<Integer> seasonStartYears) {
        List<EtlSeasonResult> results = new ArrayList<>();
        for (int year : seasonStartYears) {
            try {
                EtlSummary summary = etlService.ingestBzzoiroXg(year);
                log.info("Bzzoiro {}: {} partidos con xG nuevo, {} omitidos", year, summary.updated(), summary.skipped());
                results.add(EtlSeasonResult.ok(String.valueOf(year), summary));
            } catch (RuntimeException ex) {
                log.warn("Falló Bzzoiro {}: {}", year, ex.getMessage());
                results.add(EtlSeasonResult.failed(String.valueOf(year), ex.getMessage()));
            }
        }
        return results;
    }

    /** Descarga de FBref el calendario con resultados (y xG si la página lo incluye). */
    public EtlSeasonResult refreshFBref(String path) {
        try {
            return EtlSeasonResult.ok(path, etlService.ingestFBref(path));
        } catch (RuntimeException ex) {
            log.warn("Falló la carga de FBref {}: {}", path, ex.getMessage());
            return EtlSeasonResult.failed(path, ex.getMessage());
        }
    }

    /** Descarga de Transfermarkt los valores de mercado de las plantillas. */
    public EtlSeasonResult refreshTransfermarkt(String path) {
        try {
            return EtlSeasonResult.ok(path, etlService.ingestTransfermarktMarketValues(path));
        } catch (RuntimeException ex) {
            log.warn("Falló la carga de Transfermarkt {}: {}", path, ex.getMessage());
            return EtlSeasonResult.failed(path, ex.getMessage());
        }
    }

    /**
     * Resumen de lo que hay cargado en la base de datos.
     */
    @Transactional(readOnly = true)
    public DataStatusDto dataStatus() {
        return new DataStatusDto(teamRepository.count(), matchRepository.count(),
                matchRepository.findEarliestMatchDate(), matchRepository.findLatestMatchDate());
    }

    private MatchAnalysis buildAnalysis(Team home, Team away) {
        List<Match> history = matchRepository.findAllWithTeams();
        TeamFormTracker tracker = datasetConverter.replay(history);
        LocalDateTime asOf = history.stream()
                .map(Match::getMatchDate)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .map(date -> date.plusDays(1))
                .orElse(LocalDateTime.now());
        return tracker.analysisFor(home, away, asOf);
    }

    private Optional<Integer> latestSeasonYear() {
        LocalDateTime last = matchRepository.findLatestMatchDate();
        return last == null ? Optional.empty() : Optional.of(SeasonUtil.startYear(last));
    }

    private Collection<Team> teamsInSeason(int seasonYear) {
        Map<Long, Team> teams = new LinkedHashMap<>();
        for (Match match : matchRepository.findBetweenWithTeams(SeasonUtil.start(seasonYear),
                SeasonUtil.endExclusive(seasonYear))) {
            teams.putIfAbsent(match.getHomeTeam().getId(), match.getHomeTeam());
            teams.putIfAbsent(match.getAwayTeam().getId(), match.getAwayTeam());
        }
        return teams.values();
    }

    private Team findTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Equipo no encontrado con id " + teamId));
    }

    private Aggregate aggregate(Long teamId, List<Match> matches) {
        int played = 0;
        int wins = 0;
        int draws = 0;
        int losses = 0;
        int goalsFor = 0;
        int goalsAgainst = 0;
        int corners = 0;
        int yellowCards = 0;

        for (Match match : matches) {
            if (match.getFullTimeHomeGoals() == null || match.getFullTimeAwayGoals() == null) {
                continue;
            }
            boolean isHome = match.getHomeTeam().getId().equals(teamId);
            int scored = isHome ? match.getFullTimeHomeGoals() : match.getFullTimeAwayGoals();
            int conceded = isHome ? match.getFullTimeAwayGoals() : match.getFullTimeHomeGoals();

            played++;
            goalsFor += scored;
            goalsAgainst += conceded;
            if (scored > conceded) {
                wins++;
            } else if (scored == conceded) {
                draws++;
            } else {
                losses++;
            }
            if (match.getCorners() != null) {
                corners += match.getCorners();
            }
            if (match.getYellowCards() != null) {
                yellowCards += match.getYellowCards();
            }
        }

        return new Aggregate(played, wins, draws, losses, goalsFor, goalsAgainst, wins * 3 + draws,
                average(goalsFor, played), average(goalsAgainst, played), average(corners, played),
                average(yellowCards, played));
    }

    /** Forma de los últimos partidos jugados (el más reciente a la derecha). */
    private String recentForm(Long teamId, List<Match> newestFirst) {
        StringBuilder form = new StringBuilder();
        int counted = 0;
        for (Match match : newestFirst) {
            if (match.getFullTimeHomeGoals() == null || match.getFullTimeAwayGoals() == null) {
                continue;
            }
            form.insert(0, formCharacter(match, teamId));
            if (++counted == FORM_MATCHES) {
                break;
            }
        }
        return form.toString();
    }

    private String formCharacter(Match match, Long teamId) {
        boolean isHome = match.getHomeTeam().getId().equals(teamId);
        int scored = isHome ? match.getFullTimeHomeGoals() : match.getFullTimeAwayGoals();
        int conceded = isHome ? match.getFullTimeAwayGoals() : match.getFullTimeHomeGoals();
        if (scored > conceded) {
            return "W";
        }
        return scored == conceded ? "D" : "L";
    }

    private TeamDto toTeamDto(Team team) {
        return new TeamDto(team.getId(), team.getName(), team.getStadium(), team.getMarketValue());
    }

    private MatchSummaryDto toMatchSummary(Match match) {
        return new MatchSummaryDto(match.getId(), match.getMatchDate(),
                match.getHomeTeam().getName(), match.getAwayTeam().getName(),
                match.getFullTimeHomeGoals(), match.getFullTimeAwayGoals(),
                match.getHomeXg(), match.getAwayXg(), match.getCorners(),
                match.getYellowCards(), match.getRedCards(), decorate(match).descripcion());
    }

    /**
     * Patron Decorator: el partido base (marcador) se enriquece por capas con el
     * xG de Understat y el valor de plantilla de Transfermarkt cuando existen.
     */
    private PartidoComponent decorate(Match match) {
        PartidoComponent partido = new PartidoBase(match.getId(), match.getHomeTeam().getName(),
                match.getAwayTeam().getName(), match.getMatchDate(),
                match.getFullTimeHomeGoals(), match.getFullTimeAwayGoals());
        if (match.getHomeXg() != null || match.getAwayXg() != null) {
            partido = new UnderstatDecorator(partido, match.getHomeXg(), match.getAwayXg());
        }
        if (match.getHomeTeam().getMarketValue() != null || match.getAwayTeam().getMarketValue() != null) {
            partido = new TransfermarktDecorator(partido, match.getHomeTeam().getMarketValue(),
                    match.getAwayTeam().getMarketValue());
        }
        return partido;
    }

    private String cacheKey(Long homeTeamId, Long awayTeamId) {
        return "prediction:" + homeTeamId + ":" + awayTeamId;
    }

    private static double average(int total, int played) {
        return played == 0 ? 0.0 : (double) total / played;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record Aggregate(int played, int wins, int draws, int losses, int goalsFor, int goalsAgainst,
            int points, double averageGoalsFor, double averageGoalsAgainst, double averageCorners,
            double averageYellowCards) {
    }

    /**
     * Reparto de las tarjetas totales estimadas en amarillas y rojas de cada
     * equipo, según la proporción histórica de rojas y el peso de cada equipo.
     */
    private record CardsBreakdown(double homeYellow, double awayYellow, double homeRed, double awayRed) {

        static CardsBreakdown of(Prediction cards, MatchAnalysis analysis) {
            double total = cards.total();
            double homeShare = total > 0.0 ? cards.homeValue() / total : 0.5;

            double yellow = value(analysis.getHomeAverageYellowCards()) + value(analysis.getAwayAverageYellowCards());
            double red = value(analysis.getHomeAverageRedCards()) + value(analysis.getAwayAverageRedCards());
            double redShare = yellow + red > 0.0 ? red / (yellow + red) : DEFAULT_RED_SHARE;

            double redTotal = total * redShare;
            double yellowTotal = total - redTotal;
            return new CardsBreakdown(round(yellowTotal * homeShare), round(yellowTotal * (1.0 - homeShare)),
                    round(redTotal * homeShare), round(redTotal * (1.0 - homeShare)));
        }

        private static double value(Double number) {
            return number == null ? 0.0 : number;
        }
    }
}
