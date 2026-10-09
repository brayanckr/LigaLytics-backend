package com.ligalytics.controller;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.ligalytics.fixtures.FixtureDto;
import com.ligalytics.fixtures.FixturesService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Calendario de partidos y resultados en vivo. */
@RestController
@RequestMapping(value = "/fixtures", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Calendario", description = "Próximos partidos, resultados y marcadores en vivo")
public class FixturesController {

    private final FixturesService service;

    public FixturesController(FixturesService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Partidos por fecha", description = "Por defecto, desde hoy hasta dentro de 13 días.")
    public List<FixtureDto> fixtures(
            @Parameter(description = "Fecha inicial (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Fecha final (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Zona horaria, p. ej. America/Bogota")
            @RequestParam(defaultValue = "UTC") String zone) {
        ZoneId zoneId = zone(zone);
        LocalDate start = from != null ? from : LocalDate.now(zoneId);
        LocalDate end = to != null ? to : start.plusDays(13);
        requireConfigured();
        return service.between(start, end, zoneId);
    }

    @GetMapping("/live")
    @Operation(summary = "Partidos en juego ahora")
    public List<FixtureDto> live() {
        requireConfigured();
        return service.live();
    }

    @GetMapping("/status")
    @Operation(summary = "Indica si el proveedor de calendario está configurado")
    public Map<String, Object> status() {
        return Map.of("configured", service.isConfigured(), "provider", service.providerName());
    }

    private void requireConfigured() {
        if (!service.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Calendario no configurado: define la variable FOOTBALL_DATA_ORG_KEY con tu clave de football-data.org");
        }
    }

    private static ZoneId zone(String value) {
        try {
            return ZoneId.of(value);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("Zona horaria inválida: " + value);
        }
    }
}
