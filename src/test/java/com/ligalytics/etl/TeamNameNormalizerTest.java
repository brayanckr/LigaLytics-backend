package com.ligalytics.etl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class TeamNameNormalizerTest {

    @Test
    void unifiesFootballDataNames() {
        assertEquals("Atletico Madrid", TeamNameNormalizer.canonical("Ath Madrid"));
        assertEquals("Athletic Bilbao", TeamNameNormalizer.canonical("Ath Bilbao"));
        assertEquals("Real Sociedad", TeamNameNormalizer.canonical("Sociedad"));
        assertEquals("Real Betis", TeamNameNormalizer.canonical("Betis"));
        assertEquals("Rayo Vallecano", TeamNameNormalizer.canonical("Vallecano"));
        assertEquals("Espanyol", TeamNameNormalizer.canonical("Espanol"));
        assertEquals("Celta Vigo", TeamNameNormalizer.canonical("Celta"));
    }

    @Test
    void unifiesUnderstatAndTransfermarktNames() {
        assertEquals("Atletico Madrid", TeamNameNormalizer.canonical("Atlético de Madrid"));
        assertEquals("Espanyol", TeamNameNormalizer.canonical("RCD Espanyol Barcelona"));
        assertEquals("Alaves", TeamNameNormalizer.canonical("Deportivo Alavés"));
        assertEquals("Real Betis", TeamNameNormalizer.canonical("Real Betis Balompié"));
        assertEquals("Barcelona", TeamNameNormalizer.canonical("FC Barcelona"));
    }

    @Test
    void ignoresCaseAndExtraSpaces() {
        assertEquals("Atletico Madrid", TeamNameNormalizer.canonical("  ATH   madrid "));
        assertEquals("Real Madrid", TeamNameNormalizer.canonical("real madrid"));
    }

    @Test
    void keepsUnknownNamesAndHandlesNull() {
        assertEquals("Equipo Desconocido", TeamNameNormalizer.canonical("  Equipo   Desconocido "));
        assertNull(TeamNameNormalizer.canonical(null));
    }
}
