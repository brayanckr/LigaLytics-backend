package com.ligalytics.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ligalytics.etl.TeamNameNormalizer;

/**
 * Ficha estática de cada equipo de LaLiga (estadio, valor de plantilla y escudo) cargada de
 * {@code team-profiles.csv} (datos de Transfermarkt). Sirve de respaldo cuando la base de datos no tiene el dato:
 * el valor guardado por el ETL, si existe, siempre manda. Se busca por el nombre canónico del equipo.
 */
public final class TeamProfiles {

    /** Datos de una ficha; cualquier campo puede faltar. */
    public record Profile(String stadium, BigDecimal marketValue, String crest) {
    }

    private static final Logger log = LoggerFactory.getLogger(TeamProfiles.class);
    private static final String RESOURCE = "/team-profiles.csv";
    private static final Map<String, Profile> PROFILES = load();

    private TeamProfiles() {
    }

    /** Ficha del equipo (por nombre en cualquiera de sus variantes), o vacío si no hay. */
    public static Optional<Profile> of(String teamName) {
        String canonical = TeamNameNormalizer.canonical(teamName);
        return canonical == null ? Optional.empty() : Optional.ofNullable(PROFILES.get(canonical));
    }

    private static Map<String, Profile> load() {
        Map<String, Profile> profiles = new HashMap<>();
        try (InputStream in = TeamProfiles.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.warn("No se encontro {}: los equipos no tendran ficha de respaldo", RESOURCE);
                return profiles;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            reader.readLine(); // cabecera
            String line;
            while ((line = reader.readLine()) != null) {
                String[] cols = line.split(";", -1);
                if (cols.length < 4 || cols[0].isBlank()) {
                    continue;
                }
                profiles.put(TeamNameNormalizer.canonical(cols[0]),
                        new Profile(blankToNull(cols[1]), cols[2].isBlank() ? null : new BigDecimal(cols[2].trim()),
                                blankToNull(cols[3])));
            }
        } catch (IOException | NumberFormatException ex) {
            log.warn("No se pudo leer {}: {}", RESOURCE, ex.getMessage());
        }
        return profiles;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
