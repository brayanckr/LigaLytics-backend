package com.ligalytics.config;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Canal en vivo por WebSocket con STOMP.
 *
 * <ul>
 *   <li>Los clientes web se conectan con SockJS a {@code /api/ws} (el prefijo {@code /api} es el context-path) y
 *       los WebSocket nativos a {@code /api/ws/websocket}; después se suscriben a
 *       {@value #LIVE_MATCHES_TOPIC}.</li>
 *   <li>Solo el servidor emite: cuando llega un webhook autenticado se retransmite el evento al tópico.</li>
 *   <li>Solo se aceptan conexiones desde los orígenes de {@code ligalytics.cors.allowed-origins}, los mismos
 *       que permite CORS.</li>
 * </ul>
 *
 * <p>Con el broker simple, un cliente que envíe un frame SEND a {@code /topic/**} lo haría llegar a todos los
 * suscriptores, es decir, podría falsear marcadores. Por eso el canal de entrada rechaza todo SEND: nadie
 * necesita enviar nada por aquí.</p>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** Tópico al que se emiten las actualizaciones de marcador y estadísticas. */
    public static final String LIVE_MATCHES_TOPIC = "/topic/live-matches";

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    private final String[] allowedOrigins;

    public WebSocketConfig(@Value("${ligalytics.cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // SockJS para el cliente web (sockjs-client); los clientes WebSocket nativos usan /api/ws/websocket.
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins).withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.SEND.equals(accessor.getCommand())) {
                    log.warn("Frame SEND de un cliente WebSocket descartado (destino {})", accessor.getDestination());
                    return null;
                }
                return message;
            }
        });
    }
}
