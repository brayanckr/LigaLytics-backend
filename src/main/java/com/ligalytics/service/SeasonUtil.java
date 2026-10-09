package com.ligalytics.service;

import java.time.LocalDateTime;

/**
 * Utilidades de temporada de LaLiga. Una temporada empieza en agosto y termina
 * en mayo/junio, por lo que un partido jugado de enero a julio pertenece a la
 * temporada que empezó el año anterior.
 *
 * <p>Las temporadas se identifican por su año de inicio ({@code 2023} = 2023-24),
 * por la etiqueta legible {@code "2023/2024"} y por el código de
 * football-data.co.uk ({@code "2324"}).</p>
 */
public final class SeasonUtil {

    private static final int FIRST_MONTH_OF_SEASON = 7;

    private SeasonUtil() {
    }

    /** Año en que empezó la temporada a la que pertenece la fecha. */
    public static int startYear(LocalDateTime date) {
        return date.getMonthValue() >= FIRST_MONTH_OF_SEASON ? date.getYear() : date.getYear() - 1;
    }

    /** Etiqueta legible, p. ej. {@code "2023/2024"}. */
    public static String label(int startYear) {
        return startYear + "/" + (startYear + 1);
    }

    /** Código de football-data, p. ej. {@code "2324"}. */
    public static String code(int startYear) {
        return String.format("%02d%02d", startYear % 100, (startYear + 1) % 100);
    }

    /** Año de inicio a partir del código de football-data ({@code "2324"} → 2023). */
    public static int startYearFromCode(String code) {
        return 2000 + Integer.parseInt(code.substring(0, 2));
    }

    /** Primer instante de la temporada (1 de julio). */
    public static LocalDateTime start(int startYear) {
        return LocalDateTime.of(startYear, FIRST_MONTH_OF_SEASON, 1, 0, 0);
    }

    /** Primer instante de la temporada siguiente (exclusivo). */
    public static LocalDateTime endExclusive(int startYear) {
        return start(startYear + 1);
    }
}
