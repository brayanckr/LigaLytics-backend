package com.ligalytics.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligalytics.service.MatchDetailService;
import com.ligalytics.service.dto.MatchDetailDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Detalle y estadísticas de un partido, jugado o en vivo. */
@RestController
@RequestMapping(value = "/matches", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Partidos", description = "Estadísticas de un partido jugado o en vivo")
public class MatchController {

    private final MatchDetailService service;

    public MatchController(MatchDetailService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Partido jugado", description = "Resultado y estadísticas guardadas de un partido de la base de datos.")
    @ApiResponse(responseCode = "200", description = "Detalle del partido")
    @ApiResponse(responseCode = "404", description = "Partido no encontrado")
    public MatchDetailDto played(@PathVariable long id) {
        return service.played(id);
    }

    @GetMapping("/event/{eventId}")
    @Operation(summary = "Partido en vivo o reciente",
            description = "Marcador, minuto y estadísticas del proveedor en vivo (caché de 20 s).")
    @ApiResponse(responseCode = "200", description = "Detalle del partido")
    @ApiResponse(responseCode = "404", description = "Partido no encontrado")
    @ApiResponse(responseCode = "503", description = "Proveedor en vivo no disponible")
    public MatchDetailDto live(@PathVariable long eventId) {
        return service.live(eventId);
    }
}
