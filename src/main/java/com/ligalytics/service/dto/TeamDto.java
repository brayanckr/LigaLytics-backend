package com.ligalytics.service.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Vista resumida de un equipo de LaLiga expuesta por la API REST.
 */
@Schema(description = "Equipo de LaLiga")
public record TeamDto(
        @Schema(description = "Identificador del equipo", example = "1") Long id,
        @Schema(description = "Nombre del equipo", example = "Real Madrid") String name,
        @Schema(description = "Estadio", example = "Santiago Bernabéu") String stadium,
        @Schema(description = "Valor de mercado de la plantilla (€)", example = "1200000000") BigDecimal marketValue
) {
}
