package com.ligalytics.ai;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.ligalytics.patterns.builder.AdvancedStats;
import com.ligalytics.patterns.builder.MatchAnalysis;

import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Utils;

/**
 * Define el vector de características compartido entre el entrenamiento y la
 * predicción. Convierte un {@link MatchAnalysis} en los valores numéricos que
 * consumen los modelos de Weka, garantizando que train y predict usen
 * exactamente las mismas variables.
 */
public final class MatchFeatureExtractor {

    public static final String CLASS_ATTRIBUTE = "clase";
    private static final double MARKET_VALUE_SCALE = 1_000_000_000.0;

    private MatchFeatureExtractor() {
    }

    public static List<String> featureNames() {
        return List.of(
                "homeAvgGoalsFor", "homeAvgGoalsAgainst", "awayAvgGoalsFor", "awayAvgGoalsAgainst",
                "homeXg", "awayXg",
                "homeForm", "awayForm", "formDiff",
                "homePosition", "awayPosition", "positionDiff",
                "homeMarketValue", "awayMarketValue", "marketValueDiff",
                "homeAvgCorners", "awayAvgCorners",
                "homeAvgYellowCards", "awayAvgYellowCards",
                "homeAvgRedCards", "awayAvgRedCards",
                "homeElo", "awayElo", "eloDiff", "eloExpected",
                "homeLongGoalsFor", "homeLongGoalsAgainst", "awayLongGoalsFor", "awayLongGoalsAgainst",
                "homeAtHomeGoalsFor", "homeAtHomeGoalsAgainst", "awayAwayGoalsFor", "awayAwayGoalsAgainst",
                "homeSotFor", "homeSotAgainst", "awaySotFor", "awaySotAgainst",
                "homeRestDays", "awayRestDays",
                "homeXgFor", "homeXgAgainst", "awayXgFor", "awayXgAgainst", "xgAvailable");
    }

    /**
     * Crea la cabecera ({@link Instances} vacía) para un objetivo de predicción.
     * La clase es nominal para el resultado y numérica para el resto.
     */
    public static Instances createDataset(PredictionTarget target) {
        List<Attribute> attributes = new ArrayList<>();
        for (String feature : featureNames()) {
            attributes.add(new Attribute(feature));
        }
        attributes.add(target.isNumeric()
                ? new Attribute(CLASS_ATTRIBUTE)
                : new Attribute(CLASS_ATTRIBUTE, target.classValues()));

        Instances dataset = new Instances("ligalytics-" + target.code(), new ArrayList<>(attributes), 0);
        dataset.setClassIndex(attributes.size() - 1);
        return dataset;
    }

    /**
     * Extrae el vector numérico de un análisis de partido. Orden idéntico al de
     * {@link #featureNames()}.
     */
    public static double[] features(MatchAnalysis analysis) {
        Objects.requireNonNull(analysis, "analysis must not be null");

        double homeForm = formPoints(analysis.getHomeRecentForm());
        double awayForm = formPoints(analysis.getAwayRecentForm());
        double homePosition = value(analysis.getHomeLeaguePosition());
        double awayPosition = value(analysis.getAwayLeaguePosition());
        double homeMarketValue = value(analysis.getHomeMarketValue());
        double awayMarketValue = value(analysis.getAwayMarketValue());
        double rawHomeElo = analysis.advanced(AdvancedStats.HOME_ELO, 1500.0);
        double rawAwayElo = analysis.advanced(AdvancedStats.AWAY_ELO, 1500.0);
        double homeElo = (rawHomeElo - 1500.0) / 100.0;
        double awayElo = (rawAwayElo - 1500.0) / 100.0;
        double eloExpected = 1.0 / (1.0 + Math.pow(10.0, (rawAwayElo - rawHomeElo - 60.0) / 400.0));

        return new double[] {
                value(analysis.getHomeAverageGoalsFor()), value(analysis.getHomeAverageGoalsAgainst()),
                value(analysis.getAwayAverageGoalsFor()), value(analysis.getAwayAverageGoalsAgainst()),
                value(analysis.getHomeXg()), value(analysis.getAwayXg()),
                homeForm, awayForm, homeForm - awayForm,
                homePosition, awayPosition, awayPosition - homePosition,
                homeMarketValue, awayMarketValue, homeMarketValue - awayMarketValue,
                value(analysis.getHomeAverageCorners()), value(analysis.getAwayAverageCorners()),
                value(analysis.getHomeAverageYellowCards()), value(analysis.getAwayAverageYellowCards()),
                value(analysis.getHomeAverageRedCards()), value(analysis.getAwayAverageRedCards()),
                homeElo, awayElo, homeElo - awayElo, eloExpected,
                analysis.advanced(AdvancedStats.HOME_LONG_GF, 0.0), analysis.advanced(AdvancedStats.HOME_LONG_GA, 0.0),
                analysis.advanced(AdvancedStats.AWAY_LONG_GF, 0.0), analysis.advanced(AdvancedStats.AWAY_LONG_GA, 0.0),
                analysis.advanced(AdvancedStats.HOME_VENUE_GF, 0.0), analysis.advanced(AdvancedStats.HOME_VENUE_GA, 0.0),
                analysis.advanced(AdvancedStats.AWAY_VENUE_GF, 0.0), analysis.advanced(AdvancedStats.AWAY_VENUE_GA, 0.0),
                analysis.advanced(AdvancedStats.HOME_SOT_FOR, 0.0), analysis.advanced(AdvancedStats.HOME_SOT_AGAINST, 0.0),
                analysis.advanced(AdvancedStats.AWAY_SOT_FOR, 0.0), analysis.advanced(AdvancedStats.AWAY_SOT_AGAINST, 0.0),
                analysis.advanced(AdvancedStats.HOME_REST_DAYS, 7.0), analysis.advanced(AdvancedStats.AWAY_REST_DAYS, 7.0),
                analysis.advanced(AdvancedStats.HOME_XG_FOR, 0.0), analysis.advanced(AdvancedStats.HOME_XG_AGAINST, 0.0),
                analysis.advanced(AdvancedStats.AWAY_XG_FOR, 0.0), analysis.advanced(AdvancedStats.AWAY_XG_AGAINST, 0.0),
                analysis.advanced(AdvancedStats.XG_AVAILABLE, 0.0)
        };
    }

    /**
     * Convierte un {@link MatchAnalysis} en una instancia Weka con clase
     * desconocida, lista para predecir.
     */
    public static Instance toInstance(Instances dataset, MatchAnalysis analysis) {
        double[] features = features(analysis);
        double[] values = new double[dataset.numAttributes()];
        System.arraycopy(features, 0, values, 0, features.length);
        values[dataset.classIndex()] = Utils.missingValue();

        DenseInstance instance = new DenseInstance(1.0, values);
        instance.setDataset(dataset);
        return instance;
    }

    static double formPoints(String form) {
        if (form == null || form.isBlank()) {
            return 0.5;
        }
        int points = 0;
        int counted = 0;
        for (char c : form.toUpperCase().toCharArray()) {
            switch (c) {
                case 'W' -> {
                    points += 3;
                    counted++;
                }
                case 'D' -> {
                    points += 1;
                    counted++;
                }
                case 'L' -> counted++;
                default -> {
                }
            }
        }
        return counted == 0 ? 0.5 : (double) points / (counted * 3.0);
    }

    private static double value(Double number) {
        return number == null ? 0.0 : number;
    }

    private static double value(Integer number) {
        return number == null ? 0.0 : number;
    }

    private static double value(BigDecimal number) {
        return number == null ? 0.0 : number.doubleValue() / MARKET_VALUE_SCALE;
    }
}
