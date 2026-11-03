package com.ligalytics.betting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;

import com.ligalytics.auth.AuthService;
import com.ligalytics.auth.AuthSessionRepository;
import com.ligalytics.auth.UserRepository;
import com.ligalytics.etl.BzzoiroClient;
import com.ligalytics.webhook.MatchEventPayload;
import com.ligalytics.webhook.MatchEventReceived;

/** Un evento FULL_TIME del webhook liquida las apuestas y combinadas pendientes de ese partido, una sola vez. */
@SpringBootTest
class MatchEventBetSettlerTest {

    private static final long EVENT = 9001L;
    private static final long OTHER_EVENT = 9002L;

    @MockBean
    private BzzoiroOddsProvider oddsProvider;
    @MockBean
    private BzzoiroClient bzzoiroClient;

    @Autowired
    private ApplicationEventPublisher publisher;
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
        userId = authService.register("webhook@example.com", "Webhook", "clave-segura-1").user().getId();
    }

    private Bet bet(long eventId, Market market, String selection, Double line, double odds, Instant kickoff) {
        Bet bet = new Bet();
        bet.setUserId(userId);
        bet.setEventId(eventId);
        bet.setHomeTeam("Real Madrid");
        bet.setAwayTeam("Barcelona");
        bet.setKickoff(kickoff);
        bet.setMarket(market);
        bet.setSelection(selection);
        bet.setLine(line);
        bet.setOdds(odds);
        bet.setOddsSource("consenso");
        bet.setStake(10_000);
        return bets.save(bet);
    }

    private Parlay parlay(Instant kickoff, Object... legSpecs) {
        Parlay parlay = new Parlay();
        parlay.setUserId(userId);
        parlay.setStake(10_000);
        double total = 1.0;
        for (int i = 0; i < legSpecs.length; i += 3) {
            ParlayLeg leg = new ParlayLeg();
            leg.setParlay(parlay);
            leg.setEventId((Long) legSpecs[i]);
            leg.setHomeTeam("Real Madrid");
            leg.setAwayTeam("Barcelona");
            leg.setKickoff(kickoff);
            leg.setMarket((Market) legSpecs[i + 1]);
            leg.setSelection((String) legSpecs[i + 2]);
            leg.setOdds(2.0);
            leg.setOddsSource("consenso");
            parlay.getLegs().add(leg);
            total *= 2.0;
        }
        parlay.setTotalOdds(total);
        return parlays.save(parlay);
    }

    private static MatchEventPayload fullTime(String eventId, Integer home, Integer away, Integer corners, Integer cards) {
        return new MatchEventPayload(eventId, "FULL_TIME", "Real Madrid", "Barcelona", 90, home, away, corners, cards, Instant.now());
    }

    private void publish(MatchEventPayload payload) {
        publisher.publishEvent(new MatchEventReceived(payload));
    }

    private long balance() {
        return users.findById(userId).orElseThrow().getBalance();
    }

    private Instant started() {
        return Instant.now().minus(3, ChronoUnit.HOURS);
    }

    @Test
    void fullTimeSettlesWinnersAndLosersAndCreditsTheBalance() {
        Bet win = bet(EVENT, Market.WINNER, "HOME", null, 2.0, started());
        Bet lose = bet(EVENT, Market.WINNER, "AWAY", null, 3.5, started());
        Bet goals = bet(EVENT, Market.GOALS, "OVER", 2.5, 1.9, started());
        long before = balance();

        publish(fullTime("9001", 2, 1, null, null));

        assertEquals(BetStatus.WON, bets.findById(win.getId()).orElseThrow().getStatus());
        assertEquals(20_000L, bets.findById(win.getId()).orElseThrow().getPayout());
        assertEquals(BetStatus.LOST, bets.findById(lose.getId()).orElseThrow().getStatus());
        assertEquals(BetStatus.WON, bets.findById(goals.getId()).orElseThrow().getStatus());
        assertEquals(before + 20_000L + 19_000L, balance());
    }

    @Test
    void repeatingTheSameEventDoesNotPayTwice() {
        bet(EVENT, Market.WINNER, "HOME", null, 2.0, started());
        long before = balance();

        publish(fullTime("9001", 2, 1, null, null));
        long afterFirst = balance();
        publish(fullTime("9001", 2, 1, null, null));

        assertEquals(before + 20_000L, afterFirst);
        assertEquals(afterFirst, balance());
    }

    @Test
    void doesNotTouchBetsOfOtherMatchesOrMatchesThatHaveNotStarted() {
        Bet otherMatch = bet(OTHER_EVENT, Market.WINNER, "HOME", null, 2.0, started());
        Bet future = bet(EVENT, Market.WINNER, "HOME", null, 2.0, Instant.now().plus(1, ChronoUnit.DAYS));

        publish(fullTime("9001", 2, 1, null, null));

        assertEquals(BetStatus.PENDING, bets.findById(otherMatch.getId()).orElseThrow().getStatus());
        assertEquals(BetStatus.PENDING, bets.findById(future.getId()).orElseThrow().getStatus());
    }

    @Test
    void cornersAndCardsStayPendingUnlessTheEventCarriesTheTotals() {
        Bet corners = bet(EVENT, Market.CORNERS, "OVER", 9.5, 1.8, started());
        Bet cards = bet(EVENT, Market.CARDS, "UNDER", 4.5, 1.9, started());

        publish(fullTime("9001", 1, 1, null, null));
        assertEquals(BetStatus.PENDING, bets.findById(corners.getId()).orElseThrow().getStatus());
        assertEquals(BetStatus.PENDING, bets.findById(cards.getId()).orElseThrow().getStatus());

        publish(fullTime("9001", 1, 1, 11, 3));
        assertEquals(BetStatus.WON, bets.findById(corners.getId()).orElseThrow().getStatus());
        assertEquals(BetStatus.WON, bets.findById(cards.getId()).orElseThrow().getStatus());
    }

    @Test
    void settlesAParlayWhenAllItsMatchesAreDecidedAndLosesItAsSoonAsOneLegFails() {
        Parlay winning = parlay(started(), EVENT, Market.WINNER, "HOME", OTHER_EVENT, Market.WINNER, "HOME");
        Parlay doomed = parlay(started(), EVENT, Market.WINNER, "AWAY", OTHER_EVENT, Market.WINNER, "HOME");
        long before = balance();

        publish(fullTime("9001", 2, 1, null, null));
        // Una selección perdida basta para perder la combinada; la otra aún espera su partido.
        assertEquals(BetStatus.LOST, parlays.findById(doomed.getId()).orElseThrow().getStatus());
        assertEquals(BetStatus.PENDING, parlays.findById(winning.getId()).orElseThrow().getStatus());
        assertEquals(before, balance());

        publish(fullTime("9002", 3, 0, null, null));
        Parlay settled = parlays.findById(winning.getId()).orElseThrow();
        assertEquals(BetStatus.WON, settled.getStatus());
        assertEquals(40_000L, settled.getPayout());
        assertEquals(before + 40_000L, balance());
    }

    @Test
    void otherEventTypesDoNotSettleAnything() {
        Bet bet = bet(EVENT, Market.WINNER, "HOME", null, 2.0, started());

        publish(new MatchEventPayload("9001", "GOAL", "Real Madrid", "Barcelona", 34, 1, 0, null, null, Instant.now()));

        assertEquals(BetStatus.PENDING, bets.findById(bet.getId()).orElseThrow().getStatus());
    }

    @Test
    void fullTimeWithoutAValidScoreIsRejectedAndNothingIsSettled() {
        Bet bet = bet(EVENT, Market.WINNER, "HOME", null, 2.0, started());

        assertThrows(IllegalArgumentException.class, () -> publish(fullTime("9001", null, 1, null, null)));
        assertThrows(IllegalArgumentException.class, () -> publish(fullTime("9001", 2, -1, null, null)));
        assertThrows(IllegalArgumentException.class, () -> publish(fullTime("9001", 999, 1, null, null)));

        assertEquals(BetStatus.PENDING, bets.findById(bet.getId()).orElseThrow().getStatus());
    }

    @Test
    void aNonNumericEventIdIsIgnored() {
        Bet bet = bet(EVENT, Market.WINNER, "HOME", null, 2.0, started());

        publish(fullTime("abc-123", 2, 1, null, null));

        assertEquals(BetStatus.PENDING, bets.findById(bet.getId()).orElseThrow().getStatus());
    }
}
