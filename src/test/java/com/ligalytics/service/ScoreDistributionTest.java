package com.ligalytics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScoreDistributionTest {

    @Test
    void matrixMatchesTheWinnerProbabilityDifference() {
        // Real Madrid - Villarreal según Football Charts: 64 / 21 / 15
        ScoreDistribution distribution = ScoreDistribution.calibrate(2.9, 0.64, 0.15);

        assertEquals(0.64 - 0.15, distribution.homeWin() - distribution.awayWin(), 0.005);
        assertEquals(1.0, distribution.homeWin() + distribution.draw() + distribution.awayWin(), 1e-9);
        assertEquals(2.9, distribution.homeLambda() + distribution.awayLambda(), 1e-6);
        assertTrue(distribution.homeLambda() > distribution.awayLambda());
    }

    @Test
    void headlineScoreAgreesWithThePredictedWinner() {
        ScoreDistribution distribution = ScoreDistribution.calibrate(2.9, 0.64, 0.15);

        String score = distribution.mostLikelyScore("HOME_WIN");
        String[] goals = score.split("-");

        assertTrue(Integer.parseInt(goals[0]) > Integer.parseInt(goals[1]), score);
    }

    @Test
    void awayFavouriteGetsAnAwayWinScore() {
        // Alavés - Atlético: 26 / 24 / 50
        ScoreDistribution distribution = ScoreDistribution.calibrate(2.4, 0.26, 0.50);
        String[] goals = distribution.mostLikelyScore("AWAY_WIN").split("-");

        assertTrue(Integer.parseInt(goals[0]) < Integer.parseInt(goals[1]));
        assertTrue(distribution.awayLambda() > distribution.homeLambda());
    }

    @Test
    void balancedMatchStaysBalanced() {
        ScoreDistribution distribution = ScoreDistribution.calibrate(2.5, 0.34, 0.31);

        assertEquals(0.03, distribution.homeWin() - distribution.awayWin(), 0.005);
        assertEquals(distribution.homeLambda(), distribution.awayLambda(), 0.2);
    }

    @Test
    void extremeFavouriteAndDerivedMarketsAreConsistent() {
        ScoreDistribution distribution = ScoreDistribution.calibrate(2.9, 0.87, 0.01);

        assertEquals(0.86, distribution.homeWin() - distribution.awayWin(), 0.01);
        double over = distribution.over25();
        assertTrue(over > 0.4 && over < 0.9, "over2.5=" + over);
        assertTrue(distribution.bothTeamsScore() < 0.6);
        assertEquals(5, distribution.topScores(5).size());
        assertTrue(distribution.topScores(5).get(0).probability() >= distribution.topScores(5).get(4).probability());
    }
}
