package com.ligalytics.betting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.ligalytics.auth.AuthService;
import com.ligalytics.auth.AuthSessionRepository;
import com.ligalytics.auth.User;
import com.ligalytics.auth.UserRepository;
import com.ligalytics.betting.BzzoiroOddsProvider.OddsEvent;
import com.ligalytics.etl.BzzoiroClient;
import com.ligalytics.model.Match;
import com.ligalytics.model.Team;
import com.ligalytics.repository.MatchRepository;
import com.ligalytics.repository.TeamRepository;
import com.ligalytics.repository.TeamStatsRepository;
import com.ligalytics.service.PredictionCache;

@SpringBootTest
class BettingFlowTest {

    @MockBean
    private BzzoiroOddsProvider oddsProvider;
    @MockBean
    private BzzoiroClient bzzoiroClient;

    @Autowired
    private BettingService service;
    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository users;
    @Autowired
    private AuthSessionRepository sessions;
    @Autowired
    private BetRepository bets;
    @Autowired
    private ParlayRepository parlays;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private MatchRepository matchRepository;
    @Autowired
    private TeamStatsRepository teamStatsRepository;
    @Autowired
    private PredictionCache predictionCache;

    private Long userId;
    private OddsEvent event;

    @BeforeEach
    void setUp() {
        bets.deleteAll();
        parlays.deleteAll();
        sessions.deleteAll();
        users.deleteAll();
        matchRepository.deleteAll();
        teamStatsRepository.deleteAll();
        teamRepository.deleteAll();
        predictionCache.clear();

        Team madrid = teamRepository.save(Team.builder().name("Real Madrid").marketValue(new BigDecimal("1200000000")).build());
        Team barcelona = teamRepository.save(Team.builder().name("Barcelona").marketValue(new BigDecimal("900000000")).build());
        matchRepository.save(Match.builder().matchDate(LocalDateTime.of(2026, 3, 1, 21, 0)).homeTeam(madrid)
                .awayTeam(barcelona).fullTimeHomeGoals(2).fullTimeAwayGoals(1).corners(11).yellowCards(4).build());

        event = new OddsEvent(777L, Instant.now().plus(1, ChronoUnit.DAYS), "Real Madrid", "Barcelona");
        when(oddsProvider.isConfigured()).thenReturn(true);
        when(oddsProvider.upcoming(anyInt())).thenReturn(List.of(event));
        when(oddsProvider.odds(anyLong())).thenReturn(List.of(
                new OddsLine(Market.WINNER, "HOME", null, 2.0, "consenso"),
                new OddsLine(Market.WINNER, "AWAY", null, 3.5, "consenso"),
                new OddsLine(Market.GOALS, "OVER", 2.5, 1.9, "consenso"),
                new OddsLine(Market.CORNERS, "OVER", 9.5, 1.8, "consenso")));

        userId = authService.register("demo@example.com", "Demo", "clave-segura-1").user().getId();
    }

    private long balance() {
        return users.findById(userId).orElseThrow().getBalance();
    }

    @Test
    void boardOffersRealOddsWithModelProbabilityAndDemoCardOdds() {
        List<BettingService.BoardMatch> board = service.board(7);

        assertEquals(1, board.size());
        BettingService.BoardMatch match = board.get(0);
        assertTrue(match.hasModel());
        assertTrue(match.offers().stream().anyMatch(o -> o.line().market() == Market.CARDS && "demo".equals(o.line().source())));
        BettingService.Offer winner = match.offers().stream()
                .filter(o -> o.line().sameAs(Market.WINNER, "HOME", null)).findFirst().orElseThrow();
        assertTrue(winner.modelProbability() > 0.0 && winner.edge() != null);
    }

    @Test
    void placingABetDeductsTheStakeAndFixesTheServerOdds() {
        Bet bet = service.placeBet(userId, 777L, Market.WINNER, "home", null, 10_000);

        assertEquals(90_000L, balance());
        assertEquals(2.0, bet.getOdds());
        assertEquals("consenso", bet.getOddsSource());
        assertEquals(BetStatus.PENDING, bet.getStatus());
    }

    @Test
    void invalidBetsAreRejectedWithoutTouchingTheBalance() {
        assertThrows(IllegalArgumentException.class, () -> service.placeBet(userId, 777L, Market.WINNER, "HOME", null, 500));
        assertThrows(IllegalArgumentException.class, () -> service.placeBet(userId, 777L, Market.WINNER, "HOME", null, 200_000));
        assertThrows(IllegalArgumentException.class, () -> service.placeBet(userId, 777L, Market.WINNER, "DRAW", null, 5_000));
        assertThrows(IllegalArgumentException.class, () -> service.placeBet(userId, 999L, Market.WINNER, "HOME", null, 5_000));
        assertEquals(100_000L, balance());

        // Un partido que ya empezó no admite apuestas.
        when(oddsProvider.upcoming(anyInt())).thenReturn(List.of(
                new OddsEvent(777L, Instant.now().minus(1, ChronoUnit.HOURS), "Real Madrid", "Barcelona")));
        assertThrows(IllegalArgumentException.class, () -> service.placeBet(userId, 777L, Market.WINNER, "HOME", null, 5_000));
    }

