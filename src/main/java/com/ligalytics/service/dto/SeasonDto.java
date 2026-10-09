package com.ligalytics.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Temporada de LaLiga disponible en la base de datos.
 */
@Schema(description = "Temporada disponible")
public record SeasonDto(
        @Schema(description = "Año de inicio", example = "2023") int startYear,
        @Schema(description = "Etiqueta legible", example = "2023/2024") String label,
        @Schema(description = "Código de football-data", example = "2324") String code,
        @Schema(description = "Número de partidos cargados de la temporada", example = "380") long matches
) {
}
