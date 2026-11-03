package com.ligalytics.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.ligalytics.exception.ExternalServiceException;
import com.ligalytics.exception.ForbiddenException;
import com.ligalytics.exception.ResourceNotFoundException;
import com.ligalytics.exception.UnauthorizedException;
import com.ligalytics.service.dto.ApiError;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Manejador global de excepciones de la API REST. Traduce las excepciones a un
 * cuerpo {@link ApiError} uniforme con el código HTTP adecuado.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiError> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ApiError> handleExternalService(ExternalServiceException ex, HttpServletRequest request) {
        log.warn("Proveedor externo sin respuesta en {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE,
                "El proveedor de datos externo no responde. Inténtalo de nuevo en unos minutos.", request, Map.of());
    }

    /** Respeta el estado y el motivo que ya eligió quien la lanzó (p. ej. 503 «Cuotas no configuradas»), no un 500. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return build(status, message, request, Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Recurso no encontrado", request, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> details.put(error.getField(), error.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "Datos de la petición inválidos", request, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es válido", request, Map.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", request, Map.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status,
            String message,
            HttpServletRequest request,
            Map<String, String> details) {
        ApiError body = ApiError.of(status.value(), status.getReasonPhrase(), message, request.getRequestURI(), details);
        return ResponseEntity.status(status).body(body);
    }
}
