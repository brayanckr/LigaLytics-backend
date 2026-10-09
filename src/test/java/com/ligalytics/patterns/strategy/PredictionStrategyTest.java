package com.ligalytics.patterns.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.ligalytics.patterns.builder.MatchAnalysis;
import com.ligalytics.patterns.factory.PredictorFactory;
import com.ligalytics.patterns.factory.PredictorType;

class PredictionStrategyTest {

    private MatchAnalysis sample() {
        return MatchAnalysis.builder()
                .teams("Real Madrid", "FC Barcelona")
                .matchDate(LocalDateTime.of(2025, 3, 1, 21, 0))
                .expectedGoals(2.1, 0.9)
                .recentForm("WWWWW", "LDLWW")
                .leaguePositions(1, 4)
                .averageCorners(10.8, 9.6)
                .averageYellowCards(2.0, 2.8)
                .build();
    }

    @Test
    void logisticRegressionPredictsHomeWin() {
        Prediction prediction = new LogisticRegressionStrategy().predict(sample());

        assertEquals("resultado", prediction.target());
        assertEquals("HOME_WIN", prediction.outcome());
        assertTrue(prediction.confidence() > 0.5);
    }

    @Test
    void poissonEstimatesExpectedGoals() {
        Prediction prediction = new PoissonStrategy().predict(sample());

        assertEquals("goles", prediction.target());
        assertEquals(3.0, prediction.total(), 0.001);
        assertNotNull(prediction.outcome());
    }

    @Test
    void decisionTreePredictsCornersAndCards() {
        Prediction corners = DecisionTreeStrategy.forCorners().predict(sample());
        assertEquals("corneres", corners.target());
        assertEquals("OVER_9_5", corners.outcome());

        Prediction cards = DecisionTreeStrategy.forCards().predict(sample());
        assertEquals("tarjetas", cards.target());
        assertEquals("OVER_4_5", cards.outcome());
    }

    @Test
    void contextSwapsStrategiesAtRuntime() {
        MatchAnalysis analysis = sample();
        PredictionContext context = new PredictionContext(new LogisticRegressionStrategy());

        Prediction resultado = context.predict(analysis);
        assertEquals("resultado", resultado.target());
        assertEquals("logistic-regression", context.strategyName());

        context.setStrategy(new PoissonStrategy());
        Prediction goles = context.predict(analysis);
        assertEquals("goles", goles.target());
        assertEquals("poisson", context.strategyName());

        context.setStrategy(DecisionTreeStrategy.forCards());
        Prediction tarjetas = context.predict(analysis);
        assertEquals("tarjetas", tarjetas.target());
        assertEquals("decision-tree", context.strategyName());
        assertNotNull(tarjetas.outcome());
    }

    @Test
    void factoryWiresStrategiesIntoPredictors() {
        assertInstanceOf(LogisticRegressionStrategy.class, PredictorFactory.createStrategy(PredictorType.RESULTADO));
        assertInstanceOf(PoissonStrategy.class, PredictorFactory.createStrategy(PredictorType.GOLES));
        assertInstanceOf(DecisionTreeStrategy.class, PredictorFactory.createStrategy(PredictorType.CORNERES));
        assertInstanceOf(DecisionTreeStrategy.class, PredictorFactory.createStrategy("tarjetas"));

        Predictor predictor = PredictorFactory.createPredictor(PredictorType.GOLES);
        assertEquals("goles", predictor.target());
        assertEquals("goles", predictor.predict(sample()).target());
    }
}
