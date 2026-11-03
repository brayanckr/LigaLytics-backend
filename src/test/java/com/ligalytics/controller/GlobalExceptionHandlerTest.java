package com.ligalytics.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import com.ligalytics.exception.ExternalServiceException;
import com.ligalytics.service.dto.ApiError;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/betting/matches");

    @Test
    void anUnresponsiveProviderIsA503WithAClearMessageNotA500() {
        ResponseEntity<ApiError> response = handler.handleExternalService(
                new ExternalServiceException("Bzzoiro no responde (/odds/)", new RuntimeException("timeout")), request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertTrue(response.getBody().message().contains("no responde"));
        // El detalle técnico (ruta del proveedor) no se devuelve al cliente.
        assertFalse(response.getBody().message().contains("Bzzoiro"));
    }

    @Test
    void aResponseStatusExceptionKeepsItsStatusAndReason() {
        ResponseEntity<ApiError> response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Cuotas no configuradas: define BZZOIRO_API_KEY"),
                request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("Cuotas no configuradas: define BZZOIRO_API_KEY", response.getBody().message());
    }
}
