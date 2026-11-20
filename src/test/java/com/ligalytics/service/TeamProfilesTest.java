package com.ligalytics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class TeamProfilesTest {

    @Test
    void findsTheProfileByCanonicalNameOrAlias() {
        TeamProfiles.Profile madrid = TeamProfiles.of("Real Madrid").orElseThrow();
        assertEquals("Santiago Bernabéu", madrid.stadium());
        assertEquals(0, madrid.marketValue().compareTo(new BigDecimal("1500000000")));
        assertTrue(madrid.crest().startsWith("https://"));

        // Variantes de otras fuentes llegan a la misma ficha.
        assertEquals("Spotify Camp Nou", TeamProfiles.of("FC Barcelona").orElseThrow().stadium());
        assertEquals("Riyadh Air Metropolitano", TeamProfiles.of("Atlético de Madrid").orElseThrow().stadium());
    }

    @Test
    void unknownTeamsHaveNoProfile() {
        assertTrue(TeamProfiles.of("Equipo Inventado").isEmpty());
        assertTrue(TeamProfiles.of(null).isEmpty());
    }
}
