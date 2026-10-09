package com.ligalytics.ai;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import com.ligalytics.model.Match;
import com.ligalytics.model.Team;
import com.ligalytics.patterns.builder.AdvancedStats;
import com.ligalytics.patterns.builder.MatchAnalysis;
import com.ligalytics.service.SeasonUtil;
import com.ligalytics.service.StandingsHeap;
import com.ligalytics.service.StandingsHeap.Standing;

/**
 * Reproduce la liga partido a partido, en orden cronológico, y devuelve en
 * cada momento el estado "conocido" de un par de equipos.
 *
 * <p>Variables que calcula (todas con partidos <b>anteriores</b> al descrito, sin
 * fuga del futuro):</p>
 * <ul>
 *   <li>Corto plazo: promedios de goles, córneres y tarjetas de los últimos 10
 *       partidos, forma de los últimos 5, posición en la tabla (heap).</li>
 *   <li>Largo plazo: <b>Elo</b> y promedios de goles con memoria larga (media
 *       móvil exponencial), que evitan que un par de resultados raros
 *       desvirtúen la fuerza real de un equipo.</li>
 *   <li>Local/visitante: goles del local jugando en casa y del visitante jugando
 *       fuera (últimos 10 partidos en esa condición).</li>
 *   <li>Tiros a puerta a favor y en contra (últimos 10) y días de descanso.</li>
 * </ul>
 *
 * <p>Los promedios se "encogen" hacia la media de la liga cuando hay pocos datos.</p>
 */
public final class TeamFormTracker {

    static final int WINDOW = 10;
    static final int FORM_LENGTH = 5;
    private static final double SHRINK = 3.0;

    private static final double DEFAULT_GOALS = 1.3;
    private static final double DEFAULT_MATCH_CORNERS = 10.0;
    private static final double DEFAULT_YELLOW = 2.6;
    private static final double DEFAULT_RED = 0.1;
    private static final double DEFAULT_SOT = 4.2;

    private static final double INITIAL_ELO = 1500.0;
    private static final double ELO_K = 20.0;
    private static final double ELO_HOME_ADVANTAGE = 60.0;
    private static final double ELO_SEASON_REGRESSION = 0.2;
    private static final double LONG_TERM_ALPHA = 0.03;
    private static final double DEFAULT_REST_DAYS = 7.0;
    private static final double MAX_REST_DAYS = 14.0;

    private final Map<Long, TeamState> states = new HashMap<>();
    private final Map<Integer, Map<Long, SeasonRow>> seasonTables = new HashMap<>();
    private final League league = new League();
    /** Ultimos enfrentamientos directos por pareja de equipos: goles totales de cada partido (hasta 5). */
    private final Map<String, Deque<Integer>> headToHead = new HashMap<>();
    private static final int H2H_WINDOW = 5;

