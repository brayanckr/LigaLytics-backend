package com.ligalytics.service.dto;

import com.ligalytics.etl.dto.EtlSummary;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resultado de la carga de una temporada desde una fuente externa. Si la carga
 * falla, {@code success} es {@code false} y {@code error} explica el motivo;
 * el resto de temporadas se siguen procesando.
 */
@Schema(description = "Resultado de la carga de una temporada")
public record EtlSeasonResult(
        @Schema(description = "Código de temporada de football-data", example = "2324") String season,
        @Schema(description = "Indica si la carga terminó sin errores") boolean success,
        @Schema(description = "Resumen de la ingesta (nulo si falló)") EtlSummary summary,
        @Schema(description = "Mensaje de error (nulo si tuvo éxito)") String error
) {

    public static EtlSeasonResult ok(String season, EtlSummary summary) {
        return new EtlSeasonResult(season, true, summary, null);
    }

    public static EtlSeasonResult failed(String season, String error) {
        return new EtlSeasonResult(season, false, null, error);
    }
}
