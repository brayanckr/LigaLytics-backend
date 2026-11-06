package com.ligalytics.betting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligalytics.etl.BzzoiroClient;
import com.ligalytics.exception.ExternalServiceException;
import com.ligalytics.webhook.LiveMatchBroadcaster;
import com.ligalytics.webhook.MatchEventPayload;
import com.ligalytics.webhook.MatchEventReceived;

class LiveScoreFeederTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BzzoiroClient client;
    private BzzoiroOddsProvider oddsProvider;
    private ApplicationEventPublisher publisher;
    private LiveMatchBroadcaster broadcaster;
    private LiveScoreFeeder feeder;

    @BeforeEach
    void setUp() {
        client = mock(BzzoiroClient.class);
        oddsProvider = mock(BzzoiroOddsProvider.class);
        publisher = mock(ApplicationEventPublisher.class);
        broadcaster = mock(LiveMatchBroadcaster.class);
        when(client.isConfigured()).thenReturn(true);
        when(oddsProvider.hasMatchInLiveWindow()).thenReturn(true);
        feeder = new LiveScoreFeeder(client, oddsProvider, publisher, broadcaster);
    }

    /** Un partido tal como lo describe GET /events/live/ (campos del esquema documentado del proveedor). */
    private static JsonNode match(long id, String status, String period, int minute, int home, int away) {
        try {
            return MAPPER.readTree(String.format(
                    "{\"id\":%d,\"home_team\":\"Real Madrid\",\"away_team\":\"FC Barcelona\",\"status\":\"%s\",\"period\":%s,"
                            + "\"current_minute\":%d,\"home_score\":%d,\"away_score\":%d}",
                    id, status, period == null ? "null" : "\"" + period + "\"", minute, home, away));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static LiveScoreFeeder.Seen seen(JsonNode node) {
        return LiveScoreFeeder.snapshot(node);
    }

    private List<MatchEventPayload> published() {
        ArgumentCaptor<MatchEventReceived> captor = ArgumentCaptor.forClass(MatchEventReceived.class);
        verify(publisher, org.mockito.Mockito.atLeast(0)).publishEvent(captor.capture());
        return captor.getAllValues().stream().map(MatchEventReceived::payload).toList();
    }

    @Test
    void diffDetectsKickOffGoalHalfTimeAndFullTime() {
        JsonNode waiting = match(1, "notstarted", null, 0, 0, 0);
        JsonNode started = match(1, "inprogress", "1st_half", 1, 0, 0);
        JsonNode goal = match(1, "inprogress", "1st_half", 34, 1, 0);
        JsonNode halftime = match(1, "inprogress", "halftime", 45, 1, 0);
        JsonNode second = match(1, "inprogress", "2nd_half", 50, 1, 0);
        JsonNode late = match(1, "inprogress", "2nd_half", 88, 2, 1);
        JsonNode finished = match(1, "finished", "FT", 90, 2, 1);

        assertEquals("KICK_OFF", LiveScoreFeeder.diff(1, started, seen(waiting), seen(started)).get(0).type());
        MatchEventPayload g = LiveScoreFeeder.diff(1, goal, seen(started), seen(goal)).get(0);
        assertEquals("GOAL", g.type());
        assertEquals(1, g.homeScore());
        assertEquals(0, g.awayScore());
        assertEquals("HALF_TIME", LiveScoreFeeder.diff(1, halftime, seen(goal), seen(halftime)).get(0).type());
        assertEquals("UPDATE", LiveScoreFeeder.diff(1, second, seen(halftime), seen(second)).get(0).type());
        assertEquals("GOAL", LiveScoreFeeder.diff(1, late, seen(second), seen(late)).get(0).type());
        List<MatchEventPayload> end = LiveScoreFeeder.diff(1, finished, seen(late), seen(finished));
        assertEquals(1, end.size());
        MatchEventPayload ft = end.get(0);
        assertEquals("FULL_TIME", ft.type());
        assertEquals(2, ft.homeScore());
        assertEquals(1, ft.awayScore());
    }

    @Test
    void aLastMinuteGoalAndTheFinalWhistleInTheSamePollKeepBothEvents() {
        JsonNode before = match(1, "inprogress", "2nd_half", 89, 1, 1);
        JsonNode after = match(1, "finished", "FT", 90, 2, 1);

        List<MatchEventPayload> events = LiveScoreFeeder.diff(1, after, seen(before), seen(after));

        assertEquals(List.of("GOAL", "FULL_TIME"), events.stream().map(MatchEventPayload::type).toList());
    }

    @Test
    void aMatchSeenForTheFirstTimeInProgressPublishesItsStateWithoutInventingAGoal() {
        JsonNode running = match(1, "inprogress", "2nd_half", 60, 2, 0);
        JsonNode waiting = match(1, "notstarted", null, 0, 0, 0);

        List<MatchEventPayload> events = LiveScoreFeeder.diff(1, running, null, seen(running));

        assertEquals(List.of("UPDATE"), events.stream().map(MatchEventPayload::type).toList());
        assertTrue(LiveScoreFeeder.diff(1, waiting, null, seen(waiting)).isEmpty());
    }

    @Test
    void aWholeMatchFlowsThroughPollsAndTheFinalIsPublishedOnlyOnce() {
        when(client.liveEvents()).thenReturn(
                List.of(match(5, "notstarted", null, 0, 0, 0)),
                List.of(match(5, "inprogress", "1st_half", 1, 0, 0)),
                List.of(match(5, "inprogress", "1st_half", 30, 1, 0)),
                List.of(match(5, "finished", "FT", 90, 1, 0)),
                List.of(match(5, "finished", "FT", 90, 1, 0)));

        for (int i = 0; i < 5; i++) {
            feeder.poll();
        }

        assertEquals(List.of("KICK_OFF", "GOAL", "FULL_TIME"), published().stream().map(MatchEventPayload::type).toList());
        // Cada evento publicado se emite también al canal en vivo.
        verify(broadcaster, times(3)).broadcast(any());
        MatchEventPayload last = published().get(2);
        assertEquals("5", last.eventId());
        assertEquals(1, last.homeScore());
        assertEquals(0, last.awayScore());
        assertNull(last.corners(), "el directo no trae córners ni tarjetas: esas apuestas siguen para la liquidación normal");
    }

    @Test
    void doesNotQueryTheProviderWhenNoMatchCanBeInProgress() {
        when(oddsProvider.hasMatchInLiveWindow()).thenReturn(false);

        feeder.poll();

        verify(client, never()).liveEvents();
    }

    @Test
    void doesNotQueryTheProviderWithoutAnApiKey() {
        when(client.isConfigured()).thenReturn(false);

        feeder.poll();

        verify(client, never()).liveEvents();
    }

    @Test
    void keepsFollowingAMatchInProgressEvenAfterItsKickoffLeftTheWindow() {
        when(client.liveEvents()).thenReturn(List.of(match(8, "inprogress", "2nd_half", 80, 1, 0)));
        feeder.poll();
        when(oddsProvider.hasMatchInLiveWindow()).thenReturn(false);
        when(client.liveEvents()).thenReturn(List.of(match(8, "finished", "FT", 90, 1, 0)));

        feeder.poll();

        List<MatchEventPayload> events = published();
        assertEquals("FULL_TIME", events.get(events.size() - 1).type());
    }

    @Test
    void aFailingProviderDoesNotBreakThePoll() {
        when(client.liveEvents()).thenThrow(new ExternalServiceException("Bzzoiro no responde", new RuntimeException()));

        feeder.poll();

        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void anEventThatCannotBeProcessedStillReachesTheLiveChannelAndDoesNotStopTheRest() {
        when(client.liveEvents()).thenReturn(
                List.of(match(9, "inprogress", "1st_half", 10, 0, 0), match(10, "inprogress", "1st_half", 12, 0, 0)));
        doThrow(new IllegalArgumentException("marcador no válido")).when(publisher).publishEvent(any(Object.class));

        feeder.poll();

        verify(broadcaster, times(2)).broadcast(any());
    }
}
