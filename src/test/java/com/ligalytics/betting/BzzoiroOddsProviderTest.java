package com.ligalytics.betting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligalytics.etl.BzzoiroClient;
import com.ligalytics.exception.ExternalServiceException;

class BzzoiroOddsProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** Respuesta real de GET /events/{id}/odds/ del proveedor (la ruta rápida, ~0,5 s). */
    private static final String SUMMARY = "{\"event_id\":213633,\"odds\":{\"home_win\":2.2,\"draw\":3.25,\"away_win\":3.3,"
            + "\"over_15_goals\":1.3,\"over_25_goals\":2.0,\"over_35_goals\":3.4,\"under_15_goals\":3.5,"
            + "\"under_25_goals\":1.8,\"under_35_goals\":1.33,\"btts_yes\":1.75,\"btts_no\":2.0},"
            + "\"update_interval_seconds\":14400}";

    private static JsonNode summaryNode() throws Exception {
        return MAPPER.readTree(SUMMARY).path("odds");
    }

    private static List<JsonNode> cornerRows() throws Exception {
        return List.of(MAPPER.readTree("{\"market\":\"total_corners\",\"outcome\":\"over\",\"line\":9.5,\"decimal_odds\":1.85}"),
                MAPPER.readTree("{\"market\":\"total_corners\",\"outcome\":\"under\",\"line\":9.5,\"decimal_odds\":1.95}"),
                MAPPER.readTree("{\"market\":\"1x2\",\"outcome\":\"home\",\"decimal_odds\":2.1}"));
    }

    private static boolean has(List<OddsLine> lines, Market market, String selection, Double line, double odds) {
        return lines.stream().anyMatch(l -> l.sameAs(market, selection, line) && Math.abs(l.odds() - odds) < 1e-9);
    }

    @Test
    void theRealSummaryBecomesWinnerGoalsAndBothTeamsToScoreOdds() throws Exception {
        List<OddsLine> lines = BzzoiroOddsProvider.parseSummary(summaryNode());

        assertEquals(11, lines.size());
        assertTrue(has(lines, Market.WINNER, "HOME", null, 2.2));
        assertTrue(has(lines, Market.WINNER, "DRAW", null, 3.25));
        assertTrue(has(lines, Market.WINNER, "AWAY", null, 3.3));
        assertTrue(has(lines, Market.GOALS, "OVER", 2.5, 2.0));
        assertTrue(has(lines, Market.GOALS, "UNDER", 1.5, 3.5));
        assertTrue(has(lines, Market.GOALS, "UNDER", 3.5, 1.33));
        assertTrue(has(lines, Market.BTTS, "YES", null, 1.75));
        assertTrue(has(lines, Market.BTTS, "NO", null, 2.0));
        assertTrue(lines.stream().allMatch(OddsLine::isReal));
    }

    @Test
    void missingOrInvalidValuesAreSkipped() throws Exception {
        List<OddsLine> lines = BzzoiroOddsProvider
                .parseSummary(MAPPER.readTree("{\"home_win\":2.0,\"draw\":null,\"away_win\":0.9,\"btts_yes\":\"x\"}"));

        assertEquals(1, lines.size());
        assertTrue(has(lines, Market.WINNER, "HOME", null, 2.0));
        assertTrue(BzzoiroOddsProvider.parseSummary(MAPPER.readTree("{}")).isEmpty());
    }

    @Test
    void oddsReturnsTheFastMarketsImmediatelyWithoutWaitingForTheSlowCornersEndpoint() throws Exception {
        BzzoiroClient client = mock(BzzoiroClient.class);
        CountDownLatch slowCallStarted = new CountDownLatch(1);
        CountDownLatch releaseSlowCall = new CountDownLatch(1);
        when(client.eventOdds(7L)).thenReturn(summaryNode());
        when(client.oddsRows(7L)).thenAnswer(call -> {
            slowCallStarted.countDown();
            releaseSlowCall.await(10, TimeUnit.SECONDS); // el proveedor tardando decenas de segundos
            return cornerRows();
        });
        BzzoiroOddsProvider provider = new BzzoiroOddsProvider(client);

        long start = System.nanoTime();
        List<OddsLine> first = provider.odds(7L);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsedMs < 1500, "no debía esperar a la ruta lenta y tardó " + elapsedMs + " ms");
        assertEquals(11, first.size());
        assertFalse(first.stream().anyMatch(l -> l.market() == Market.CORNERS), "los córners aún no han llegado");
        assertTrue(slowCallStarted.await(3, TimeUnit.SECONDS), "la ruta lenta debía pedirse en segundo plano");

        // Mientras la ruta lenta sigue en curso, más consultas no la repiten.
        provider.odds(7L);
        provider.odds(7L);
        verify(client, times(1)).oddsRows(7L);

        releaseSlowCall.countDown();
        List<OddsLine> withCorners = first;
        for (int i = 0; i < 60 && withCorners.stream().noneMatch(l -> l.market() == Market.CORNERS); i++) {
            Thread.sleep(50);
            withCorners = provider.odds(7L);
        }
        assertTrue(has(withCorners, Market.CORNERS, "OVER", 9.5, 1.85));
        assertTrue(has(withCorners, Market.CORNERS, "UNDER", 9.5, 1.95));
        // Solo se quedan los córners de la ruta lenta: lo demás ya salió del resumen rápido, sin duplicados.
        assertEquals(1, withCorners.stream().filter(l -> l.sameAs(Market.WINNER, "HOME", null)).count());
    }

    @Test
    void aFailingProviderDoesNotThrowAndIsNotRememberedForThirtyMinutes() throws Exception {
        BzzoiroClient client = mock(BzzoiroClient.class);
        when(client.eventOdds(9L)).thenThrow(new ExternalServiceException("Bzzoiro no responde", new RuntimeException()));
        when(client.oddsRows(9L)).thenThrow(new ExternalServiceException("Bzzoiro no responde", new RuntimeException()));
        BzzoiroOddsProvider provider = new BzzoiroOddsProvider(client);

        assertTrue(provider.odds(9L).isEmpty());
        // Dentro del minuto no se vuelve a golpear al proveedor caído...
        assertTrue(provider.odds(9L).isEmpty());
        verify(client, times(1)).eventOdds(9L);
    }
}
