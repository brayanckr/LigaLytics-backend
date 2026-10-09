package com.ligalytics.patterns.decorator;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Decorador concreto que añade el valor de mercado de la plantilla obtenido de
 * Transfermarkt al partido base.
 */
public class TransfermarktDecorator extends PartidoDecorator {

    private final BigDecimal valorLocal;
    private final BigDecimal valorVisitante;

    public TransfermarktDecorator(PartidoComponent delegate, BigDecimal valorLocal, BigDecimal valorVisitante) {
        super(delegate);
        this.valorLocal = valorLocal;
        this.valorVisitante = valorVisitante;
    }

    @Override
    public String descripcion() {
        return delegate.descripcion() + " [valor " + valorLocal + "-" + valorVisitante + "]";
    }

    @Override
    public Map<String, Object> datos() {
        Map<String, Object> datos = super.datos();
        datos.put("valorPlantillaLocal", valorLocal);
        datos.put("valorPlantillaVisitante", valorVisitante);
        return datos;
    }
}
