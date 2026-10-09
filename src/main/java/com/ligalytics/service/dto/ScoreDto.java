package com.ligalytics.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Marcador con su probabilidad. */
@Schema(description = "Marcador probable")
public record ScoreDto(
        @Schema(description = "Marcador local-visitante", example = "2-1") String score,
        @Schema(description = "Probabilidad del marcador (0-1)") double probability) {
}
