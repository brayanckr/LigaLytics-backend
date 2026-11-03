package com.ligalytics.exception;

/**
 * Un proveedor externo (cuotas, calendario...) no respondió a tiempo o devolvió algo inutilizable. El manejador global
 * la traduce a una respuesta HTTP 503 con un mensaje claro, en lugar de dejar la petición colgada o dar un 500.
 *
 * <p>Extiende {@link IllegalStateException} para no cambiar el comportamiento de quien ya capturaba esa excepción.</p>
 */
public class ExternalServiceException extends IllegalStateException {

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
