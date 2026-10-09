package com.ligalytics.patterns.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class MatchAnalysisBuilderTest {

    private static final LocalDateTime KICK_OFF = LocalDateTime.of(2025, 3, 1, 21, 0);

    @Test
    void buildsCompleteAnalysisWithAllFields() {
        MatchAnalysis analysis = MatchAnalysis.builder()
                .teams("Real Madrid", "FC Barcelona")
                .matchDate(KICK_OFF)
                .season("2024/2025")
                .score(2, 1)
                .expectedGoals(1.8, 1.2)
                .corners(11)
                .cards(3, 1)
                .marketValues(new BigDecimal("1200000000"), new BigDecimal("900000000"))
                .recentForm("WWDLW", "WDWLW")
                .leaguePositions(1, 3)
                .averageCorners(5.5, 4.5)
                .averageYellowCards(2.0, 2.5)
                .build();

        assertEquals("Real Madrid", analysis.getHomeTeam());
        assertEquals("FC Barcelona", analysis.getAwayTeam());
        assertEquals(KICK_OFF, analysis.getMatchDate());
        assertEquals("2024/2025", analysis.getSeason());
        assertEquals(2, analysis.getHomeGoals().intValue());
        assertEquals(1, analysis.getAwayGoals().intValue());
        assertEquals(1.8, analysis.getHomeXg().doubleValue(), 1e-9);
        assertEquals(1.2, analysis.getAwayXg().doubleValue(), 1e-9);
        assertEquals(11, analysis.getCorners().intValue());
        assertEquals(3, analysis.getYellowCards().intValue());
        assertEquals(1, analysis.getRedCards().intValue());
        assertEquals(new BigDecimal("1200000000"), analysis.getHomeMarketValue());
        assertEquals(new BigDecimal("900000000"), analysis.getAwayMarketValue());
        assertEquals("WWDLW", analysis.getHomeRecentForm());
        assertEquals("WDWLW", analysis.getAwayRecentForm());
        assertEquals(1, analysis.getHomeLeaguePosition().intValue());
        assertEquals(3, analysis.getAwayLeaguePosition().intValue());
        assertEquals(5.5, analysis.getHomeAverageCorners().doubleValue(), 1e-9);
        assertEquals(4.5, analysis.getAwayAverageCorners().doubleValue(), 1e-9);
        assertEquals(2.0, analysis.getHomeAverageYellowCards().doubleValue(), 1e-9);
        assertEquals(2.5, analysis.getAwayAverageYellowCards().doubleValue(), 1e-9);
        assertTrue(analysis.hasExpectedGoals());
        assertEquals(3.0, analysis.totalExpectedGoals(), 1e-9);
    }

    @Test
    void optionalFieldsDefaultToNull() {
        MatchAnalysis analysis = MatchAnalysis.builder()
                .teams("Getafe", "Cádiz")
                .matchDate(KICK_OFF)
                .build();

        assertNull(analysis.getSeason());
        assertNull(analysis.getHomeGoals());
        assertNull(analysis.getAwayGoals());
        assertNull(analysis.getHomeXg());
        assertNull(analysis.getAwayXg());
        assertNull(analysis.getCorners());
        assertNull(analysis.getYellowCards());
        assertNull(analysis.getRedCards());
        assertNull(analysis.getHomeMarketValue());
        assertNull(analysis.getAwayMarketValue());
        assertNull(analysis.getHomeRecentForm());
        assertNull(analysis.getAwayRecentForm());
        assertNull(analysis.getHomeLeaguePosition());
        assertNull(analysis.getAwayLeaguePosition());
        assertNull(analysis.getHomeAverageCorners());
        assertNull(analysis.getAwayAverageCorners());
        assertNull(analysis.getHomeAverageYellowCards());
        assertNull(analysis.getAwayAverageYellowCards());
        assertEquals(0.0, analysis.totalExpectedGoals());
    }

    @Test
    void failsWhenTeamsAreMissing() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> MatchAnalysis.builder().matchDate(KICK_OFF).build());
        assertTrue(error.getMessage().contains("equipos"));
    }

    @Test
    void failsWhenMatchDateIsMissing() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> MatchAnalysis.builder().teams("Real Madrid", "FC Barcelona").build());
        assertTrue(error.getMessage().contains("fecha"));
    }
}
