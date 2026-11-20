package com.ligalytics.etl;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ligalytics.etl.dto.EtlSummary;
import com.ligalytics.service.SeasonUtil;

/**
 * Completa las estadísticas (córneres, tarjetas, tiros, faltas) de los partidos de la temporada en curso con el CSV
 * gratuito de football-data.co.uk. Los partidos de la temporada actual llegan del calendario solo con el marcador;
 * este CSV los publica con unos días de retraso, así que se revisa cada pocas horas y solo se rellena lo que falta.
 * Desactivable con {@code ligalytics.etl.stats-sync=false}.
 */
@Component
public class FootballDataStatsSync {

    private static final Logger log = LoggerFactory.getLogger(FootballDataStatsSync.class);

    private final EtlService etlService;
    private final boolean enabled;
    private final String division;

    public FootballDataStatsSync(EtlService etlService,
            @Value("${ligalytics.etl.stats-sync:true}") boolean enabled,
            @Value("${ligalytics.etl.auto-load.division:SP1}") String division) {
        this.etlService = etlService;
        this.enabled = enabled;
        this.division = division;
    }

    /** Cada 6 horas por defecto, empezando 2 minutos después de arrancar (tras la sincronización del calendario). */
    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "${ligalytics.etl.stats-sync-interval:PT6H}")
    public void scheduled() {
        if (!enabled) {
            return;
        }
        try {
            sync();
        } catch (RuntimeException ex) {
            log.warn("No se pudieron completar las estadisticas de la temporada actual: {}", ex.getMessage());
        }
    }

    /** Descarga el CSV de la temporada actual y completa los partidos guardados a los que les faltan datos. */
    public EtlSummary sync() {
        String season = SeasonUtil.code(SeasonUtil.startYear(LocalDateTime.now()));
        EtlSummary summary = etlService.completeFootballDataStats(season, division);
        log.info("Estadisticas de la temporada {}: {} partidos completados, {} omitidos", season, summary.updated(),
                summary.skipped());
        return summary;
    }
}
