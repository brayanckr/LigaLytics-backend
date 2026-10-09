package com.ligalytics.exception;

/**
 * Excepción lanzada cuando un recurso solicitado no existe. El manejador global
 * la traduce a una respuesta HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