    /**
     * Estado de la liga justo antes de {@code date} para el enfrentamiento
     * indicado.
     */
    public MatchAnalysis analysisFor(Team home, Team away, LocalDateTime date) {
        int seasonYear = SeasonUtil.startYear(date);
        TeamState homeState = states.getOrDefault(home.getId(), TeamState.EMPTY);
        TeamState awayState = states.getOrDefault(away.getId(), TeamState.EMPTY);
        Map<Long, Integer> positions = positions(seasonYear);

        double goalPrior = league.goalsPerTeam();
        double cornerPrior = league.cornersPerMatch();
        double yellowPrior = league.yellowPerTeam();
        double redPrior = league.redPerTeam();
        double sotPrior = league.sotPerTeam();
        double xgPrior = league.xgPerTeam(goalPrior);
        boolean xgAvailable = homeState.countXg() >= 3 && awayState.countXg() >= 3;

        return MatchAnalysis.builder()
                .teams(home.getName(), away.getName())
                .matchDate(date)
                .season(SeasonUtil.label(seasonYear))
                .expectedGoals(homeState.xgAverage(seasonYear), awayState.xgAverage(seasonYear))
                .recentForm(homeState.form(), awayState.form())
                .leaguePositions(position(positions, home.getId(), seasonYear),
                        position(positions, away.getId(), seasonYear))
                .averageGoals(homeState.average(Played::goalsFor, goalPrior),
                        homeState.average(Played::goalsAgainst, goalPrior),
                        awayState.average(Played::goalsFor, goalPrior),
                        awayState.average(Played::goalsAgainst, goalPrior))
                .leagueGoalAverages(league.homeGoalsPerMatch(), league.awayGoalsPerMatch())
                .averageCorners(homeState.averageNullable(Played::corners, cornerPrior),
                        awayState.averageNullable(Played::corners, cornerPrior))
                .averageYellowCards(homeState.averageNullable(Played::yellow, yellowPrior),
                        awayState.averageNullable(Played::yellow, yellowPrior))
                .averageRedCards(homeState.averageNullable(Played::red, redPrior),
                        awayState.averageNullable(Played::red, redPrior))
                .marketValues(home.getMarketValue(), away.getMarketValue())
                .advanced(AdvancedStats.HOME_ELO, homeState.eloAt(seasonYear))
                .advanced(AdvancedStats.AWAY_ELO, awayState.eloAt(seasonYear))
                .advanced(AdvancedStats.HOME_LONG_GF, homeState.longFor(goalPrior))
                .advanced(AdvancedStats.HOME_LONG_GA, homeState.longAgainst(goalPrior))
                .advanced(AdvancedStats.AWAY_LONG_GF, awayState.longFor(goalPrior))
                .advanced(AdvancedStats.AWAY_LONG_GA, awayState.longAgainst(goalPrior))
                .advanced(AdvancedStats.HOME_VENUE_GF,
                        shrink(homeState.homeWindow, 0, league.homeGoalsPerMatch()))
                .advanced(AdvancedStats.HOME_VENUE_GA,
                        shrink(homeState.homeWindow, 1, league.awayGoalsPerMatch()))
                .advanced(AdvancedStats.AWAY_VENUE_GF,
                        shrink(awayState.awayWindow, 0, league.awayGoalsPerMatch()))
                .advanced(AdvancedStats.AWAY_VENUE_GA,
                        shrink(awayState.awayWindow, 1, league.homeGoalsPerMatch()))
                .advanced(AdvancedStats.HOME_SOT_FOR, homeState.averageNullable(Played::sotFor, sotPrior))
                .advanced(AdvancedStats.HOME_SOT_AGAINST, homeState.averageNullable(Played::sotAgainst, sotPrior))
                .advanced(AdvancedStats.AWAY_SOT_FOR, awayState.averageNullable(Played::sotFor, sotPrior))
                .advanced(AdvancedStats.AWAY_SOT_AGAINST, awayState.averageNullable(Played::sotAgainst, sotPrior))
                .advanced(AdvancedStats.HOME_XG_FOR, homeState.averageNullable(Played::xgFor, xgPrior))
                .advanced(AdvancedStats.HOME_XG_AGAINST, homeState.averageNullable(Played::xgAgainst, xgPrior))
                .advanced(AdvancedStats.AWAY_XG_FOR, awayState.averageNullable(Played::xgFor, xgPrior))
                .advanced(AdvancedStats.AWAY_XG_AGAINST, awayState.averageNullable(Played::xgAgainst, xgPrior))
                .advanced(AdvancedStats.XG_AVAILABLE, xgAvailable ? 1.0 : 0.0)
                .advanced(AdvancedStats.HOME_REST_DAYS, homeState.restDays(date))
                .advanced(AdvancedStats.H2H_MATCHES, h2hCount(home.getId(), away.getId()))
                .advanced(AdvancedStats.H2H_AVG_GOALS, h2hAverage(home.getId(), away.getId()))
                .advanced(AdvancedStats.AWAY_REST_DAYS, awayState.restDays(date))
                .build();
    }

