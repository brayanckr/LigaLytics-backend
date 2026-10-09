package com.ligalytics.patterns.observer;

/**
 * Interfaz del patrón <b>Observer</b>. Los suscriptores interesados en los
 * eventos del ETL implementan este contrato y se registran en el sujeto
 * {@link ETLSubject}.
 */
public interface ETLObserver {

    /**
     * Nombre legible del observador, útil para logs y diagnósticos.
     */
    String name();

    /**
     * Se invoca cuando el ETL termina una ingesta de datos.
     *
     * @param event resumen de la ingesta completada
     */
    void onEtlCompleted(EtlEvent event);
}
