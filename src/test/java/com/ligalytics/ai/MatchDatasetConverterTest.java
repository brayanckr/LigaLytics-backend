package com.ligalytics.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ligalytics.ai.MatchDatasetConverter.Sample;
import com.ligalytics.model.Match;
import com.ligalytics.model.Team;
import com.ligalytics.patterns.builder.MatchAnalysis;

import weka.core.Instances;

class MatchDatasetConverterTest {

    private final MatchDatasetConverter converter = new MatchDatasetConverter();

    private final Team a = team(1L, "Team A", "1000000000");
    private final Team b = team(2L, "Team B", "500000000");

    private Team team(long id, String name, String marketValue) {
        return Team.builder().id(id).name(name).marketValue(new BigDecimal(marketValue)).build();
    }

    private List<Match> sampleMatches() {
        return List.of(
                match(1L, a, b, 2, 1, 10, 5, 1),
                match(2L, b, a, 1, 1, 8, 3, 0),
                match(3L, a, b, 0, 2, 11, 6, 1),
                match(4L, a, b, 3, 0, 9, 4, 0));
    }

    private Match match(long id, Team home, Team away, int homeGoals, int awayGoals,
            int corners, int yellowCards, int redCards) {
        return Match.builder()
                .id(id)
                .matchDate(LocalDateTime.of(2025, 1, (int) id, 20, 0))
                .homeTeam(home)
                .awayTeam(away)
                .fullTimeHomeGoals(homeGoals)
                .fullTimeAwayGoals(awayGoals)
                .corners(corners)
                .yellowCards(yellowCards)
                .redCards(redCards)
                .build();
    }

    @Test
    void buildsResultDatasetWithExpectedShape() {
        Instances dataset = converter.buildFromMatches(PredictionTarget.RESULTADO, sampleMatches());

        assertEquals(4, dataset.numInstances());
        assertEquals(MatchFeatureExtractor.featureNames().size() + 1, dataset.numAttributes());
        assertEquals(MatchFeatureExtractor.featureNames().size(), dataset.classIndex());
        assertEquals("clase", dataset.classAttribute().name());
        assertEquals(3, dataset.classAttribute().numValues());
    }

    @Test
    void derivesCorrectLabelsAndNumericTargets() {
        Instances result = converter.buildFromMatches(PredictionTarget.RESULTADO, sampleMatches());
        assertEquals("HOME_WIN", result.classAttribute().value((int) result.get(0).classValue()));
        assertEquals("DRAW", result.classAttribute().value((int) result.get(1).classValue()));
        assertEquals("AWAY_WIN", result.classAttribute().value((int) result.get(2).classValue()));

        Instances goals = converter.buildFromMatches(PredictionTarget.GOLES, sampleMatches());
        assertTrue(goals.classAttribute().isNumeric());
        assertEquals(3.0, goals.get(0).classValue(), 1e-9);
        assertEquals(2.0, goals.get(1).classValue(), 1e-9);

        Instances corners = converter.buildFromMatches(PredictionTarget.CORNERES, sampleMatches());
        assertEquals(10.0, corners.get(0).classValue(), 1e-9);

        // tarjetas = amarillas + rojas
        Instances cards = converter.buildFromMatches(PredictionTarget.TARJETAS, sampleMatches());
        assertEquals(6.0, cards.get(0).classValue(), 1e-9);
    }

    @Test
    void buildsEmptyHeaderWhenNoMatches() {
        Instances dataset = converter.buildFromMatches(PredictionTarget.RESULTADO, List.of());

        assertEquals(0, dataset.numInstances());
        assertEquals(3, dataset.classAttribute().numValues());
        assertEquals(MatchFeatureExtractor.featureNames().size(), dataset.numAttributes() - 1);
    }

    @Test
    void featuresOfEachMatchOnlyUsePreviousMatches() {
        List<Sample> samples = converter.samples(sampleMatches());

        assertEquals(4, samples.size());
        // En el primer partido todavía no hay historial: nadie tiene forma ni posición propia.
        MatchAnalysis first = samples.get(0).analysis();
        assertEquals("", first.getHomeRecentForm());
        assertEquals("", first.getAwayRecentForm());

        // En el segundo, A (ganó 2-1 el primero) ya tiene forma "W" y B "L".
        MatchAnalysis second = samples.get(1).analysis();
        assertEquals("W", second.getAwayRecentForm());
        assertEquals("L", second.getHomeRecentForm());
        assertEquals(1, second.getAwayLeaguePosition());
        assertEquals(2, second.getHomeLeaguePosition());
    }

    @Test
    void futureResultDoesNotChangeEarlierFeatures() {
        List<Match> original = sampleMatches();
        List<Sample> before = converter.samples(original);

        // Cambiamos radicalmente el último partido: los anteriores no deben verse afectados.
        List<Match> changed = new java.util.ArrayList<>(original.subList(0, 3));
        changed.add(match(4L, a, b, 9, 9, 30, 20, 3));
        List<Sample> after = converter.samples(changed);

        for (int i = 0; i < 4; i++) {
            assertEquals(java.util.Arrays.toString(MatchFeatureExtractor.features(before.get(i).analysis())),
                    java.util.Arrays.toString(MatchFeatureExtractor.features(after.get(i).analysis())),
                    "Las variables del partido " + i + " dependen de partidos posteriores");
        }
    }

    @Test
    void eloRisesForTheWinnerAndFallsForTheLoser() {
        List<Sample> samples = converter.samples(sampleMatches());

        // Antes del 1.er partido ambos valen 1500; tras ganar A 2-1, A (visitante en el 2.o) supera a B.
        assertEquals(1500.0, samples.get(0).analysis().advanced(com.ligalytics.patterns.builder.AdvancedStats.HOME_ELO, 0), 1e-9);
        double eloB = samples.get(1).analysis().advanced(com.ligalytics.patterns.builder.AdvancedStats.HOME_ELO, 0);
        double eloA = samples.get(1).analysis().advanced(com.ligalytics.patterns.builder.AdvancedStats.AWAY_ELO, 0);
        assertTrue(eloA > 1500.0 && eloB < 1500.0, "A=" + eloA + " B=" + eloB);
    }

    @Test
    void headToHeadUsesOnlyEarlierMeetings() {
        List<Sample> samples = converter.samples(sampleMatches());
        var key = com.ligalytics.patterns.builder.AdvancedStats.H2H_MATCHES;
        var avg = com.ligalytics.patterns.builder.AdvancedStats.H2H_AVG_GOALS;

        // Sin precedentes en el 1.er partido; en el 4.o hay 3 (3, 2 y 2 goles totales).
        assertEquals(false, samples.get(0).analysis().hasAdvanced(key));
        assertEquals(3.0, samples.get(3).analysis().advanced(key, 0), 1e-9);
        assertEquals((3 + 2 + 2) / 3.0, samples.get(3).analysis().advanced(avg, 0), 1e-9);
    }

    @Test
    void featureVectorHasOneValuePerAttribute() {
        MatchAnalysis analysis = MatchAnalysis.builder()
                .teams("Team A", "Team B")
                .matchDate(LocalDateTime.of(2025, 5, 1, 20, 0))
                .expectedGoals(1.8, 1.1)
                .recentForm("WWDLW", "LDLWW")
                .leaguePositions(1, 4)
                .averageCorners(6.0, 4.0)
                .averageYellowCards(2.0, 2.8)
                .build();

        assertEquals(MatchFeatureExtractor.featureNames().size(),
                MatchFeatureExtractor.features(analysis).length);
    }
}
