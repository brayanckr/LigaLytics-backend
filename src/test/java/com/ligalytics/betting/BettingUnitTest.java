package com.ligalytics.betting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligalytics.betting.BettingService.Offer;
import com.ligalytics.service.dto.PredictionResponseDto;

class BettingUnitTest {

    private static PredictionResponseDto prediction(double home, double draw, double away, double corners, double cards) {
        return new PredictionResponseDto(1L, "Real Madrid", 2L, "Villarreal", "HOME_WIN", home, draw, away, 2.0, 1.0,
                "OVER_2_5", "2-1", corners, "OVER_9_5", cards, "OVER_4_5", 2.0, 2.0, 0.1, 0.1, Map.of(), "2026/2027",
                "football-charts", home, draw, away, List.of(), 0.6, 0.5, List.of(), false, Instant.now());
    }

    @Test
    void winnerProbabilitiesComeFromThePrediction() {
        MarketModel model = new MarketModel(prediction(0.64, 0.21, 0.15, 9.8, 5.0));

        assertEquals(0.64, model.probability(Market.WINNER, "HOME", null).orElseThrow(), 1e-9);
        assertEquals(0.15, model.probability(Market.WINNER, "AWAY", null).orElseThrow(), 1e-9);
        assertTrue(model.probability(Market.WINNER, "OTHER", null).isEmpty());
    }

    @Test
    void overAndUnderProbabilitiesAddUpToOneAndFollowTheLine() {
        MarketModel model = new MarketModel(prediction(0.5, 0.25, 0.25, 9.8, 5.0));

        for (Market market : List.of(Market.GOALS, Market.CORNERS, Market.CARDS)) {
            double over = model.probability(market, "OVER", 2.5).orElseThrow();
            double under = model.probability(market, "UNDER", 2.5).orElseThrow();
            assertEquals(1.0, over + under, 1e-9);
        }
        // Más córneres esperados → más probable superar una línea alta.
        double few = new MarketModel(prediction(0.5, 0.25, 0.25, 8.0, 5.0)).probability(Market.CORNERS, "OVER", 9.5).orElseThrow();
        double many = new MarketModel(prediction(0.5, 0.25, 0.25, 11.0, 5.0)).probability(Market.CORNERS, "OVER", 9.5).orElseThrow();
        assertTrue(many > few);
        // Superar 0,5 goles es mucho más probable que superar 3,5.
        assertTrue(model.probability(Market.GOALS, "OVER", 0.5).orElseThrow()
                > model.probability(Market.GOALS, "OVER", 3.5).orElseThrow());
    }

    @Test
    void demoCardOddsHaveNoEdgeByConstruction() {
        MarketModel model = new MarketModel(prediction(0.5, 0.25, 0.25, 9.8, 5.0));

        for (OddsLine line : model.demoCardOdds()) {
            double p = model.probability(Market.CARDS, line.selection(), line.line()).orElseThrow();
            assertEquals("demo", line.source());
            assertTrue(p * line.odds() - 1.0 < 0.0, "La cuota demo no debe dar ventaja: " + line);
        }
    }

    @Test
    void settlementRulesForEveryMarket() {
        // 2-1, 11 córneres, 5 tarjetas
        assertEquals(Optional.of(BetStatus.WON), BetSettlement.outcome(Market.WINNER, "HOME", null, 2, 1, 11, 5));
        assertEquals(Optional.of(BetStatus.LOST), BetSettlement.outcome(Market.WINNER, "DRAW", null, 2, 1, 11, 5));
        assertEquals(Optional.of(BetStatus.WON), BetSettlement.outcome(Market.GOALS, "OVER", 2.5, 2, 1, 11, 5));
        assertEquals(Optional.of(BetStatus.LOST), BetSettlement.outcome(Market.GOALS, "OVER", 3.5, 2, 1, 11, 5));
        assertEquals(Optional.of(BetStatus.WON), BetSettlement.outcome(Market.BTTS, "YES", null, 2, 1, 11, 5));
        assertEquals(Optional.of(BetStatus.LOST), BetSettlement.outcome(Market.BTTS, "NO", null, 2, 1, 11, 5));
        assertEquals(Optional.of(BetStatus.WON), BetSettlement.outcome(Market.CORNERS, "OVER", 10.5, 2, 1, 11, 5));
        assertEquals(Optional.of(BetStatus.WON), BetSettlement.outcome(Market.CARDS, "UNDER", 5.5, 2, 1, 11, 5));
        // Sin datos de córneres no se puede decidir (la apuesta queda pendiente).
        assertTrue(BetSettlement.outcome(Market.CORNERS, "OVER", 9.5, 2, 1, null, 5).isEmpty());
        assertTrue(BetSettlement.outcome(Market.CARDS, "OVER", 4.5, 2, 1, 11, null).isEmpty());
    }

