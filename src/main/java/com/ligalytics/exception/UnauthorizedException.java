package com.ligalytics.exception;

/**
 * Excepción lanzada cuando falta o es incorrecta la clave de administración.
 * El manejador global la traduce a una respuesta HTTP 401.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
