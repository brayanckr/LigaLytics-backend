package com.ligalytics.etl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.ligalytics.etl.dto.RawMatch;
import com.ligalytics.model.Match;

class StatsCompletionTest {

    private static RawMatch raw(Integer corners, Integer yellow, Integer red, Integer homeShots, Integer homeFouls) {
        return new RawMatch("SP1", LocalDateTime.of(2026, 11, 8, 21, 0), "Celta Vigo", "Levante", 0, 0, homeShots, 6,
                4, 2, null, null, corners, yellow, red, 2, 1, 0, 0, homeFouls, 11);
    }

    @Test
    void fillsOnlyTheMissingFieldsAndNeverOverwritesExistingOnes() {
        Match match = Match.builder().corners(9).build(); // ya tenía córneres, de otra fuente

        boolean changed = EtlService.fillMissingStats(match, raw(7, 3, 0, 14, 12));

        assertTrue(changed);
        assertEquals(9, match.getCorners()); // no se pisa
        assertEquals(3, match.getYellowCards());
        assertEquals(0, match.getRedCards());
        assertEquals(14, match.getHomeShots());
        assertEquals(12, match.getHomeFouls());
        assertEquals(2, match.getHomeYellowCards());
        assertEquals(1, match.getAwayYellowCards());
    }

    @Test
    void reportsNoChangeWhenThereIsNothingToFillOrTheSourceHasNoData() {
        Match empty = Match.builder().build();
        assertFalse(EtlService.fillMissingStats(empty, new RawMatch("SP1", LocalDateTime.now(), "A", "B", 1, 0,
                null, null, null, null, null, null, null, null, null)));
        assertNull(empty.getCorners());

        Match full = Match.builder().corners(1).yellowCards(2).redCards(0).homeYellowCards(1).awayYellowCards(1)
                .homeRedCards(0).awayRedCards(0).homeShots(5).awayShots(5).homeShotsOnTarget(2).awayShotsOnTarget(2)
                .homeFouls(9).awayFouls(9).build();
        assertFalse(EtlService.fillMissingStats(full, raw(7, 3, 1, 14, 12)));
        assertEquals(1, full.getCorners());
    }
}
