package com.ligalytics.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.ligalytics.auth.AuthInterceptor;

/**
 * Protección por defecto de la API: toda ruta exige sesión salvo la lista de públicas de abajo, así que un
 * endpoint nuevo nace protegido y no al revés.
 *
 * <p><strong>Los patrones van SIN el prefijo {@code /api}.</strong> Ese prefijo es el
 * {@code server.servlet.context-path} y Spring MVC compara los patrones con la ruta que queda después de
 * quitarlo ({@code /betting/wallet}, no {@code /api/betting/wallet}); un patrón {@code /api/**} no
 * coincidiría con nada y dejaría toda la API abierta sin ningún aviso.</p>
 *
 * <p>Públicas:</p>
 * <ul>
 *   <li>{@code /auth/login} y {@code /auth/register}: no puede haber sesión antes de entrar. {@code /auth/me} y
 *       {@code /auth/logout} sí quedan protegidas: sin sesión deben dar 401, y el frontend usa ese 401 para saber
 *       si hay sesión.</li>
 *   <li>{@code /webhooks/**}: las llamadas del proveedor de datos no llevan cookie; se autentican con la firma
 *       HMAC (ver {@code WebhookSignatureVerifier}).</li>
 *   <li>{@code /public/**}: reservado para futuros endpoints abiertos.</li>
 *   <li>{@code /health}: Railway lo consulta sin sesión para dar por buena una versión desplegada.</li>
 *   <li>{@code /ws/**}: el handshake de SockJS/STOMP valida el origen por su cuenta y no lleva datos privados.</li>
 *   <li>{@code /error}: el redireccionamiento interno de errores no debe volver a pasar por la autenticación.</li>
 * </ul>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login", "/auth/register",
                        "/public/**",
                        "/webhooks/**",
                        "/health",
                        "/ws/**",
                        "/error");
    }
}
