package com.ligalytics.webhook;

/**
 * Evento de aplicación que se publica cuando llega un webhook autenticado y válido. Quien quiera reaccionar
 * (liquidar apuestas, refrescar datos...) se suscribe con {@code @EventListener} sin tocar el controlador.
 */
public record MatchEventReceived(MatchEventPayload payload) {
}
