package com.ligalytics.betting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligalytics.auth.AuthService;
import com.ligalytics.auth.AuthSessionRepository;
import com.ligalytics.auth.UserRepository;
import com.ligalytics.etl.BzzoiroClient;

/**
 * Camino completo del directo: el proveedor dice que el partido terminó → el alimentador publica FULL_TIME → se liquidan
 * las apuestas y se abona el saldo, una sola vez. Es el mismo camino que recorre un webhook firmado.
 */
@SpringBootTest
class LiveScoreFeederSettlementTest {

    private static final long EVENT = 9100L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @MockBean
    private BzzoiroClient client;
    @MockBean
    private BzzoiroOddsProvider oddsProvider;

    @Autowired
    private LiveScoreFeeder feeder;
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

    private Long userId;

    @BeforeEach
    void setUp() {
        bets.deleteAll();
        parlays.deleteAll();
        sessions.deleteAll();
        users.deleteAll();
        userId = authService.register("directo@example.com", "Directo", "clave-segura-1").user().getId();
        when(client.isConfigured()).thenReturn(true);
        when(oddsProvider.hasMatchInLiveWindow()).thenReturn(true);
    }

    private Bet bet(String selection, double odds) {
        Bet bet = new Bet();
        bet.setUserId(userId);
        bet.setEventId(EVENT);
        bet.setHomeTeam("Real Madrid");
        bet.setAwayTeam("FC Barcelona");
        bet.setKickoff(Instant.now().minus(3, ChronoUnit.HOURS));
        bet.setMarket(Market.WINNER);
        bet.setSelection(selection);
        bet.setOdds(odds);
        bet.setOddsSource("consenso");
        bet.setStake(10_000);
        return bets.save(bet);
    }

    private static JsonNode live(String status, String period, int minute, int home, int away) throws Exception {
        return MAPPER.readTree(String.format(
                "{\"id\":%d,\"home_team\":\"Real Madrid\",\"away_team\":\"FC Barcelona\",\"status\":\"%s\",\"period\":\"%s\","
                        + "\"current_minute\":%d,\"home_score\":%d,\"away_score\":%d}", EVENT, status, period, minute, home, away));
    }

    private long balance() {
        return users.findById(userId).orElseThrow().getBalance();
    }

    @Test
    void theFinalWhistleSeenInTheLiveFeedSettlesTheBetsOnceAndCreditsTheBalance() throws Exception {
        Bet winner = bet("HOME", 2.0);
        Bet loser = bet("AWAY", 3.5);
        long before = balance();
        when(client.liveEvents()).thenReturn(
                List.of(live("inprogress", "2nd_half", 80, 1, 0)),
                List.of(live("finished", "FT", 90, 2, 1)),
                List.of(live("finished", "FT", 90, 2, 1)));

        feeder.poll(); // en juego: no liquida nada
        assertEquals(BetStatus.PENDING, bets.findById(winner.getId()).orElseThrow().getStatus());
        assertEquals(before, balance());

        feeder.poll(); // final 2-1
        assertEquals(BetStatus.WON, bets.findById(winner.getId()).orElseThrow().getStatus());
        assertEquals(BetStatus.LOST, bets.findById(loser.getId()).orElseThrow().getStatus());
        assertEquals(before + 20_000L, balance());

        feeder.poll(); // el proveedor sigue mostrando el partido terminado: no se paga otra vez
        assertEquals(before + 20_000L, balance());
    }
}