    @Test
    void suggestedStakeIsCappedAtFivePercentAndNeverBelowTheMinimum() {
        OddsLine line = new OddsLine(Market.WINNER, "AWAY", null, 2.0, "consenso");

        // Ventaja enorme (p=0,9): la sugerencia no pasa del 5 % del saldo (5 000 de 100 000).
        assertEquals(5_000L, BettingService.suggestedStake(new Offer(line, 0.9, 0.5, 0.9, 0.8), 100_000L));
        // Ventaja mínima: al menos el importe mínimo.
        assertEquals(1_000L, BettingService.suggestedStake(new Offer(line, 0.53, 0.5, 0.53, 0.06), 100_000L));
    }

    @Test
    void noVigProbabilitiesSumToOneWithinAMarket() {
        List<OddsLine> lines = List.of(
                new OddsLine(Market.WINNER, "HOME", null, 2.0, "consenso"),
                new OddsLine(Market.WINNER, "DRAW", null, 4.0, "consenso"),
                new OddsLine(Market.WINNER, "AWAY", null, 4.0, "consenso"),
                new OddsLine(Market.GOALS, "OVER", 2.5, 1.8, "consenso"));

        double sum = 0;
        for (OddsLine line : lines.subList(0, 3)) {
            sum += BettingService.noVigProbability(line, lines);
        }
        assertEquals(1.0, sum, 1e-9);
        // El par más/menos está incompleto (falta UNDER): no hay probabilidad de mercado.
        assertEquals(null, BettingService.noVigProbability(lines.get(3), lines));
    }

    @Test
    void suspiciousEdgesAreNotRecommended() {
        OddsLine real = new OddsLine(Market.GOALS, "UNDER", 2.5, 2.0, "consenso");
        // Ventaja moderada y modelo cercano al mercado: se recomienda.
        assertTrue(BettingService.isRecommendable(new Offer(real, 0.58, 0.50, 0.54, 0.08)));
        // Ventaja enorme: probable error del modelo.
        assertEquals(false, BettingService.isRecommendable(new Offer(real, 0.90, 0.50, 0.70, 0.40)));
        // Modelo y mercado muy distintos aunque la ventaja ajustada parezca razonable.
        assertEquals(false, BettingService.isRecommendable(new Offer(real, 0.72, 0.50, 0.61, 0.22)));
        // Cuota demo (sin mercado real): nunca se recomienda.
        assertEquals(false, BettingService.isRecommendable(new Offer(
                new OddsLine(Market.CARDS, "OVER", 4.5, 2.0, "demo"), 0.6, null, 0.6, 0.2)));
    }

    @Test
    void parsesPinnacleCardOddsKeepingOnlyHalfLines() throws Exception {
        String json = """
                {"bookmakers":[{"key":"pinnacle","markets":[{"key":"alternate_totals_cards","outcomes":[
                  {"name":"Over","price":1.8,"point":4.5},{"name":"Under","price":2.01,"point":4.5},
                  {"name":"Over","price":1.47,"point":4.0},{"name":"Under","price":2.61,"point":4.0},
                  {"name":"Over","price":2.5,"point":5.5},{"name":"Under","price":1.49,"point":5.5}]},
                  {"key":"alternate_totals_corners","outcomes":[{"name":"Over","price":1.67,"point":9.5}]}]}]}
                """;

        List<OddsLine> lines = TheOddsApiCardsProvider.parse(new ObjectMapper().readTree(json));

        assertEquals(4, lines.size());
        assertTrue(lines.stream().allMatch(l -> l.market() == Market.CARDS && l.isReal() && "pinnacle".equals(l.source())));
        assertTrue(lines.stream().anyMatch(l -> l.sameAs(Market.CARDS, "UNDER", 4.5) && l.odds() == 2.01));
        assertTrue(lines.stream().noneMatch(l -> l.line() == 4.0));
    }

    @Test
    void parsesRealOddsRowsAndIgnoresUnsupportedMarkets() throws Exception {
        String json = """
                [{"market":"1x2","outcome":"HOME","line":null,"decimal_odds":1.335},
                 {"market":"1x2","outcome":"DRAW","line":null,"decimal_odds":5.78},
                 {"market":"over_under_25","outcome":"over","line":2.5,"decimal_odds":1.268},
                 {"market":"btts","outcome":"yes","line":null,"decimal_odds":1.468},
                 {"market":"total_corners","outcome":"under","line":9.5,"decimal_odds":2.373},
                 {"market":"double_chance","outcome":"1X","line":null,"decimal_odds":1.084},
                 {"market":"1x2","outcome":"AWAY","line":null,"decimal_odds":0.5}]
                """;
        List<JsonNode> rows = new java.util.ArrayList<>();
        new ObjectMapper().readTree(json).forEach(rows::add);

        List<OddsLine> lines = BzzoiroOddsProvider.parse(rows);

        assertEquals(5, lines.size());
        assertTrue(lines.stream().anyMatch(l -> l.sameAs(Market.WINNER, "HOME", null) && l.odds() == 1.34));
        assertTrue(lines.stream().anyMatch(l -> l.sameAs(Market.GOALS, "OVER", 2.5)));
        assertTrue(lines.stream().anyMatch(l -> l.sameAs(Market.CORNERS, "UNDER", 9.5)));
        assertTrue(lines.stream().anyMatch(l -> l.sameAs(Market.BTTS, "YES", null)));
    }
}
