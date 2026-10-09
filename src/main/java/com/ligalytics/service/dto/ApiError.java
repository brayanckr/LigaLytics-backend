package com.ligalytics.service.dto;

import java.time.Instant;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cuerpo de error uniforme devuelto por el manejador global de excepciones.
 */
@Schema(description = "Respuesta de error de la API")
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> details
) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, Map.of());
    }

    public static ApiError of(int status, String error, String message, String path, Map<String, String> details) {
        return new ApiError(Instant.now(), status, error, message, path, details == null ? Map.of() : details);
    }
}
