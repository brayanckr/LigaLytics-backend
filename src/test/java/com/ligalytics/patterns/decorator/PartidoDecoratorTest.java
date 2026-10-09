package com.ligalytics.patterns.decorator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PartidoDecoratorTest {

    private PartidoBase partidoBase() {
        return new PartidoBase(1L, "Real Madrid", "FC Barcelona",
                LocalDateTime.of(2025, 3, 1, 21, 0), 2, 1);
    }

    @Test
    void baseContainsOnlyResultData() {
        PartidoComponent partido = partidoBase();
        Map<String, Object> datos = partido.datos();

        assertEquals("Real Madrid", datos.get("local"));
        assertEquals(2, ((Number) datos.get("golesLocal")).intValue());
        assertFalse(datos.containsKey("xgLocal"));
        assertFalse(datos.containsKey("posesionLocal"));
        assertFalse(datos.containsKey("valorPlantillaLocal"));
    }

    @Test
    void decoratorsEnrichTheBaseDynamically() {
        PartidoComponent partido = new TransfermarktDecorator(
                new FBrefDecorator(
                        new UnderstatDecorator(partidoBase(), 1.8, 1.2),
                        60.0, 40.0, 8.5, 11.2),
                new BigDecimal("1200000000"), new BigDecimal("900000000"));

        Map<String, Object> datos = partido.datos();
        assertEquals(1.8, ((Number) datos.get("xgLocal")).doubleValue(), 1e-9);
        assertEquals(60.0, ((Number) datos.get("posesionLocal")).doubleValue(), 1e-9);
        assertEquals(11.2, ((Number) datos.get("presionVisitante")).doubleValue(), 1e-9);
        assertEquals(new BigDecimal("1200000000"), datos.get("valorPlantillaLocal"));

        assertTrue(partido.descripcion().contains("xG"));
        assertTrue(partido.descripcion().contains("posesión"));
        assertTrue(partido.descripcion().contains("valor"));
    }

    @Test
    void decoratingDoesNotMutateTheBaseComponent() {
        PartidoBase base = partidoBase();
        PartidoComponent decorated = new UnderstatDecorator(base, 1.8, 1.2);

        assertFalse(base.datos().containsKey("xgLocal"));
        assertTrue(decorated.datos().containsKey("xgLocal"));
        assertEquals("Real Madrid", decorated.datos().get("local"));
    }

    @Test
    void decoratorOrderIsInterchangeable() {
        PartidoComponent first = new TransfermarktDecorator(partidoBase(),
                new BigDecimal("1200000000"), new BigDecimal("900000000"));
        PartidoComponent second = new UnderstatDecorator(first, 1.8, 1.2);

        assertTrue(second.datos().containsKey("valorPlantillaLocal"));
        assertTrue(second.datos().containsKey("xgLocal"));
        assertEquals("FC Barcelona", second.datos().get("visitante"));
    }
}
