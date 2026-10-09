package com.ligalytics.etl;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Unifica los nombres de equipo de LaLiga que usan las distintas fuentes
 * (football-data usa "Ath Madrid" o "Espanol"; Understat, "Atletico Madrid" o
 * "Espanyol"; Transfermarkt, "Atlético de Madrid" o "RCD Espanyol Barcelona")
 * en un único nombre canónico, sin tildes, para que el mismo equipo no se
 * guarde varias veces y los cruces entre fuentes funcionen.
 *
 * <p>La comparación ignora mayúsculas, tildes y signos de puntuación. Si un
 * nombre no está registrado se devuelve tal cual (limpiando espacios).</p>
 */
public final class TeamNameNormalizer {

    private static final Map<String, String> ALIASES = new HashMap<>();

    static {
        register("Alaves", "deportivo alaves", "d alaves");
        register("Almeria", "ud almeria");
        register("Athletic Bilbao", "ath bilbao", "athletic club", "athletic");
        register("Atletico Madrid", "ath madrid", "atletico de madrid", "atl madrid", "club atletico de madrid");
        register("Barcelona", "fc barcelona", "barca");
        register("Real Betis", "betis", "real betis balompie");
        register("Celta Vigo", "celta", "rc celta de vigo", "celta de vigo");
        register("Cadiz", "cadiz cf");
        register("Eibar", "sd eibar");
        register("Elche", "elche cf");
        register("Espanyol", "espanol", "rcd espanyol", "rcd espanyol barcelona", "rcd espanyol de barcelona");
        register("Getafe", "getafe cf");
        register("Girona", "girona fc");
        register("Granada", "granada cf");
        register("Huesca", "sd huesca");
        register("Las Palmas", "ud las palmas");
        register("Leganes", "cd leganes");
        register("Levante", "levante ud");
        register("Mallorca", "rcd mallorca");
        register("Osasuna", "ca osasuna");
        register("Rayo Vallecano", "vallecano", "rayo vallecano de madrid");
        register("Real Madrid", "real madrid cf");
        register("Oviedo", "real oviedo");
        register("Real Sociedad", "real sociedad de futbol");
        register("Real Sociedad", "sociedad");
        register("Sevilla", "sevilla fc");
        register("Valencia", "valencia cf");
        register("Real Valladolid", "valladolid", "real valladolid cf");
        register("Villarreal", "villarreal cf");
        // Equipos que aparecen en temporadas anteriores de football-data
        register("Deportivo La Coruna", "la coruna", "deportivo de la coruna", "rc deportivo");
        register("Sporting Gijon", "sp gijon", "real sporting de gijon");
        register("Malaga", "malaga cf");
    }

    private TeamNameNormalizer() {
    }

    /**
     * Devuelve el nombre canónico del equipo, o el nombre original limpio si no
     * hay un alias registrado. Devuelve {@code null} si la entrada es nula.
     */
    public static String canonical(String rawName) {
        if (rawName == null) {
            return null;
        }
        String cleaned = rawName.trim().replaceAll("\\s+", " ");
        return ALIASES.getOrDefault(key(cleaned), cleaned);
    }

    private static void register(String canonical, String... aliases) {
        ALIASES.put(key(canonical), canonical);
        for (String alias : aliases) {
            ALIASES.put(key(alias), canonical);
        }
    }

    /** Clave de comparación: sin tildes, en minúsculas y sin puntuación. */
    private static String key(String name) {
        String withoutAccents = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return withoutAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }
}
