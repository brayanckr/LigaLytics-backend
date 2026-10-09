package com.ligalytics.patterns.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.ligalytics.patterns.builder.MatchAnalysis;
import com.ligalytics.patterns.strategy.Predictor;
import com.ligalytics.patterns.strategy.PredictorCorneres;
import com.ligalytics.patterns.strategy.PredictorGoles;
import com.ligalytics.patterns.strategy.PredictorResultado;
import com.ligalytics.patterns.strategy.PredictorTarjetas;

class PredictorFactoryTest {

    @Test
    void createsEachPredictorFromItsType() {
        assertInstanceOf(PredictorResultado.class, PredictorFactory.createPredictor(PredictorType.RESULTADO));
        assertInstanceOf(PredictorGoles.class, PredictorFactory.createPredictor(PredictorType.GOLES));
        assertInstanceOf(PredictorCorneres.class, PredictorFactory.createPredictor(PredictorType.CORNERES));
        assertInstanceOf(PredictorTarjetas.class, PredictorFactory.createPredictor(PredictorType.TARJETAS));
    }

    @Test
    void createsPredictorFromTextCodeIgnoringCase() {
        assertInstanceOf(PredictorResultado.class, PredictorFactory.createPredictor("resultado"));
        assertInstanceOf(PredictorGoles.class, PredictorFactory.createPredictor("GOLES"));
        assertInstanceOf(PredictorCorneres.class, PredictorFactory.createPredictor("Corneres"));
        assertInstanceOf(PredictorTarjetas.class, PredictorFactory.createPredictor(" TARJETAS "));
    }

    @Test
    void rejectsUnknownType() {
        assertThrows(IllegalArgumentException.class, () -> PredictorFactory.createPredictor("posesion"));
        assertThrows(IllegalArgumentException.class, () -> PredictorType.from(""));
        assertThrows(IllegalArgumentException.class, () -> PredictorType.from(null));
    }

    @Test
    void rejectsNullType() {
        assertThrows(NullPointerException.class, () -> PredictorFactory.createPredictor((PredictorType) null));
    }

    @Test
    void createdPredictorProducesAPrediction() {
        MatchAnalysis analysis = MatchAnalysis.builder()
                .teams("Real Madrid", "FC Barcelona")
                .matchDate(LocalDateTime.of(2025, 3, 1, 21, 0))
                .expectedGoals(2.1, 0.9)
                .recentForm("WWWWW", "LDLWW")
                .leaguePositions(1, 4)
                .averageCorners(6.0, 4.0)
                .averageYellowCards(2.0, 2.8)
                .build();

        Predictor resultado = PredictorFactory.createPredictor(PredictorType.RESULTADO);
        assertEquals("resultado", resultado.target());
        assertEquals("HOME_WIN", resultado.predict(analysis).outcome());

        Predictor goles = PredictorFactory.createPredictor(PredictorType.GOLES);
        assertEquals("goles", goles.target());
        assertEquals(3.0, goles.predict(analysis).total(), 0.001);

        Predictor corneres = PredictorFactory.createPredictor(PredictorType.CORNERES);
        assertEquals("corneres", corneres.target());
        assertNotNull(corneres.predict(analysis).outcome());

        Predictor tarjetas = PredictorFactory.createPredictor(PredictorType.TARJETAS);
        assertEquals("tarjetas", tarjetas.target());
        assertNotNull(tarjetas.predict(analysis).outcome());
    }
}
