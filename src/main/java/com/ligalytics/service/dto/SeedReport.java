package com.ligalytics.service.dto;

import java.time.Instant;
import java.util.List;

import com.ligalytics.etl.dto.EtlSummary;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resultado de la carga de datos locales (fixtures). Resume cuántos equipos y
 * partidos quedaron en la base de datos y el detalle de cada fuente.
 */
@Schema(description = "Resultado del sembrado de datos locales")
public record SeedReport(
        Instant seededAt,
        @Schema(description = "Equipos en la base de datos tras el sembrado") long teams,
        @Schema(description = "Partidos en la base de datos tras el sembrado") long matches,
        List<SourceResult> sources
) {

    /**
     * Resultado de procesar una fixture concreta.
     */
    @Schema(description = "Detalle de una fuente de datos")
    public record SourceResult(
            String source,
            boolean success,
            long processed,
            String message) {

        public static SourceResult ok(String source, EtlSummary summary) {
            return new SourceResult(source, true, summary.created() + summary.updated(), "Cargado correctamente");
        }

        public static SourceResult failed(String source, String message) {
            return new SourceResult(source, false, 0, message);
        }
    }
}
