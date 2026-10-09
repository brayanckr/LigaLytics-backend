package com.ligalytics.patterns.decorator;

import java.util.Map;

/**
 * Decorador concreto que añade los goles esperados (xG) obtenidos de Understat
 * al partido base.
 */
public class UnderstatDecorator extends PartidoDecorator {

    private final Double xgLocal;
    private final Double xgVisitante;

    public UnderstatDecorator(PartidoComponent delegate, Double xgLocal, Double xgVisitante) {
        super(delegate);
        this.xgLocal = xgLocal;
        this.xgVisitante = xgVisitante;
    }

    @Override
    public String descripcion() {
        return delegate.descripcion() + " [xG " + xgLocal + "-" + xgVisitante + "]";
    }

    @Override
    public Map<String, Object> datos() {
        Map<String, Object> datos = super.datos();
        datos.put("xgLocal", xgLocal);
        datos.put("xgVisitante", xgVisitante);
        return datos;
    }
}
