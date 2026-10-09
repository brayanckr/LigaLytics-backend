package com.ligalytics.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligalytics.ai.TrainingReport;
import com.ligalytics.ai.TrainingReportHolder;
import com.ligalytics.exception.ResourceNotFoundException;
import com.ligalytics.model.PredictionRecord;
import com.ligalytics.patterns.facade.LigaLyticsFacade;
import com.ligalytics.service.PredictionLogService;
import com.ligalytics.service.dto.SeasonDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Información pública sobre los datos y el modelo: temporadas disponibles,
 * métricas de validación del modelo de IA e historial de predicciones.
 */
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Modelo y datos", description = "Temporadas, métricas del modelo de IA e historial de predicciones")
public class ModelController {

    private final LigaLyticsFacade facade;
    private final TrainingReportHolder reportHolder;
    private final PredictionLogService predictionLogService;

    public ModelController(LigaLyticsFacade facade,
            TrainingReportHolder reportHolder,
            PredictionLogService predictionLogService) {
        this.facade = facade;
        this.reportHolder = reportHolder;
        this.predictionLogService = predictionLogService;
    }

    @GetMapping("/seasons")
    @Operation(summary = "Temporadas con datos", description = "De la más reciente a la más antigua, con el número de partidos.")
    @ApiResponse(responseCode = "200", description = "Temporadas disponibles")
    public List<SeasonDto> seasons() {
        return facade.seasons();
    }

    @GetMapping("/model/report")
    @Operation(summary = "Métricas de validación del modelo de IA",
            description = "Accuracy del resultado y MAE de goles, córneres y tarjetas sobre la temporada de prueba, "
                    + "comparados con un modelo trivial.")
    @ApiResponse(responseCode = "200", description = "Último informe de entrenamiento")
    @ApiResponse(responseCode = "404", description = "Todavía no se ha entrenado el modelo")
    public TrainingReport report() {
        return reportHolder.latest()
                .orElseThrow(() -> new ResourceNotFoundException("Todavía no se ha entrenado el modelo de IA"));
    }

    @GetMapping("/predict/history")
    @Operation(summary = "Últimas predicciones generadas", description = "Las 20 más recientes guardadas en la base de datos.")
    @ApiResponse(responseCode = "200", description = "Historial de predicciones")
    public List<PredictionRecord> predictionHistory() {
        return predictionLogService.latest();
    }
}
