package com.ligalytics.fixtures;

import java.util.List;

/**
 * Proveedor de calendario y resultados (en vivo). Hay una implementacion por
 * API; cambiar de proveedor no afecta al resto de la aplicacion.
 */
public interface FixturesProvider {

    String name();

    /** Indica si el proveedor tiene credenciales (clave de API) configuradas. */
    boolean isConfigured();

    /** Todos los partidos de la temporada en curso de LaLiga. */
    List<Fixture> fetchSeason();
}
