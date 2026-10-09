package com.ligalytics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class SeasonUtilTest {

    @Test
    void seasonStartsInJuly() {
        assertEquals(2023, SeasonUtil.startYear(LocalDateTime.of(2023, 8, 12, 21, 0)));
        assertEquals(2023, SeasonUtil.startYear(LocalDateTime.of(2024, 5, 26, 21, 0)));
        assertEquals(2024, SeasonUtil.startYear(LocalDateTime.of(2024, 8, 15, 21, 0)));
    }

    @Test
    void formatsLabelsAndCodes() {
        assertEquals("2023/2024", SeasonUtil.label(2023));
        assertEquals("2324", SeasonUtil.code(2023));
        assertEquals("9900", SeasonUtil.code(1999));
        assertEquals(2023, SeasonUtil.startYearFromCode("2324"));
    }
}
