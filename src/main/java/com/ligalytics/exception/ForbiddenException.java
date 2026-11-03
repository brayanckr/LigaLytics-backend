package com.ligalytics.exception;

/**
 * Petición rechazada por una regla de seguridad (no por falta de sesión). El manejador global la traduce a una
 * respuesta HTTP 403.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
