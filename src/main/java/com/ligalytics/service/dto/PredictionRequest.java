package com.ligalytics.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Cuerpo de la petición POST /predict.
 */
@Schema(description = "Petición de predicción entre dos equipos")
public record PredictionRequest(
        @NotNull @Positive
        @Schema(description = "Identificador del equipo local", example = "1") Long homeTeamId,
        @NotNull @Positive
        @Schema(description = "Identificador del equipo visitante", example = "2") Long awayTeamId
) {
}
