package com.ligalytics.etl;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ligalytics.etl.dto.EtlSummary;
import com.ligalytics.fixtures.Fixture;
import com.ligalytics.fixtures.FixturesProvider;

/**
 * Mantiene al dia la temporada en curso: cada pocas horas (y al arrancar) descarga el calendario de
 * LaLiga de football-data.org y guarda en la base de datos los partidos ya finalizados que falten. Cada
 * ingesta notifica a los observadores (estadisticas, cache y reentrenamiento). Desactivable con
 * {@code ligalytics.etl.current-season-sync=false}; sin clave de football-data.org no hace nada.
 */
@Component
@EnableScheduling
public class CurrentSeasonSync {

    private static final Logger log = LoggerFactory.getLogger(CurrentSeasonSync.class);

    private final FixturesProvider provider;
    private final EtlService etlService;
    private final boolean enabled;

    public CurrentSeasonSync(FixturesProvider provider, EtlService etlService,
            @Value("${ligalytics.etl.current-season-sync:true}") boolean enabled) {
        this.provider = provider;
        this.etlService = etlService;
        this.enabled = enabled;
    }

    /** Cada 6 horas, empezando 30 s despues de arrancar. */
    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "PT6H")
    public void scheduled() {
        if (!enabled) {
            return;
        }
        try {
            sync();
        } catch (RuntimeException ex) {
            log.warn("No se pudo sincronizar la temporada actual: {}", ex.getMessage());
        }
    }

    /** Descarga el calendario y guarda los partidos finalizados que falten. */
    public EtlSummary sync() {
        if (!provider.isConfigured()) {
            throw new IllegalStateException("Calendario no configurado: define FOOTBALL_DATA_ORG_KEY");
        }
        List<Fixture> fixtures = provider.fetchSeason();
        EtlSummary summary = etlService.ingestFinishedFixtures(fixtures);
        log.info("Temporada actual: {} partidos nuevos, {} omitidos", summary.created(), summary.skipped());
        return summary;
    }
}
