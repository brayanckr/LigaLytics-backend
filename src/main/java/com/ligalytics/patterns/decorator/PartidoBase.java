package com.ligalytics.patterns.decorator;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Componente concreto del patrón <b>Decorator</b>: los datos simples de un
 * partido (resultado final) sin información adicional de fuentes externas.
 */
public class PartidoBase implements PartidoComponent {

    private final Long id;
    private final String local;
    private final String visitante;
    private final LocalDateTime fecha;
    private final Integer golesLocal;
    private final Integer golesVisitante;

    public PartidoBase(Long id,
            String local,
            String visitante,
            LocalDateTime fecha,
            Integer golesLocal,
            Integer golesVisitante) {
        this.id = id;
        this.local = Objects.requireNonNull(local, "local must not be null");
        this.visitante = Objects.requireNonNull(visitante, "visitante must not be null");
        this.fecha = fecha;
        this.golesLocal = golesLocal;
        this.golesVisitante = golesVisitante;
    }

    public Long getId() {
        return id;
    }

    public String getLocal() {
        return local;
    }

    public String getVisitante() {
        return visitante;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public Integer getGolesLocal() {
        return golesLocal;
    }

    public Integer getGolesVisitante() {
        return golesVisitante;
    }

    @Override
    public String descripcion() {
        String fechaTexto = fecha == null ? "" : " (" + fecha.toLocalDate() + ")";
        return local + " " + golesLocal + "-" + golesVisitante + " " + visitante + fechaTexto;
    }

    @Override
    public Map<String, Object> datos() {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("id", id);
        datos.put("local", local);
        datos.put("visitante", visitante);
        datos.put("fecha", fecha);
        datos.put("golesLocal", golesLocal);
        datos.put("golesVisitante", golesVisitante);
        return datos;
    }
}