    @Test
    void aWinningBetPaysStakeTimesOddsWhenTheMatchIsSettled() {
        Bet bet = service.placeBet(userId, 777L, Market.WINNER, "HOME", null, 10_000);
        Bet corners = service.placeBet(userId, 777L, Market.CORNERS, "OVER", 9.5, 5_000);
        assertEquals(85_000L, balance());

        // El partido se juega (hace 3 horas) y termina 2-1 con 11 córneres.
        Instant played = Instant.now().minus(3, ChronoUnit.HOURS);
        for (Bet pending : List.of(bet, corners)) {
            pending.setKickoff(played);
            bets.save(pending);
        }
        Team madrid = teamRepository.findByNameIgnoreCase("Real Madrid").orElseThrow();
        Team barcelona = teamRepository.findByNameIgnoreCase("Barcelona").orElseThrow();
        matchRepository.save(Match.builder().matchDate(LocalDateTime.ofInstant(played, ZoneId.of("Europe/Madrid")))
                .homeTeam(madrid).awayTeam(barcelona).fullTimeHomeGoals(2).fullTimeAwayGoals(1).corners(11)
                .yellowCards(3).redCards(0).build());

        assertEquals(2, service.settlePending());

        assertEquals(BetStatus.WON, bets.findById(bet.getId()).orElseThrow().getStatus());
        // 85 000 + 10 000 x 2,0 + 5 000 x 1,8
        assertEquals(85_000L + 20_000L + 9_000L, balance());
        assertEquals(0, service.settlePending());
    }

    @Test
    void aLosingBetPaysNothingAndVoidReturnsTheStake() {
        Bet lost = service.placeBet(userId, 777L, Market.WINNER, "AWAY", null, 10_000);
        lost.setKickoff(Instant.now().minus(3, ChronoUnit.HOURS));
        bets.save(lost);
        Team madrid = teamRepository.findByNameIgnoreCase("Real Madrid").orElseThrow();
        Team barcelona = teamRepository.findByNameIgnoreCase("Barcelona").orElseThrow();
        matchRepository.save(Match.builder()
                .matchDate(LocalDateTime.ofInstant(lost.getKickoff(), ZoneId.of("Europe/Madrid")))
                .homeTeam(madrid).awayTeam(barcelona).fullTimeHomeGoals(1).fullTimeAwayGoals(0).build());

        // Una apuesta sin datos del partido tras 3 días se anula y se devuelve.
        Bet orphan = service.placeBet(userId, 777L, Market.GOALS, "OVER", 2.5, 4_000);
        orphan.setHomeTeam("Equipo Desconocido");
        orphan.setKickoff(Instant.now().minus(4, ChronoUnit.DAYS));
        bets.save(orphan);

        service.settlePending();

        assertEquals(BetStatus.LOST, bets.findById(lost.getId()).orElseThrow().getStatus());
        assertEquals(BetStatus.VOID, bets.findById(orphan.getId()).orElseThrow().getStatus());
        assertEquals(100_000L - 10_000L, balance());
    }

    private OddsEvent secondEvent() {
        OddsEvent second = new OddsEvent(778L, Instant.now().plus(2, ChronoUnit.DAYS), "Real Madrid", "Barcelona");
        when(oddsProvider.upcoming(anyInt())).thenReturn(List.of(event, second));
        return second;
    }

    @Test
    void aParlayMultipliesTheOddsAndDeductsTheStake() {
        secondEvent();

        Parlay parlay = service.placeParlay(userId, 10_000, List.of(
                new BettingService.LegRequest(777L, Market.WINNER, "home", null),
                new BettingService.LegRequest(778L, Market.CORNERS, "OVER", 9.5)));

        assertEquals(3.6, parlay.getTotalOdds(), 1e-9);
        assertEquals(2, parlay.getLegs().size());
        assertEquals(90_000L, balance());
        assertEquals(BetStatus.PENDING, parlay.getStatus());
    }

