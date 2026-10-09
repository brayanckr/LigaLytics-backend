package com.ligalytics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ligalytics.service.StandingsHeap.Standing;

class StandingsHeapTest {

    private final List<Standing> table = List.of(
            new Standing(1L, "Sevilla", 40, 5, 30),
            new Standing(2L, "Barcelona", 60, 25, 55),
            new Standing(3L, "Real Madrid", 60, 25, 55),
            new Standing(4L, "Girona", 60, 30, 50),
            new Standing(5L, "Getafe", 20, -10, 15));

    @Test
    void ranksByPointsThenGoalDifferenceThenGoalsThenName() {
        List<Standing> ranked = StandingsHeap.rank(table);

        assertEquals(List.of("Girona", "Barcelona", "Real Madrid", "Sevilla", "Getafe"),
                ranked.stream().map(Standing::teamName).toList());
    }

    @Test
    void topKDoesNotNeedTheWholeOrdering() {
        List<Standing> top = new StandingsHeap(table).pollTop(2);

        assertEquals(List.of("Girona", "Barcelona"), top.stream().map(Standing::teamName).toList());
    }

    @Test
    void positionsStartAtOne() {
        Map<Long, Integer> positions = StandingsHeap.positions(table);

        assertEquals(1, positions.get(4L));
        assertEquals(5, positions.get(5L));
    }
}
