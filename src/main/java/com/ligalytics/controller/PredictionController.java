package com.ligalytics.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligalytics.patterns.facade.LigaLyticsFacade;
import com.ligalytics.service.dto.PredictionRequest;
import com.ligalytics.service.dto.PredictionResponseDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * API REST de predicciones.
 */
@RestController
@RequestMapping(value = "/predict", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Predicciones", description = "Predicción de resultados, goles, córneres y tarjetas")
public class PredictionController {

    private final LigaLyticsFacade facade;

    public PredictionController(LigaLyticsFacade facade) {
        this.facade = facade;
    }

    @PostMapping
    @Operation(summary = "Predice un partido", description = "Recibe los identificadores de equipo local y visitante y devuelve la predicción completa con probabilidades.")
    @ApiResponse(responseCode = "200", description = "Predicción generada")
    @ApiResponse(responseCode = "400", description = "Petición inválida")
    @ApiResponse(responseCode = "404", description = "Alguno de los equipos no existe")
    public PredictionResponseDto predict(@Valid @RequestBody PredictionRequest request) {
        return facade.predict(request.homeTeamId(), request.awayTeamId());
    }
}
