package com.ligalytics.patterns.builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Implementación concreta del {@link MatchAnalysisBuilder}. Acumula los valores
 * en campos mutables y construye un {@link MatchAnalysis} inmutable al final.
 *
 * <p>Los campos obligatorios (equipos y fecha) se validan en {@link #build()};
 * el resto son opcionales y quedan a {@code null} si no se informan.</p>
 */
public class DefaultMatchAnalysisBuilder implements MatchAnalysisBuilder {

    private String homeTeam;
    private String awayTeam;
    private LocalDateTime matchDate;
    private String season;

    private Integer homeGoals;
    private Integer awayGoals;
    private Double homeXg;
    private Double awayXg;

    private Integer corners;
    private Integer yellowCards;
    private Integer redCards;
    private Integer homeYellowCards;
    private Integer awayYellowCards;
    private Integer homeRedCards;
    private Integer awayRedCards;

    private BigDecimal homeMarketValue;
    private BigDecimal awayMarketValue;

    private String homeRecentForm;
    private String awayRecentForm;

    private Integer homeLeaguePosition;
    private Integer awayLeaguePosition;

    private Double homeAverageCorners;
    private Double awayAverageCorners;
    private Double homeAverageYellowCards;
    private Double awayAverageYellowCards;
    private Double homeAverageRedCards;
    private Double awayAverageRedCards;

    private Double homeAverageGoalsFor;
    private Double homeAverageGoalsAgainst;
    private Double awayAverageGoalsFor;
    private Double awayAverageGoalsAgainst;
    private Double leagueAverageHomeGoals;
    private Double leagueAverageAwayGoals;
    private final java.util.Map<String, Double> advanced = new java.util.HashMap<>();

    @Override
    public MatchAnalysisBuilder teams(String homeTeam, String awayTeam) {
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        return this;
    }

    @Override
    public MatchAnalysisBuilder matchDate(LocalDateTime matchDate) {
        this.matchDate = matchDate;
        return this;
    }

    @Override
    public MatchAnalysisBuilder season(String season) {
        this.season = season;
        return this;
    }

    @Override
    public MatchAnalysisBuilder score(Integer homeGoals, Integer awayGoals) {
        this.homeGoals = homeGoals;
        this.awayGoals = awayGoals;
        return this;
    }

    @Override
    public MatchAnalysisBuilder expectedGoals(Double homeXg, Double awayXg) {
        this.homeXg = homeXg;
        this.awayXg = awayXg;
        return this;
    }

    @Override
    public MatchAnalysisBuilder corners(Integer corners) {
        this.corners = corners;
        return this;
    }

    @Override
    public MatchAnalysisBuilder cards(Integer yellowCards, Integer redCards) {
        this.yellowCards = yellowCards;
        this.redCards = redCards;
        return this;
    }

    @Override
    public MatchAnalysisBuilder teamCards(Integer homeYellow, Integer awayYellow, Integer homeRed, Integer awayRed) {
        this.homeYellowCards = homeYellow;
        this.awayYellowCards = awayYellow;
        this.homeRedCards = homeRed;
        this.awayRedCards = awayRed;
        return this;
    }

    @Override
    public MatchAnalysisBuilder marketValues(BigDecimal homeMarketValue, BigDecimal awayMarketValue) {
        this.homeMarketValue = homeMarketValue;
        this.awayMarketValue = awayMarketValue;
        return this;
    }

    @Override
    public MatchAnalysisBuilder recentForm(String homeRecentForm, String awayRecentForm) {
        this.homeRecentForm = homeRecentForm;
        this.awayRecentForm = awayRecentForm;
        return this;
    }

    @Override
    public MatchAnalysisBuilder leaguePositions(Integer homeLeaguePosition, Integer awayLeaguePosition) {
        this.homeLeaguePosition = homeLeaguePosition;
        this.awayLeaguePosition = awayLeaguePosition;
        return this;
    }

    @Override
    public MatchAnalysisBuilder averageCorners(Double homeAverageCorners, Double awayAverageCorners) {
        this.homeAverageCorners = homeAverageCorners;
        this.awayAverageCorners = awayAverageCorners;
        return this;
    }

    @Override
    public MatchAnalysisBuilder averageYellowCards(Double homeAverageYellowCards, Double awayAverageYellowCards) {
        this.homeAverageYellowCards = homeAverageYellowCards;
        this.awayAverageYellowCards = awayAverageYellowCards;
        return this;
    }

    @Override
    public MatchAnalysisBuilder averageRedCards(Double homeAverageRedCards, Double awayAverageRedCards) {
        this.homeAverageRedCards = homeAverageRedCards;
        this.awayAverageRedCards = awayAverageRedCards;
        return this;
    }

    @Override
    public MatchAnalysisBuilder averageGoals(Double homeFor, Double homeAgainst, Double awayFor, Double awayAgainst) {
        this.homeAverageGoalsFor = homeFor;
        this.homeAverageGoalsAgainst = homeAgainst;
        this.awayAverageGoalsFor = awayFor;
        this.awayAverageGoalsAgainst = awayAgainst;
        return this;
    }

    @Override
    public MatchAnalysisBuilder leagueGoalAverages(Double homeGoals, Double awayGoals) {
        this.leagueAverageHomeGoals = homeGoals;
        this.leagueAverageAwayGoals = awayGoals;
        return this;
    }

    @Override
    public MatchAnalysisBuilder advanced(String key, Double value) {
        if (key != null && value != null) {
            advanced.put(key, value);
        }
        return this;
    }

    @Override
    public MatchAnalysis build() {
        if (isBlank(homeTeam) || isBlank(awayTeam)) {
            throw new IllegalStateException("Los equipos local y visitante son obligatorios");
        }
        if (matchDate == null) {
            throw new IllegalStateException("La fecha del partido es obligatoria");
        }
        return new MatchAnalysis(homeTeam,
                awayTeam,
                matchDate,
                season,
                homeGoals,
                awayGoals,
                homeXg,
                awayXg,
                corners,
                yellowCards,
                redCards,
                homeYellowCards,
                awayYellowCards,
                homeRedCards,
                awayRedCards,
                homeMarketValue,
                awayMarketValue,
                homeRecentForm,
                awayRecentForm,
                homeLeaguePosition,
                awayLeaguePosition,
                homeAverageCorners,
                awayAverageCorners,
                homeAverageYellowCards,
                awayAverageYellowCards,
                homeAverageRedCards,
                awayAverageRedCards,
                homeAverageGoalsFor,
                homeAverageGoalsAgainst,
                awayAverageGoalsFor,
                awayAverageGoalsAgainst,
                leagueAverageHomeGoals,
                leagueAverageAwayGoals,
                advanced);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
