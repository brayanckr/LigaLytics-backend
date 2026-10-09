package com.ligalytics.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ligalytics.external.FootballChartsParser.ExternalFixture;
import com.ligalytics.patterns.builder.MatchAnalysis;
import com.ligalytics.patterns.strategy.FootballChartsStrategy;
import com.ligalytics.patterns.strategy.Prediction;

class FootballChartsTest {

    /** Forma de la respuesta real de /leagues/spain1/fixtures/ (recortada). */
    private static final String JSON = """
            {"league":"spain1","count":3,"matches":[
              {"slug":"spain/la-liga/2026-10-10-alaves-vs-atl-madrid","home_team":"Alaves","away_team":"Atl. Madrid",
               "match_date":"2026-10-10","status":"scheduled",
               "model_predictions":{"dc_v2":{
                 "raw":{"home":0.31,"away":0.45,"expected_home_goals":1.08,"expected_away_goals":1.46},
                 "calibrated":{"home":0.26,"away":0.50}}}},
              {"slug":"x","home_team":"Elche","away_team":"Celta Vigo","match_date":"2026-10-11",
               "model_predictions":{"dc_v2":{"raw":{"home":0.42,"away":0.29}}}},
              {"slug":"y","home_team":"Getafe","away_team":"Sevilla","match_date":"2026-10-12",
               "model_predictions":{}}
            ]}
            """;

    private final FootballChartsParser parser = new FootballChartsParser();

    @Test
    void parsesCalibratedProbabilitiesAndFallsBackToRaw() {
        List<ExternalFixture> fixtures = parser.parse(JSON, "football-charts");

        // El tercer partido no trae probabilidades y se omite.
        assertEquals(2, fixtures.size());
        WinnerOdds alaves = fixtures.get(0).odds();
        assertEquals(0.26, alaves.home(), 1e-9);
        assertEquals(0.50, alaves.away(), 1e-9);
        assertEquals(0.24, alaves.draw(), 1e-9);
        assertEquals("AWAY_WIN", alaves.outcome());
        assertEquals(LocalDate.of(2026, 10, 10), fixtures.get(0).date());

        WinnerOdds elche = fixtures.get(1).odds();
        assertEquals(0.42, elche.home(), 1e-9);
        assertEquals("HOME_WIN", elche.outcome());
    }

    @Test
    void probabilitiesAlwaysSumToOne() {
        WinnerOdds odds = WinnerOdds.of(0.7, 0.5, "football-charts");

        assertEquals(1.0, odds.home() + odds.draw() + odds.away(), 1e-9);
        assertTrue(odds.draw() >= 0.0);
    }

    @Test
    void strategyExposesOutcomeAndSource() {
        MatchAnalysis analysis = MatchAnalysis.builder()
                .teams("Alaves", "Atletico Madrid")
                .matchDate(LocalDateTime.of(2026, 10, 10, 14, 0))
                .build();

        FootballChartsStrategy strategy = new FootballChartsStrategy(WinnerOdds.of(0.26, 0.50, "football-charts"));
        Prediction prediction = strategy.predict(analysis);

        assertEquals("football-charts", strategy.name());
        assertEquals("resultado", prediction.target());
        assertEquals("AWAY_WIN", prediction.outcome());
        assertEquals(0.26, prediction.homeValue(), 1e-9);
        assertEquals(0.50, prediction.awayValue(), 1e-9);
    }

    @Test
    void clientWithoutKeyIsDisabledAndNeverCallsTheNetwork() {
        FootballChartsClient client = new FootballChartsClient("", "http://invalid.local", "spain1", parser);

        assertEquals(false, client.isConfigured());
        assertTrue(client.winnerOdds("Alaves", "Atletico Madrid").isEmpty());
    }
}
