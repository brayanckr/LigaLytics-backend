package com.ligalytics.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ligalytics.model.Match;
import com.ligalytics.model.Team;
import com.ligalytics.repository.MatchRepository;

/**
 * Comprueba el esquema de validación de la propuesta: entrenar con las
 * temporadas anteriores y medir accuracy / MAE en la temporada de prueba.
 */
class ModelEvaluationTest {

    private WekaTrainingService service;
    private TrainingReportHolder holder;

    @BeforeEach
    void setUp() throws IOException {
        Path directory = Files.createTempDirectory("ligalytics-eval-");
        AiProperties properties = new AiProperties();
        properties.setModelPath(directory.toString());
        properties.setMinTrainingMatches(8);
        properties.setTestSeasonStartYear(2023);

        holder = new TrainingReportHolder(properties);
        service = new WekaTrainingService(mock(MatchRepository.class), new MatchDatasetConverter(),
                new WekaModelManager(properties), holder, properties);
    }

    @Test
    void trainsOnPreviousSeasonsAndEvaluatesOnTheTestSeason() {
        List<Match> matches = new ArrayList<>();
        matches.addAll(season(2021));
        matches.addAll(season(2022));
        matches.addAll(season(2023));

        TrainingReport report = service.train(matches);

        assertTrue(report.evaluation().contains("Prueba 2023/2024"), report.evaluation());
        assertTrue(report.evaluation().contains("2021/2022"), report.evaluation());
        assertEquals(4, report.trainedModels(), report.toString());

        TrainingReport.TargetResult resultado = result(report, "resultado");
        assertEquals("accuracy", resultado.metricName());
        assertNotNull(resultado.metric());
        assertTrue(resultado.metric() >= 0.0 && resultado.metric() <= 1.0);
        assertNotNull(resultado.baseline());
        assertEquals(season(2023).size(), resultado.testInstances());
        assertEquals(season(2021).size() + season(2022).size(), resultado.trainInstances());

        for (String code : List.of("goles", "corneres", "tarjetas")) {
            TrainingReport.TargetResult numeric = result(report, code);
            assertEquals("MAE", numeric.metricName(), code);
            assertNotNull(numeric.metric(), code);
            assertTrue(numeric.metric() >= 0.0, code);
            assertNotNull(numeric.baseline(), code);
        }
        assertTrue(holder.latest().isPresent());
    }

    @Test
    void usesCrossValidationWhenThereIsNoSeparateTestSeason() {
        TrainingReport report = service.train(season(2024));

        assertTrue(report.evaluation().startsWith("Validación cruzada"), report.evaluation());
        assertEquals(4, report.trainedModels(), report.toString());
    }

    private static TrainingReport.TargetResult result(TrainingReport report, String code) {
        return report.results().stream().filter(r -> r.target().equals(code)).findFirst().orElseThrow();
    }

    /** Liga sintética de 4 equipos con 6 vueltas (72 partidos) que empieza en agosto del año indicado. */
    private static List<Match> season(int startYear) {
        List<Team> teams = List.of(
                team(1, "Alfa", "900000000"), team(2, "Beta", "600000000"),
                team(3, "Gamma", "300000000"), team(4, "Delta", "100000000"));
        List<Match> matches = new ArrayList<>();
        LocalDateTime date = LocalDateTime.of(startYear, 8, 20, 20, 0);
        long id = startYear * 1000L;
        for (int round = 0; round < 6; round++) {
            for (Team home : teams) {
                for (Team away : teams) {
                    if (home == away) {
                        continue;
                    }
                    int strengthGap = (int) (home.getMarketValue().longValue() - away.getMarketValue().longValue())
                            / 300_000_000;
                    int homeGoals = Math.max(0, 1 + strengthGap + (round % 2));
                    int awayGoals = Math.max(0, (round + home.getId().intValue()) % 2);
                    matches.add(Match.builder()
                            .id(id++)
                            .matchDate(date)
                            .homeTeam(home)
                            .awayTeam(away)
                            .fullTimeHomeGoals(homeGoals)
                            .fullTimeAwayGoals(awayGoals)
                            .corners(7 + (int) (id % 6))
                            .yellowCards(2 + (int) (id % 4))
                            .redCards((int) (id % 9 == 0 ? 1 : 0))
                            .homeYellowCards(1 + (int) (id % 3))
                            .awayYellowCards(1 + (int) (id % 2))
                            .build());
                    date = date.plusDays(2);
                }
            }
        }
        return matches;
    }

    private static Team team(long id, String name, String value) {
        return Team.builder().id(id).name(name).marketValue(new BigDecimal(value)).build();
    }
}
