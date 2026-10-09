package com.ligalytics.patterns.decorator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Decorador abstracto del patrón <b>Decorator</b>. Envuelve un
 * {@link PartidoComponent} y delega en él, permitiendo a las subclases añadir
 * atributos sin modificar el componente original.
 */
public abstract class PartidoDecorator implements PartidoComponent {

    protected final PartidoComponent delegate;

    protected PartidoDecorator(PartidoComponent delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    public String descripcion() {
        return delegate.descripcion();
    }

    @Override
    public Map<String, Object> datos() {
        return new LinkedHashMap<>(delegate.datos());
    }
}
