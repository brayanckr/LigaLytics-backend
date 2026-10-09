package com.ligalytics.etl;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ligalytics.ai.PredictionTarget;
import com.ligalytics.ai.WekaModelManager;
import com.ligalytics.ai.WekaTrainingService;
import com.ligalytics.patterns.facade.LigaLyticsFacade;
import com.ligalytics.repository.MatchRepository;
import com.ligalytics.service.dto.EtlSeasonResult;

/**
 * Deja el sistema listo la primera vez que arranca: si la tabla de partidos
 * está vacía descarga las temporadas configuradas de football-data.co.uk y, si
 * hay datos pero no hay modelos entrenados, entrena la IA.
 *
 * <p>Corre en un hilo aparte para no retrasar el arranque del servidor. Se
 * controla con {@code ligalytics.etl.auto-load.enabled} (variable de entorno
 * {@code AUTO_LOAD_DATA}).</p>
 */
@Component
@ConditionalOnProperty(name = "ligalytics.etl.auto-load.enabled", havingValue = "true")
public class EtlAutoLoader {

    private static final Logger log = LoggerFactory.getLogger(EtlAutoLoader.class);

    private final LigaLyticsFacade facade;
    private final MatchRepository matchRepository;
    private final WekaTrainingService trainingService;
    private final WekaModelManager modelManager;
    private final List<String> seasons;
    private final String division;
    private final boolean extraSources;
    private final String understatLeague;
    private final String transfermarktPath;

    public EtlAutoLoader(LigaLyticsFacade facade,
            MatchRepository matchRepository,
            WekaTrainingService trainingService,
            WekaModelManager modelManager,
            @Value("${ligalytics.etl.auto-load.seasons:2021,2122,2223,2324,2425,2526}") List<String> seasons,
            @Value("${ligalytics.etl.auto-load.division:SP1}") String division,
            @Value("${ligalytics.etl.auto-load.extra-sources:true}") boolean extraSources,
            @Value("${ligalytics.etl.auto-load.understat-league:La_liga}") String understatLeague,
            @Value("${ligalytics.etl.auto-load.transfermarkt-path:/laliga/startseite/wettbewerb/ES1}") String transfermarktPath) {
        this.facade = facade;
        this.matchRepository = matchRepository;
        this.trainingService = trainingService;
        this.modelManager = modelManager;
        this.seasons = seasons;
        this.division = division;
        this.extraSources = extraSources;
        this.understatLeague = understatLeague;
        this.transfermarktPath = transfermarktPath;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        Thread thread = new Thread(this::run, "etl-auto-load");
        thread.setDaemon(true);
        thread.start();
    }

    /** Fuentes complementarias (xG de Understat y valor de mercado de Transfermarkt): si fallan, se sigue sin ellas. */
    private void loadExtraSources() {
        List<String> years = seasons.stream()
                .map(code -> String.valueOf(2000 + Integer.parseInt(code.substring(0, 2))))
                .toList();
        long understatOk = facade.refreshUnderstat(understatLeague, years).stream()
                .filter(EtlSeasonResult::success).count();
        EtlSeasonResult transfermarkt = facade.refreshTransfermarkt(transfermarktPath);
        log.info("Fuentes complementarias: Understat {} de {} temporadas, Transfermarkt {}",
                understatOk, years.size(), transfermarkt.success() ? "OK" : "no disponible");
    }

    void run() {
        try {
            boolean loaded = false;
            if (matchRepository.count() == 0) {
                log.info("La base de datos no tiene partidos: descargando {} temporadas de football-data ({})",
                        seasons.size(), division);
                List<EtlSeasonResult> results = facade.refreshFootballData(seasons, division);
                long ok = results.stream().filter(EtlSeasonResult::success).count();
                log.info("Carga automática terminada: {} de {} temporadas cargadas, {} partidos en total",
                        ok, results.size(), matchRepository.count());
                if (ok == 0) {
                    log.warn("No se pudo descargar ninguna temporada (¿sin conexión a football-data.co.uk?). "
                            + "Reintenta con POST /api/admin/etl/football-data cuando haya red.");
                }
                loaded = ok > 0;
                if (loaded && extraSources) {
                    loadExtraSources();
                }
            }
            if (!loaded && matchRepository.count() > 0 && !modelManager.exists(PredictionTarget.RESULTADO)) {
                log.info("Hay datos pero no hay modelos entrenados: entrenando la IA");
                trainingService.trainAll();
            }
        } catch (RuntimeException ex) {
            log.error("Falló la carga automática de datos", ex);
        }
    }
}
