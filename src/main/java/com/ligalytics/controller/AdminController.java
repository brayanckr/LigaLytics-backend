package com.ligalytics.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ligalytics.ai.TrainingReport;
import com.ligalytics.ai.WekaTrainingService;
import com.ligalytics.etl.DataSeederService;
import com.ligalytics.exception.UnauthorizedException;
import com.ligalytics.patterns.facade.LigaLyticsFacade;
import com.ligalytics.service.dto.DataStatusDto;
import com.ligalytics.service.dto.EtlSeasonResult;
import com.ligalytics.service.dto.SeedReport;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * API REST de administración: permite forzar la actualización de datos desde
 * las fuentes externas y comprobar qué hay cargado en la base de datos.
 *
 * <p>Si se define {@code ligalytics.admin.api-key} (variable de entorno
 * {@code ADMIN_API_KEY}), todas las peticiones deben enviar esa clave en la
 * cabecera {@code X-Admin-Key}. Si no se define, los endpoints quedan abiertos
 * (cómodo en desarrollo; en producción conviene definirla).</p>
 */
@RestController
@RequestMapping(value = "/admin", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Administración", description = "Actualización de datos y estado de la base de datos")
public class AdminController {

    /** Seis temporadas de LaLiga: 2018-19 a 2023-24 (entrenamiento 2018-23, prueba 2023-24). */
    static final List<String> DEFAULT_SEASONS = List.of("2021", "2122", "2223", "2324", "2425", "2526");

    private static final Pattern SEASON_CODE = Pattern.compile("\\d{4}");
    private static final Pattern DIVISION_CODE = Pattern.compile("[A-Z]{1,2}\\d");

    private final LigaLyticsFacade facade;
    private final DataSeederService dataSeederService;
    private final WekaTrainingService trainingService;
    private final com.ligalytics.etl.CurrentSeasonSync currentSeasonSync;
    private final String adminApiKey;

    public AdminController(LigaLyticsFacade facade,
            DataSeederService dataSeederService,
            WekaTrainingService trainingService,
            com.ligalytics.etl.CurrentSeasonSync currentSeasonSync,
            @Value("${ligalytics.admin.api-key:}") String adminApiKey) {
        this.facade = facade;
        this.dataSeederService = dataSeederService;
        this.trainingService = trainingService;
        this.currentSeasonSync = currentSeasonSync;
        this.adminApiKey = adminApiKey == null ? "" : adminApiKey;
    }

    @PostMapping("/etl/football-data")
    @Operation(summary = "Carga datos de football-data.co.uk",
            description = "Descarga e ingiere los CSV de las temporadas indicadas (por defecto 1819 a 2324, "
                    + "división SP1 = LaLiga). Es idempotente: los partidos ya cargados se omiten.")
    @ApiResponse(responseCode = "200", description = "Resultado por temporada (las que fallan se reportan sin detener las demás)")
    @ApiResponse(responseCode = "400", description = "Temporada o división inválida")
    @ApiResponse(responseCode = "401", description = "Clave de administración incorrecta")
    public List<EtlSeasonResult> loadFootballData(
            @RequestHeader(name = "X-Admin-Key", required = false) String apiKey,
            @Parameter(description = "Códigos de temporada separados por coma, p. ej. 2223,2324")
            @RequestParam(name = "seasons", required = false) List<String> seasons,
            @Parameter(description = "Código de división de football-data (SP1 = LaLiga)")
            @RequestParam(name = "division", defaultValue = "SP1") String division) {
        requireAdminKey(apiKey);

        String normalizedDivision = division.trim().toUpperCase(Locale.ROOT);
        if (!DIVISION_CODE.matcher(normalizedDivision).matches()) {
            throw new IllegalArgumentException("División inválida: " + division + " (ejemplo válido: SP1)");
        }
        return facade.refreshFootballData(resolveSeasons(seasons), normalizedDivision);
    }

    @GetMapping("/status")
    @Operation(summary = "Estado de los datos cargados",
            description = "Número de equipos y partidos, y rango de fechas cargado.")
    @ApiResponse(responseCode = "200", description = "Estado actual de la base de datos")
    @ApiResponse(responseCode = "401", description = "Clave de administración incorrecta")
    public DataStatusDto status(@RequestHeader(name = "X-Admin-Key", required = false) String apiKey) {
        requireAdminKey(apiKey);
        return facade.dataStatus();
    }

    @PostMapping("/seed")
    @Operation(summary = "Carga datos locales de EJEMPLO (fixtures sintéticas)",
            description = "Lee las fixtures CSV/HTML incluidas en el backend (datos sintéticos de la temporada 2023-24, "
                    + "NO son resultados reales) para desarrollar sin red. No usar para el entregable: "
                    + "carga los datos reales con POST /admin/etl/football-data. Es idempotente.")
    @ApiResponse(responseCode = "200", description = "Resumen del sembrado (equipos y partidos cargados)")
    @ApiResponse(responseCode = "401", description = "Clave de administración incorrecta")
    public SeedReport seed(@RequestHeader(name = "X-Admin-Key", required = false) String apiKey) {
        requireAdminKey(apiKey);
        return dataSeederService.seed();
    }

    @PostMapping(value = "/etl/football-data/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importa CSV de football-data subidos",
            description = "Acepta uno o varios archivos SP1.csv descargados a mano. La temporada se deduce de las fechas.")
    @ApiResponse(responseCode = "200", description = "Resultado por archivo")
    @ApiResponse(responseCode = "401", description = "Clave de administración incorrecta")
    public List<EtlSeasonResult> uploadFootballData(
            @RequestHeader(name = "X-Admin-Key", required = false) String apiKey,
            @RequestParam("files") List<org.springframework.web.multipart.MultipartFile> files) throws java.io.IOException {
        requireAdminKey(apiKey);
        java.util.Map<String, String> contents = new java.util.LinkedHashMap<>();
        for (var file : files) {
            contents.put(String.valueOf(file.getOriginalFilename()),
                    new String(file.getBytes(), StandardCharsets.ISO_8859_1));
        }
        return facade.importFootballDataCsv(contents);
    }

    @PostMapping("/etl/football-data/folder")
    @Operation(summary = "Importa los CSV de una carpeta del servidor",
            description = "Lee todos los archivos .csv de la carpeta indicada (formato football-data).")
    @ApiResponse(responseCode = "200", description = "Resultado por archivo")
    @ApiResponse(responseCode = "401", description = "Clave de administración incorrecta")
    public List<EtlSeasonResult> importFolder(
            @RequestHeader(name = "X-Admin-Key", required = false) String apiKey,
            @RequestParam("path") String path) throws java.io.IOException {
        requireAdminKey(apiKey);
        java.util.Map<String, String> contents = new java.util.TreeMap<>();
        try (var stream = java.nio.file.Files.list(java.nio.file.Path.of(path))) {
            for (java.nio.file.Path file : stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv")).toList()) {
                contents.put(file.getFileName().toString(),
                        new String(java.nio.file.Files.readAllBytes(file), StandardCharsets.ISO_8859_1));
            }
        }
        if (contents.isEmpty()) {
            throw new IllegalArgumentException("No hay archivos .csv en " + path);
        }
        return facade.importFootballDataCsv(contents);
    }

    @PostMapping(value = "/etl/understat/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importa paginas de Understat guardadas como HTML",
            description = "Aplica el xG a los partidos ya cargados. Un archivo por temporada.")
    public List<EtlSeasonResult> uploadUnderstat(
            @RequestHeader(name = "X-Admin-Key", required = false) String apiKey,
            @RequestParam("files") List<org.springframework.web.multipart.MultipartFile> files) throws java.io.IOException {
        requireAdminKey(apiKey);
        java.util.Map<String, String> contents = new java.util.LinkedHashMap<>();
        for (var file : files) {
            contents.put(String.valueOf(file.getOriginalFilename()), new String(file.getBytes(), StandardCharsets.UTF_8));
        }
        return facade.importUnderstatHtml(contents);
    }

    @PostMapping("/etl/understat/folder")
    @Operation(summary = "Importa las paginas HTML de Understat de una carpeta del servidor")
    public List<EtlSeasonResult> importUnderstatFolder(
            @RequestHeader(name = "X-Admin-Key", required = false) String apiKey,
            @RequestParam("path") String path) throws java.io.IOException {
        requireAdminKey(apiKey);
        java.util.Map<String, String> contents = new java.util.TreeMap<>();
        try (var stream = java.nio.file.Files.list(java.nio.file.Path.of(path))) {
            for (java.nio.file.Path file : stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).matches(".*[.]html?")).toList()) {
                contents.put(file.getFileName().toString(),
                        new String(java.nio.file.Files.readAllBytes(file), StandardCharsets.UTF_8));
            }
        }
        if (contents.isEmpty()) {
            throw new IllegalArgumentException("No hay archivos .html en " + path);
        }
        return facade.importUnderstatHtml(contents);
    }

    @PostMapping("/etl/understat")
    @Operation(summary = "Carga xG de Understat",
            description = "Descarga el xG de cada partido y lo aplica a los partidos ya cargados. "
                    + "Las temporadas se indican por su año de inicio (2018,...,2023).")
    @ApiResponse(responseCode = "200", description = "Resultado por temporada")
    @ApiResponse(responseCode = "401", description = "Clave de administración incorrecta")
    public List<EtlSeasonResult> loadUnderstat(
            @RequestHeader(name = "X-Admin-Key", required = false) String apiKey,
            @Parameter(description = "Años de inicio separados por coma, p. ej. 2022,2023")
            @RequestParam(name = "seasons", required = false) List<String> seasons,
            @Parameter(description = "Liga de Understat") @RequestParam(name = "league", defaultValue = "La_liga") String league) {
        requireAdminKey(apiKey);
        List<String> years = (seasons == null || seasons.isEmpty())
                ? DEFAULT_SEASONS.stream().map(code -> String.valueOf(2000 + Integer.parseInt(code.substring(0, 2)))).toList()
                : seasons;
        return facade.refreshUnderstat(league, years);
    }

    @PostMapping("/etl/fbref")
    @Operation(summary = "Carga calendario y resultados de FBref",
            description = "Descarga una página de partidos de FBref; los partidos ya cargados se omiten.")
    @ApiResponse(responseCode = "200", description = "Resultado de la carga")
    @ApiResponse(responseCode = "401", description = "Clave de administración incorrecta")
    public EtlSeasonResult loadFBref(
            @RequestHeader(name = "X-Admin-Key", required = false) String apiKey,
            @Parameter(description = "Ruta de FBref con el calendario")
            @RequestParam(name = "path", defaultValue = "/en/comps/12/schedule/La-Liga-Scores-and-Fixtures") String path) {
        requireAdminKey(apiKey);
        return facade.refreshFBref(path);
    }

    @PostMapping("/etl/bzzoiro")
    @Operation(summary = "Carga xG real desde Bzzoiro Sports Data",
            description = "Completa el xG de los partidos ya cargados (disponible desde 2022/23). Requiere BZZOIRO_API_KEY. "
                    + "Límite de la API gratuita: 7.500 peticiones al día (una por partido).")
    @ApiResponse(responseCode = "200", description = "Resultado por temporada")
    @ApiResponse(responseCode = "401", description = "Clave de administración incorrecta")
    public List<EtlSeasonResult> loadBzzoiro(
            @RequestHeader(name = "X-Admin-Key", required = false) String apiKey,
            @Parameter(description = "Años de inicio separados por coma, p. ej. 2022,2023,2024,2025")
            @RequestParam(name = "seasons", required = false) List<Integer> seasons) {
        requireAdminKey(apiKey);
        return facade.refreshBzzoiroXg(seasons == null || seasons.isEmpty() ? List.of(2021, 2022, 2023, 2024, 2025) : seasons);
    }

    @PostMapping("/etl/current-season")
    @Operation(summary = "Actualiza la temporada en curso",
            description = "Guarda los partidos ya finalizados de la temporada actual (football-data.org). "
                    + "Se ejecuta tambien automaticamente cada 6 horas. Requiere FOOTBALL_DATA_ORG_KEY.")
    @ApiResponse(responseCode = "200", description = "Resumen de la sincronizacion")
    @ApiResponse(responseCode = "401", description = "Clave de administración incorrecta")
    public com.ligalytics.etl.dto.EtlSummary syncCurrentSeason(
            @RequestHeader(name = "X-Admin-Key", required = false) String apiKey) {
        requireAdminKey(apiKey);
        return currentSeasonSync.sync();
    }

    @PostMapping("/etl/transfermarkt")
    @Operation(summary = "Carga valores de mercado de Transfermarkt",
            description = "Descarga la página de la competición y actualiza el valor de plantilla de cada equipo.")
    @ApiResponse(responseCode = "200", description = "Resultado de la carga")
    @ApiResponse(responseCode = "401", description = "Clave de administración incorrecta")
    public EtlSeasonResult loadTransfermarkt(
            @RequestHeader(name = "X-Admin-Key", required = false) String apiKey,
            @Parameter(description = "Ruta de Transfermarkt de la competición")
            @RequestParam(name = "path", defaultValue = "/laliga/startseite/wettbewerb/ES1") String path) {
        requireAdminKey(apiKey);
        return facade.refreshTransfermarkt(path);
    }

    @PostMapping("/train")
    @Operation(summary = "Reentrena y valida el modelo de IA",
            description = "Entrena con las temporadas anteriores a la de prueba, mide accuracy/MAE sobre la temporada "
                    + "de prueba y guarda los modelos definitivos (entrenados con todos los datos).")
    @ApiResponse(responseCode = "200", description = "Informe de entrenamiento con las métricas")
    @ApiResponse(responseCode = "401", description = "Clave de administración incorrecta")
    public TrainingReport train(@RequestHeader(name = "X-Admin-Key", required = false) String apiKey,
            @Parameter(description = "Primera temporada para entrenar (año de inicio, p. ej. 2022); por defecto todas")
            @RequestParam(name = "fromSeason", required = false) Integer fromSeason) {
        requireAdminKey(apiKey);
        return trainingService.trainAll(fromSeason);
    }

    private List<String> resolveSeasons(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return DEFAULT_SEASONS;
        }
        LinkedHashSet<String> seasons = new LinkedHashSet<>();
        for (String raw : requested) {
            String season = raw == null ? "" : raw.trim();
            if (!isValidSeason(season)) {
                throw new IllegalArgumentException(
                        "Temporada inválida: '" + raw + "' (formato esperado: 2324 para 2023-24)");
            }
            seasons.add(season);
        }
        return List.copyOf(seasons);
    }

    /** Cuatro dígitos con años consecutivos, p. ej. 2324 (23 → 24) o 9900 (99 → 00). */
    private static boolean isValidSeason(String season) {
        if (!SEASON_CODE.matcher(season).matches()) {
            return false;
        }
        int start = Integer.parseInt(season.substring(0, 2));
        int end = Integer.parseInt(season.substring(2));
        return (start + 1) % 100 == end;
    }

    private void requireAdminKey(String provided) {
        if (adminApiKey.isBlank()) {
            return;
        }
        boolean valid = provided != null && MessageDigest.isEqual(
                adminApiKey.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
        if (!valid) {
            throw new UnauthorizedException("Clave de administración inválida o ausente");
        }
    }
}