    /**
     * Registra un partido ya jugado. Debe llamarse en orden cronológico. Los
     * partidos sin marcador se ignoran.
     */
    public void record(Match match) {
        Integer homeGoals = match.getFullTimeHomeGoals();
        Integer awayGoals = match.getFullTimeAwayGoals();
        if (homeGoals == null || awayGoals == null || match.getMatchDate() == null
                || match.getHomeTeam() == null || match.getAwayTeam() == null) {
            return;
        }
        int seasonYear = SeasonUtil.startYear(match.getMatchDate());

        Integer corners = match.getCorners();
        Double homeYellow = ownCards(match.getHomeYellowCards(), match.getYellowCards());
        Double awayYellow = ownCards(match.getAwayYellowCards(), match.getYellowCards());
        Double homeRed = ownCards(match.getHomeRedCards(), match.getRedCards());
        Double awayRed = ownCards(match.getAwayRedCards(), match.getRedCards());

        Deque<Integer> pair = headToHead.computeIfAbsent(pairKey(match.getHomeTeam().getId(), match.getAwayTeam().getId()),
                key -> new ArrayDeque<>());
        pair.addLast(homeGoals + awayGoals);
        if (pair.size() > H2H_WINDOW) {
            pair.removeFirst();
        }
        TeamState home = state(match.getHomeTeam().getId());
        TeamState away = state(match.getAwayTeam().getId());
        home.startSeason(seasonYear);
        away.startSeason(seasonYear);
        updateElo(home, away, homeGoals, awayGoals);

        double goalPrior = league.goalsPerTeam();
        Played homePlayed = new Played(homeGoals, awayGoals, corners, homeYellow, homeRed,
                match.getHomeShotsOnTarget(), match.getAwayShotsOnTarget(), match.getHomeXg(), match.getAwayXg());
        Played awayPlayed = new Played(awayGoals, homeGoals, corners, awayYellow, awayRed,
                match.getAwayShotsOnTarget(), match.getHomeShotsOnTarget(), match.getAwayXg(), match.getHomeXg());
        home.add(homePlayed, resultChar(homeGoals, awayGoals), seasonYear, match.getHomeXg(), true,
                match.getMatchDate(), goalPrior);
        away.add(awayPlayed, resultChar(awayGoals, homeGoals), seasonYear, match.getAwayXg(), false,
                match.getMatchDate(), goalPrior);

        Map<Long, SeasonRow> table = seasonTables.computeIfAbsent(seasonYear, key -> new HashMap<>());
        table.computeIfAbsent(match.getHomeTeam().getId(), key -> new SeasonRow(match.getHomeTeam().getName()))
                .add(homeGoals, awayGoals);
        table.computeIfAbsent(match.getAwayTeam().getId(), key -> new SeasonRow(match.getAwayTeam().getName()))
                .add(awayGoals, homeGoals);

        league.add(homeGoals, awayGoals, corners, homeYellow, awayYellow, homeRed, awayRed,
                match.getHomeShotsOnTarget(), match.getAwayShotsOnTarget(), match.getHomeXg(), match.getAwayXg());
    }

    /** Número de partidos registrados con marcador. */
    public int matchesRecorded() {
        return league.matches;
    }

    private static String pairKey(Long a, Long b) {
        return Math.min(a, b) + "-" + Math.max(a, b);
    }

    private Double h2hCount(Long home, Long away) {
        Deque<Integer> pair = headToHead.get(pairKey(home, away));
        return pair == null ? null : (double) pair.size();
    }

    private Double h2hAverage(Long home, Long away) {
        Deque<Integer> pair = headToHead.get(pairKey(home, away));
        if (pair == null || pair.isEmpty()) {
            return null;
        }
        return pair.stream().mapToInt(Integer::intValue).average().orElse(0.0);
    }

    /** Elo de un equipo en este momento (1500 si no se conoce). */
    public double eloOf(Long teamId) {
        TeamState state = states.get(teamId);
        return state == null ? INITIAL_ELO : state.elo;
    }

    private static void updateElo(TeamState home, TeamState away, int homeGoals, int awayGoals) {
        double expectedHome = 1.0 / (1.0 + Math.pow(10.0, (away.elo - home.elo - ELO_HOME_ADVANTAGE) / 400.0));
        double actualHome = homeGoals > awayGoals ? 1.0 : homeGoals == awayGoals ? 0.5 : 0.0;
        double margin = 1.0 + Math.log(1.0 + Math.abs(homeGoals - awayGoals));
        double delta = ELO_K * margin * (actualHome - expectedHome);
        home.elo += delta;
        away.elo -= delta;
    }

    private TeamState state(Long teamId) {
        return states.computeIfAbsent(teamId, key -> new TeamState());
    }

