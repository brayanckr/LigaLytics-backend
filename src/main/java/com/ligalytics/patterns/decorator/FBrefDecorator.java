package com.ligalytics.patterns.decorator;

import java.util.Map;

/**
 * Decorador concreto que añade la posesión y la presión (PPDA) obtenidas de
 * FBref al partido base.
 */
public class FBrefDecorator extends PartidoDecorator {

    private final Double posesionLocal;
    private final Double posesionVisitante;
    private final Double presionLocal;
    private final Double presionVisitante;

    public FBrefDecorator(PartidoComponent delegate,
            Double posesionLocal,
            Double posesionVisitante,
            Double presionLocal,
            Double presionVisitante) {
        super(delegate);
        this.posesionLocal = posesionLocal;
        this.posesionVisitante = posesionVisitante;
        this.presionLocal = presionLocal;
        this.presionVisitante = presionVisitante;
    }

    @Override
    public String descripcion() {
        return delegate.descripcion() + " [posesión " + posesionLocal + "%-" + posesionVisitante
                + "%, presión " + presionLocal + "-" + presionVisitante + "]";
    }

    @Override
    public Map<String, Object> datos() {
        Map<String, Object> datos = super.datos();
        datos.put("posesionLocal", posesionLocal);
        datos.put("posesionVisitante", posesionVisitante);
        datos.put("presionLocal", presionLocal);
        datos.put("presionVisitante", presionVisitante);
        return datos;
    }
}
