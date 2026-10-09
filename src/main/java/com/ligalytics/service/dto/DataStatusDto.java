package com.ligalytics.service.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Estado de los datos cargados en la base de datos. Sirve para comprobar de un
 * vistazo si el ETL ya pobló la BD.
 */
@Schema(description = "Resumen de los datos cargados")
public record DataStatusDto(
        @Schema(description = "Número de equipos", example = "28") long teams,
        @Schema(description = "Número de partidos", example = "2280") long matches,
        @Schema(description = "Fecha del partido más antiguo (nulo si no hay datos)") LocalDateTime firstMatch,
        @Schema(description = "Fecha del partido más reciente (nulo si no hay datos)") LocalDateTime lastMatch
) {
}