    @Test
    void invalidParlaysAreRejectedWithoutTouchingTheBalance() {
        secondEvent();
        BettingService.LegRequest winner = new BettingService.LegRequest(777L, Market.WINNER, "HOME", null);
        BettingService.LegRequest corners = new BettingService.LegRequest(778L, Market.CORNERS, "OVER", 9.5);

        // Una sola selección, dos del mismo partido, una cuota inexistente, importe bajo y saldo insuficiente.
        assertThrows(IllegalArgumentException.class, () -> service.placeParlay(userId, 5_000, List.of(winner)));
        assertThrows(IllegalArgumentException.class, () -> service.placeParlay(userId, 5_000, List.of(winner,
                new BettingService.LegRequest(777L, Market.GOALS, "OVER", 2.5))));
        assertThrows(IllegalArgumentException.class, () -> service.placeParlay(userId, 5_000, List.of(winner,
                new BettingService.LegRequest(778L, Market.WINNER, "DRAW", null))));
        assertThrows(IllegalArgumentException.class, () -> service.placeParlay(userId, 500, List.of(winner, corners)));
        assertThrows(IllegalArgumentException.class, () -> service.placeParlay(userId, 200_000, List.of(winner, corners)));
        assertEquals(100_000L, balance());
        assertEquals(0, service.parlaysOf(userId).size());
    }

    @Test
    void contradictoryOrRedundantSelectionsOfTheSameMatchCannotBeCombined() {
        secondEvent();
        BettingService.LegRequest other = new BettingService.LegRequest(778L, Market.CORNERS, "OVER", 9.5);
        List<List<BettingService.LegRequest>> sameMatchPairs = List.of(
                // Ganador y empate del mismo partido.
                List.of(new BettingService.LegRequest(777L, Market.WINNER, "HOME", null),
                        new BettingService.LegRequest(777L, Market.WINNER, "DRAW", null)),
                // Más y menos de 2,5 goles.
                List.of(new BettingService.LegRequest(777L, Market.GOALS, "OVER", 2.5),
                        new BettingService.LegRequest(777L, Market.GOALS, "UNDER", 2.5)),
                // La misma selección repetida.
                List.of(new BettingService.LegRequest(777L, Market.WINNER, "HOME", null),
                        new BettingService.LegRequest(777L, Market.WINNER, "HOME", null)),
                // Dos mercados distintos del mismo partido (correlacionados) tampoco se combinan.
                List.of(new BettingService.LegRequest(777L, Market.WINNER, "HOME", null),
                        new BettingService.LegRequest(777L, Market.GOALS, "OVER", 2.5)));

        for (List<BettingService.LegRequest> pair : sameMatchPairs) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> service.placeParlay(userId, 5_000, pair));
            assertEquals("No se pueden combinar dos selecciones del mismo partido", error.getMessage());
            // Tampoco con una tercera selección válida de otro partido.
            List<BettingService.LegRequest> withThird = new java.util.ArrayList<>(pair);
            withThird.add(other);
            assertThrows(IllegalArgumentException.class, () -> service.placeParlay(userId, 5_000, withThird));
        }
        assertEquals(100_000L, balance());
        assertEquals(0, service.parlaysOf(userId).size());
    }

    @Test
    void aParlayPaysOnlyWhenEveryLegWins() {
        secondEvent();
        Parlay winning = service.placeParlay(userId, 10_000, List.of(
                new BettingService.LegRequest(777L, Market.WINNER, "HOME", null),
                new BettingService.LegRequest(778L, Market.CORNERS, "OVER", 9.5)));
        Parlay losing = service.placeParlay(userId, 5_000, List.of(
                new BettingService.LegRequest(777L, Market.WINNER, "AWAY", null),
                new BettingService.LegRequest(778L, Market.CORNERS, "OVER", 9.5)));
        assertEquals(85_000L, balance());

        // El partido se jugó hace 3 horas y terminó 2-1 con 11 córneres.
        Instant played = Instant.now().minus(3, ChronoUnit.HOURS);
        for (Parlay parlay : List.of(winning, losing)) {
            parlay.getLegs().forEach(leg -> leg.setKickoff(played));
            parlays.save(parlay);
        }
        Team madrid = teamRepository.findByNameIgnoreCase("Real Madrid").orElseThrow();
        Team barcelona = teamRepository.findByNameIgnoreCase("Barcelona").orElseThrow();
        matchRepository.save(Match.builder().matchDate(LocalDateTime.ofInstant(played, ZoneId.of("Europe/Madrid")))
                .homeTeam(madrid).awayTeam(barcelona).fullTimeHomeGoals(2).fullTimeAwayGoals(1).corners(11)
                .yellowCards(3).redCards(0).build());

        assertEquals(2, service.settlePending());

        assertEquals(BetStatus.WON, parlays.findById(winning.getId()).orElseThrow().getStatus());
        assertEquals(BetStatus.LOST, parlays.findById(losing.getId()).orElseThrow().getStatus());
        // 85 000 + 10 000 x 3,6
        assertEquals(85_000L + 36_000L, balance());
        assertEquals(0, service.settlePending());
    }

    @Test
    void resetRestoresTheDemoBalanceAndClearsTheHistory() {
        service.placeBet(userId, 777L, Market.WINNER, "HOME", null, 10_000);

        service.resetWallet(userId);

        assertEquals(User.INITIAL_BALANCE, balance());
        assertEquals(0, service.betsOf(userId).size());
    }
}