    private Map<Long, Integer> positions(int seasonYear) {
        Map<Long, SeasonRow> table = seasonTables.get(seasonYear);
        if (table == null || table.isEmpty()) {
            return Map.of();
        }
        List<Standing> standings = new ArrayList<>(table.size());
        for (Map.Entry<Long, SeasonRow> entry : table.entrySet()) {
            SeasonRow row = entry.getValue();
            standings.add(new Standing(entry.getKey(), row.name, row.points(), row.goalsFor - row.goalsAgainst,
                    row.goalsFor));
        }
        return StandingsHeap.positions(standings);
    }

    private int position(Map<Long, Integer> positions, Long teamId, int seasonYear) {
        Integer position = positions.get(teamId);
        if (position != null) {
            return position;
        }
        Map<Long, SeasonRow> table = seasonTables.get(seasonYear);
        return (table == null ? 0 : table.size()) + 1;
    }

    /** Media de la columna {@code index} de una ventana de {goles a favor, goles en contra}, encogida hacia el prior. */
    private static double shrink(Deque<int[]> window, int index, double prior) {
        double total = 0.0;
        for (int[] goals : window) {
            total += goals[index];
        }
        return (total + SHRINK * prior) / (window.size() + SHRINK);
    }

    private static Double ownCards(Integer own, Integer matchTotal) {
        if (own != null) {
            return own.doubleValue();
        }
        return matchTotal == null ? null : matchTotal / 2.0;
    }

    private static char resultChar(int scored, int conceded) {
        return scored > conceded ? 'W' : scored == conceded ? 'D' : 'L';
    }

    private record Played(int goalsFor, int goalsAgainst, Integer corners, Double yellow, Double red,
            Integer sotFor, Integer sotAgainst, Double xgFor, Double xgAgainst) {
    }

    /** Fila de la tabla de una temporada. */
    private static final class SeasonRow {
        private final String name;
        private int wins;
        private int draws;
        private int goalsFor;
        private int goalsAgainst;

        private SeasonRow(String name) {
            this.name = name;
        }

        private void add(int scored, int conceded) {
            goalsFor += scored;
            goalsAgainst += conceded;
            if (scored > conceded) {
                wins++;
            } else if (scored == conceded) {
                draws++;
            }
        }

        private int points() {
            return wins * 3 + draws;
        }
    }

    /** Estado acumulado de un equipo. */
    private static final class TeamState {

        static final TeamState EMPTY = new TeamState();

        private final Deque<Played> window = new ArrayDeque<>();
        private final Deque<Character> form = new ArrayDeque<>();
        private final Deque<int[]> homeWindow = new ArrayDeque<>();
        private final Deque<int[]> awayWindow = new ArrayDeque<>();
        private final Map<Integer, double[]> xgBySeason = new HashMap<>();
        private double elo = INITIAL_ELO;
        private Integer lastSeason;
        private LocalDateTime lastDate;
        private Double longFor;
        private Double longAgainst;

        /** Al empezar una temporada nueva, el Elo se acerca un poco a la media (plantillas que cambian). */
        private void startSeason(int seasonYear) {
            if (lastSeason != null && lastSeason < seasonYear) {
                elo = elo * (1.0 - ELO_SEASON_REGRESSION) + INITIAL_ELO * ELO_SEASON_REGRESSION;
            }
            lastSeason = seasonYear;
        }

        private double eloAt(int seasonYear) {
            if (lastSeason != null && lastSeason < seasonYear) {
                return elo * (1.0 - ELO_SEASON_REGRESSION) + INITIAL_ELO * ELO_SEASON_REGRESSION;
            }
            return elo;
        }

        private void add(Played played, char result, int seasonYear, Double xg, boolean atHome,
                LocalDateTime date, double goalPrior) {
            window.addLast(played);
            if (window.size() > WINDOW) {
                window.removeFirst();
            }
            form.addLast(result);
            if (form.size() > FORM_LENGTH) {
                form.removeFirst();
            }
            Deque<int[]> venue = atHome ? homeWindow : awayWindow;
            venue.addLast(new int[] { played.goalsFor(), played.goalsAgainst() });
            if (venue.size() > WINDOW) {
                venue.removeFirst();
            }
            if (xg != null) {
                double[] sum = xgBySeason.computeIfAbsent(seasonYear, key -> new double[2]);
                sum[0] += xg;
                sum[1]++;
            }
            double previousFor = longFor == null ? goalPrior : longFor;
            double previousAgainst = longAgainst == null ? goalPrior : longAgainst;
            longFor = previousFor + LONG_TERM_ALPHA * (played.goalsFor() - previousFor);
            longAgainst = previousAgainst + LONG_TERM_ALPHA * (played.goalsAgainst() - previousAgainst);
            lastDate = date;
        }

