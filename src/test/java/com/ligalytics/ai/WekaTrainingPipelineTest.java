package com.ligalytics.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.ligalytics.model.Match;
import com.ligalytics.model.Team;
import com.ligalytics.model.TeamStats;
import com.ligalytics.patterns.builder.MatchAnalysis;
import com.ligalytics.patterns.factory.PredictionStrategyResolver;
import com.ligalytics.patterns.factory.PredictorType;
import com.ligalytics.patterns.strategy.LogisticRegressionStrategy;
import com.ligalytics.patterns.strategy.Prediction;
import com.ligalytics.patterns.strategy.PredictionStrategy;
import com.ligalytics.patterns.strategy.WekaStrategy;
import com.ligalytics.repository.MatchRepository;
import com.ligalytics.repository.TeamRepository;
import com.ligalytics.repository.TeamStatsRepository;

@SpringBootTest
class WekaTrainingPipelineTest {

    private static final LocalDateTime BASE_DATE = LocalDateTime.of(2024, 8, 1, 20, 0);

    static Path modelDirectory;

    @DynamicPropertySource
    static void modelProperties(DynamicPropertyRegistry registry) throws IOException {
        modelDirectory = Files.createTempDirectory("ligalytics-weka-");
        registry.add("ligalytics.ai.model-path", () -> modelDirectory.toString());
    }

    @Autowired
    private WekaTrainingService trainingService;

    @Autowired
    private WekaModelManager modelManager;

    @Autowired
    private PredictionStrategyResolver resolver;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamStatsRepository teamStatsRepository;

    @BeforeEach
    void setUp() {
        matchRepository.deleteAll();
        teamStatsRepository.deleteAll();
        teamRepository.deleteAll();
        modelManager.deleteAll();
        seedData();
    }

    @Test
    void trainsPersistsAndReloadsModels() {
        TrainingReport report = trainingService.trainAll();

        assertTrue(report.anyTrained(), report.toString());
        assertEquals(4, report.trainedModels());

        for (PredictionTarget target : PredictionTarget.values()) {
            if (target != PredictionTarget.GOLES) {
                // Los goles usan Poisson (modelo estadístico): no hay fichero de modelo.
                assertTrue(modelManager.exists(target), "Falta el modelo " + target.code());
            }
            TrainingReport.TargetResult result = report.results().stream()
                    .filter(r -> r.target().equals(target.code()))
                    .findFirst().orElseThrow();
            assertTrue(result.trained());
            assertTrue(result.trainInstances() > 0);
        }

        modelManager.clearCache();
        assertTrue(modelManager.load(PredictionTarget.RESULTADO).isPresent());
    }

    @Test
    void resolvesWekaStrategiesAndPredictsConsistently() {
        trainingService.trainAll();
        modelManager.clearCache();

        PredictionStrategy resultStrategy = resolver.resolveStrategy(PredictorType.RESULTADO);
        assertInstanceOf(WekaStrategy.class, resultStrategy);

        MatchAnalysis analysis = sampleAnalysis();
        Prediction result = resultStrategy.predict(analysis);
        assertTrue(Set.of("HOME_WIN", "DRAW", "AWAY_WIN").contains(result.outcome()));
        assertTrue(result.confidence() >= 0.0 && result.confidence() <= 1.0);
        assertTrue(result.homeValue() + result.awayValue() <= 1.0 + 1e-6);

        Prediction goals = resolver.resolveStrategy(PredictorType.GOLES).predict(analysis);
        assertTrue(Set.of("UNDER_2_5", "OVER_2_5").contains(goals.outcome()));

        Prediction corners = resolver.resolveStrategy(PredictorType.CORNERES).predict(analysis);
        assertTrue(Set.of("UNDER_9_5", "OVER_9_5").contains(corners.outcome()));

        Prediction cards = resolver.resolveStrategy(PredictorType.TARJETAS).predict(analysis);
        assertTrue(Set.of("UNDER_4_5", "OVER_4_5").contains(cards.outcome()));
    }

    @Test
    void fallsBackToHeuristicWhenNoModelIsTrained() {
        modelManager.deleteAll();

        PredictionStrategy strategy = resolver.resolveStrategy(PredictorType.RESULTADO);

        assertInstanceOf(LogisticRegressionStrategy.class, strategy);
        assertFalse(resolver.hasTrainedModel(PredictorType.RESULTADO));
    }

    @Test
    void doesNotTrainWithInsufficientData() {
        matchRepository.deleteAll();
        modelManager.deleteAll();

        TrainingReport report = trainingService.trainAll();

        assertFalse(report.anyTrained());
        assertFalse(modelManager.exists(PredictionTarget.RESULTADO));
    }

    private MatchAnalysis sampleAnalysis() {
        return MatchAnalysis.builder()
                .teams("Equipo 1", "Equipo 2")
                .matchDate(LocalDateTime.of(2025, 5, 1, 20, 0))
                .expectedGoals(1.8, 1.1)
                .recentForm("WWDLW", "LDLWW")
                .leaguePositions(1, 3)
                .averageCorners(6.5, 4.5)
                .averageYellowCards(2.5, 3.0)
                .marketValues(new BigDecimal("1000000000"), new BigDecimal("700000000"))
                .build();
    }

    private void seedData() {
        Team t1 = teamRepository.save(team("Equipo 1", "1000000000"));
        Team t2 = teamRepository.save(team("Equipo 2", "800000000"));
        Team t3 = teamRepository.save(team("Equipo 3", "600000000"));
        Team t4 = teamRepository.save(team("Equipo 4", "400000000"));
        List<Team> teams = List.of(t1, t2, t3, t4);

        for (int i = 0; i < 24; i++) {
            Team home = teams.get(i % 4);
            Team away = teams.get((i + 1) % 4);
            matchRepository.save(Match.builder()
                    .matchDate(BASE_DATE.plusDays(i))
                    .homeTeam(home)
                    .awayTeam(away)
                    .fullTimeHomeGoals(i % 4)
                    .fullTimeAwayGoals((i / 2) % 3)
                    .homeXg(0.5 + (i % 5) * 0.3)
                    .awayXg(0.4 + (i % 4) * 0.25)
                    .corners(6 + (i % 7))
                    .yellowCards(2 + (i % 5))
                    .build());
        }

        for (int i = 0; i < teams.size(); i++) {
            Team team = teams.get(i);
            teamStatsRepository.save(TeamStats.builder()
                    .team(team)
                    .season("2024/2025")
                    .matchesPlayed(6)
                    .wins(3)
                    .draws(1)
                    .losses(2)
                    .goalsFor(9)
                    .goalsAgainst(7)
                    .points(10 - i * 2)
                    .averageGoalsFor(1.5)
                    .averageGoalsAgainst(1.1)
                    .averageCorners(7.0 + i)
                    .averageYellowCards(3.0 + i * 0.2)
                    .build());
        }
    }

    private Team team(String name, String marketValue) {
        return Team.builder()
                .name(name)
                .stadium("Estadio " + name)
                .marketValue(new BigDecimal(marketValue))
                .build();
    }
}
