package com.ligalytics.patterns.decorator;

import java.util.Map;

/**
 * Componente del patrón <b>Decorator</b>. Expone una descripción legible y un
 * mapa de datos del partido que los decoradores van enriqueciendo.
 */
public interface PartidoComponent {

    String descripcion();

    Map<String, Object> datos();
}
