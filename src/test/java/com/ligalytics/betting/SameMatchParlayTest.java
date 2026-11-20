package com.ligalytics.betting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ligalytics.service.dto.PredictionResponseDto;

/** Reglas de las casas de apuestas para combinar selecciones de un mismo partido («crear apuesta»). */
class SameMatchParlayTest {

    private static boolean conflict(Market a, String sa, Double la, Market b, String sb, Double lb) {
        boolean ab = ScoreMarkets.conflicts(a, sa, la, b, sb, lb);
        assertEquals(ab, ScoreMarkets.conflicts(b, sb, lb, a, sa, la), "la regla debe ser simétrica");
        return ab;
    }

    @Test
    void sameMarketSelectionsAlwaysConflict() {
        assertTrue(conflict(Market.BTTS, "YES", null, Market.BTTS, "NO", null));
        assertTrue(conflict(Market.GOALS, "OVER", 2.5, Market.GOALS, "UNDER", 2.5));
        assertTrue(conflict(Market.GOALS, "OVER", 1.5, Market.GOALS, "OVER", 2.5));
        assertTrue(conflict(Market.WINNER, "HOME", null, Market.WINNER, "DRAW", null));
        assertTrue(conflict(Market.CORNERS, "OVER", 9.5, Market.CORNERS, "UNDER", 10.5));
    }

    @Test
    void impossibleOrImpliedCombinationsConflict() {
        // Ambos marcan «sí» exige al menos 2 goles.
        assertTrue(conflict(Market.BTTS, "YES", null, Market.GOALS, "UNDER", 1.5), "imposible");
        assertTrue(conflict(Market.BTTS, "YES", null, Market.GOALS, "OVER", 1.5), "implicada");
        assertTrue(conflict(Market.BTTS, "YES", null, Market.GOALS, "OVER", 0.5), "implicada");
        // Como mucho 1 gol implica que no marcan los dos.
        assertTrue(conflict(Market.BTTS, "NO", null, Market.GOALS, "UNDER", 1.5), "implicada");
        // Ganar sin goles es imposible.
        assertTrue(conflict(Market.WINNER, "HOME", null, Market.GOALS, "UNDER", 0.5), "imposible");
    }

    @Test
    void correlatedButPossibleCombinationsAreAllowed() {
        assertFalse(conflict(Market.BTTS, "YES", null, Market.GOALS, "OVER", 2.5));
        assertFalse(conflict(Market.BTTS, "YES", null, Market.GOALS, "UNDER", 2.5));
        assertFalse(conflict(Market.BTTS, "NO", null, Market.GOALS, "OVER", 2.5));
        assertFalse(conflict(Market.BTTS, "NO", null, Market.GOALS, "UNDER", 2.5));
        assertFalse(conflict(Market.WINNER, "HOME", null, Market.GOALS, "OVER", 2.5));
        assertFalse(conflict(Market.WINNER, "DRAW", null, Market.BTTS, "YES", null));
        assertFalse(conflict(Market.WINNER, "AWAY", null, Market.BTTS, "NO", null));
        // Córneres y tarjetas no dependen del marcador.
        assertFalse(conflict(Market.CORNERS, "OVER", 9.5, Market.BTTS, "YES", null));
        assertFalse(conflict(Market.CARDS, "OVER", 4.5, Market.GOALS, "OVER", 2.5));
        assertFalse(conflict(Market.CORNERS, "OVER", 9.5, Market.CARDS, "OVER", 4.5));
    }

    private static MarketModel model() {
        PredictionResponseDto prediction = new PredictionResponseDto(1L, "Real Madrid", 2L, "Villarreal", "HOME_WIN",
                0.5, 0.25, 0.25, 1.5, 1.2, "OVER_2_5", "2-1", 9.8, "OVER_9_5", 5.0, "OVER_4_5", 2.0, 2.0, 0.1, 0.1,
                Map.of(), "2026/2027", "football-charts", 0.5, 0.25, 0.25, List.of(), 0.6, 0.5, List.of(), false,
                Instant.now());
        return new MarketModel(prediction);
    }

    @Test
    void correlatedSelectionsPayLessThanTheProductAndOpposedOnesMore() {
        MarketModel model = model();
        // Ambos marcan y más de 2,5 goles se favorecen: la cuota conjunta baja (factor > 1).
        double together = model.correlationFactor(List.of(new MarketModel.Leg(Market.BTTS, "YES", null),
                new MarketModel.Leg(Market.GOALS, "OVER", 2.5)));
        assertTrue(together > 1.2, "factor " + together);
        // Ambos marcan y menos de 2,5 se oponen: la cuota conjunta sube (factor < 1).
        double opposed = model.correlationFactor(List.of(new MarketModel.Leg(Market.BTTS, "YES", null),
                new MarketModel.Leg(Market.GOALS, "UNDER", 2.5)));
        assertTrue(opposed < 1.0, "factor " + opposed);
        // Córneres o una sola selección: independientes.
        assertEquals(1.0, model.correlationFactor(List.of(new MarketModel.Leg(Market.BTTS, "YES", null),
                new MarketModel.Leg(Market.CORNERS, "OVER", 9.5))), 1e-12);
        assertEquals(1.0, model.correlationFactor(List.of(new MarketModel.Leg(Market.BTTS, "YES", null))), 1e-12);
    }

    @Test
    void jointProbabilityIsConsistentWithTheCorrelationFactor() {
        MarketModel model = model();
        List<MarketModel.Leg> legs = List.of(new MarketModel.Leg(Market.BTTS, "YES", null),
                new MarketModel.Leg(Market.GOALS, "OVER", 2.5));
        double joint = model.jointProbability(legs).orElseThrow();
        double pBtts = model.probability(Market.BTTS, "YES", null).orElseThrow();
        double pOver = model.probability(Market.GOALS, "OVER", 2.5).orElseThrow();
        assertEquals(pBtts * pOver * model.correlationFactor(legs), joint, 1e-9);
        // La conjunta no puede superar a ninguna de las dos.
        assertTrue(joint < Math.min(pBtts, pOver));
    }
}
