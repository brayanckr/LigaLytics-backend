package com.ligalytics.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ligalytics.patterns.facade.LigaLyticsFacade;
import com.ligalytics.service.dto.RankingEntryDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * API REST de la clasificación de LaLiga.
 */
@RestController
@RequestMapping(value = "/ranking", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Clasificación", description = "Tabla de posiciones y rendimiento de los equipos")
public class RankingController {

    private final LigaLyticsFacade facade;

    public RankingController(LigaLyticsFacade facade) {
        this.facade = facade;
    }

    @GetMapping
    @Operation(summary = "Tabla de posiciones", description = "Devuelve la clasificación con el rendimiento de cada equipo.")
    @ApiResponse(responseCode = "200", description = "Clasificación calculada")
    public List<RankingEntryDto> ranking(
            @Parameter(description = "Año de inicio de la temporada (2023 = 2023/24); por defecto la más reciente")
            @RequestParam(name = "season", required = false) Integer season) {
        return facade.ranking(season);
    }
}