        private double longFor(double prior) {
            return longFor == null ? prior : longFor;
        }

        private double longAgainst(double prior) {
            return longAgainst == null ? prior : longAgainst;
        }

        private int countXg() {
            int count = 0;
            for (Played played : window) {
                if (played.xgFor() != null) {
                    count++;
                }
            }
            return count;
        }

        private double restDays(LocalDateTime date) {
            if (lastDate == null) {
                return DEFAULT_REST_DAYS;
            }
            double days = Duration.between(lastDate, date).toHours() / 24.0;
            return Math.max(0.0, Math.min(MAX_REST_DAYS, days));
        }

        private String form() {
            StringBuilder builder = new StringBuilder();
            form.forEach(builder::append);
            return builder.toString();
        }

        private Double xgAverage(int seasonYear) {
            double[] sum = xgBySeason.get(seasonYear);
            return sum == null || sum[1] == 0 ? null : sum[0] / sum[1];
        }

        private double average(ToDoubleFunction<Played> extractor, double prior) {
            double total = 0.0;
            for (Played played : window) {
                total += extractor.applyAsDouble(played);
            }
            return (total + SHRINK * prior) / (window.size() + SHRINK);
        }

        private double averageNullable(Function<Played, ? extends Number> extractor, double prior) {
            double total = 0.0;
            int count = 0;
            for (Played played : window) {
                Number value = extractor.apply(played);
                if (value != null) {
                    total += value.doubleValue();
                    count++;
                }
            }
            return (total + SHRINK * prior) / (count + SHRINK);
        }
    }

    /** Totales de la liga hasta el momento (a priori de los promedios). */
    private static final class League {
        private int matches;
        private long homeGoals;
        private long awayGoals;
        private double cornersSum;
        private int cornersCount;
        private double yellowSum;
        private int yellowCount;
        private double redSum;
        private int redCount;
        private double sotSum;
        private int sotCount;
        private double xgSum;
        private int xgCount;

        private void add(int home, int away, Integer corners, Double homeYellow, Double awayYellow, Double homeRed,
                Double awayRed, Integer homeSot, Integer awaySot, Double homeXg, Double awayXg) {
            matches++;
            for (Double xg : new Double[] { homeXg, awayXg }) {
                if (xg != null) {
                    xgSum += xg;
                    xgCount++;
                }
            }
            homeGoals += home;
            awayGoals += away;
            if (corners != null) {
                cornersSum += corners;
                cornersCount++;
            }
            for (Double yellow : new Double[] { homeYellow, awayYellow }) {
                if (yellow != null) {
                    yellowSum += yellow;
                    yellowCount++;
                }
            }
            for (Double red : new Double[] { homeRed, awayRed }) {
                if (red != null) {
                    redSum += red;
                    redCount++;
                }
            }
            for (Integer sot : new Integer[] { homeSot, awaySot }) {
                if (sot != null) {
                    sotSum += sot;
                    sotCount++;
                }
            }
        }

        private double homeGoalsPerMatch() {
            return matches == 0 ? DEFAULT_GOALS * 1.15 : (double) homeGoals / matches;
        }

        private double awayGoalsPerMatch() {
            return matches == 0 ? DEFAULT_GOALS * 0.85 : (double) awayGoals / matches;
        }

        private double goalsPerTeam() {
            return matches == 0 ? DEFAULT_GOALS : (homeGoals + awayGoals) / (2.0 * matches);
        }

        private double cornersPerMatch() {
            return cornersCount == 0 ? DEFAULT_MATCH_CORNERS : cornersSum / cornersCount;
        }

        private double yellowPerTeam() {
            return yellowCount == 0 ? DEFAULT_YELLOW : yellowSum / yellowCount;
        }

        private double redPerTeam() {
            return redCount == 0 ? DEFAULT_RED : redSum / redCount;
        }

        private double xgPerTeam(double fallback) {
            return xgCount == 0 ? fallback : xgSum / xgCount;
        }

        private double sotPerTeam() {
            return sotCount == 0 ? DEFAULT_SOT : sotSum / sotCount;
        }
    }
}
