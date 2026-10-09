package com.ligalytics.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ligalytics.patterns.facade.LigaLyticsFacade;
import com.ligalytics.service.dto.TeamDto;
import com.ligalytics.service.dto.TeamStatsDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * API REST de equipos: listado, búsqueda y estadísticas.
 */
@RestController
@RequestMapping(value = "/teams", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Equipos", description = "Consulta y estadísticas de los equipos de LaLiga")
public class TeamController {

    private final LigaLyticsFacade facade;

    public TeamController(LigaLyticsFacade facade) {
        this.facade = facade;
    }

    @GetMapping
    @Operation(summary = "Lista y busca equipos", description = "Devuelve los equipos de LaLiga; admite búsqueda por nombre.")
    @ApiResponse(responseCode = "200", description = "Listado de equipos")
    public List<TeamDto> listTeams(
            @Parameter(description = "Texto de búsqueda por nombre") @RequestParam(name = "q", required = false) String query) {
        return facade.listTeams(query);
    }

    @GetMapping("/h2h")
    @Operation(summary = "Últimos enfrentamientos directos entre dos equipos",
            description = "Partidos jugados entre ambos en cualquier sede, del más reciente al más antiguo, con resumen.")
    @ApiResponse(responseCode = "200", description = "Enfrentamientos directos")
    @ApiResponse(responseCode = "404", description = "Alguno de los equipos no existe")
    public com.ligalytics.service.dto.HeadToHeadDto headToHead(
            @RequestParam("homeId") Long homeId,
            @RequestParam("awayId") Long awayId,
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return facade.headToHead(homeId, awayId, limit);
    }

    @GetMapping("/{id}/stats")
    @Operation(summary = "Estadísticas e historial de un equipo",
            description = "Por defecto de la temporada más reciente en la que jugó; use season=2023 para 2023/24.")
    @ApiResponse(responseCode = "200", description = "Estadísticas avanzadas e historial del equipo")
    @ApiResponse(responseCode = "404", description = "Equipo no encontrado")
    public TeamStatsDto getTeamStats(@PathVariable Long id,
            @Parameter(description = "Año de inicio de la temporada (2023 = 2023/24)")
            @RequestParam(name = "season", required = false) Integer season) {
        return facade.getTeamStats(id, season);
    }
}
