package com.ligalytics.etl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import com.ligalytics.etl.dto.EtlSummary;
import com.ligalytics.repository.MatchRepository;
import com.ligalytics.repository.TeamRepository;
import com.ligalytics.service.dto.SeedReport;

/**
 * Cargador de datos locales (Seeder). Lee las fixtures CSV/HTML incluidas en
 * {@code classpath:fixtures} y puebla PostgreSQL con los 20 equipos de LaLiga y
 * sus partidos, sin depender de la red. <b>Son datos sintéticos de ejemplo (no
 * resultados reales)</b>: sirven para desarrollar sin conexión; los datos reales
 * se cargan con el ETL de football-data.
 *
 * <p>Se puede ejecutar al arrancar el backend
 * ({@code ligalytics.seed.on-startup=true}) o manualmente mediante el endpoint
 * {@code POST /api/admin/seed}. La carga es idempotente: los partidos ya
 * existentes se omiten y los equipos se reutilizan por nombre canónico.</p>
 */
@Service
public class DataSeederService {

    private static final Logger log = LoggerFactory.getLogger(DataSeederService.class);

    static final String FOOTBALL_DATA_FIXTURE = "classpath:fixtures/football-data-sp1-2324.csv";
    static final String TRANSFERMARKT_FIXTURE = "classpath:fixtures/transfermarkt-sp1-2324.html";
    static final String UNDERSTAT_FIXTURE = "classpath:fixtures/understat-la-liga-2324.html";

    private static final String SEASON = "2324";
    private static final String REFERENCE = "SP1-" + SEASON;

    private final EtlService etlService;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final ResourceLoader resourceLoader;

    @Value("${ligalytics.seed.on-startup:false}")
    private boolean seedOnStartup;

    public DataSeederService(EtlService etlService,
            TeamRepository teamRepository,
            MatchRepository matchRepository,
            ResourceLoader resourceLoader) {
        this.etlService = etlService;
        this.teamRepository = teamRepository;
        this.matchRepository = matchRepository;
        this.resourceLoader = resourceLoader;
    }

    /**
     * Sembrado automático al arrancar el backend si
     * {@code ligalytics.seed.on-startup} es {@code true}.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void seedOnStartup() {
        if (!seedOnStartup) {
            return;
        }
        try {
            SeedReport report = seed();
            log.info("Sembrado local completado: {} equipos, {} partidos", report.teams(), report.matches());
        } catch (RuntimeException ex) {
            log.error("Falló el sembrado automático de datos locales", ex);
        }
    }

    /**
     * Carga todas las fixtures locales. Cada fuente se procesa de forma
     * independiente: si una falla, las demás continúan.
     */
    public SeedReport seed() {
        List<SeedReport.SourceResult> results = new ArrayList<>();
        results.add(runSource("football-data", FOOTBALL_DATA_FIXTURE,
                content -> etlService.ingestLocalFootballData(content, REFERENCE, SEASON)));
        results.add(runSource("transfermarkt", TRANSFERMARKT_FIXTURE,
                content -> etlService.ingestLocalTransfermarkt(content, REFERENCE)));
        results.add(runSource("understat", UNDERSTAT_FIXTURE,
                content -> etlService.ingestLocalUnderstat(content, REFERENCE, SEASON)));

        SeedReport report = new SeedReport(Instant.now(), teamRepository.count(), matchRepository.count(), results);
        log.info("Sembrado local: {} equipos, {} partidos, {} fuentes", report.teams(), report.matches(),
                results.size());
        return report;
    }

    private SeedReport.SourceResult runSource(String source,
            String location,
            Function<String, EtlSummary> ingester) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            log.warn("Fixture no encontrada para '{}': {}", source, location);
            return SeedReport.SourceResult.failed(source, "Fixture no encontrada: " + location);
        }
        try {
            EtlSummary summary = ingester.apply(read(resource));
            log.info("Fuente '{}' cargada: {} creados, {} actualizados, {} omitidos", source, summary.created(),
                    summary.updated(), summary.skipped());
            return SeedReport.SourceResult.ok(source, summary);
        } catch (RuntimeException ex) {
            log.error("Fallo al cargar la fuente '{}'", source, ex);
            return SeedReport.SourceResult.failed(source, ex.getMessage());
        }
    }

    private String read(Resource resource) {
        try (InputStream input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo leer la fixture " + resource.getDescription(), ex);
        }
    }
}
