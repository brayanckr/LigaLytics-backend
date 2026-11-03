package com.ligalytics;

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder;
import org.springframework.test.web.servlet.setup.MockMvcConfigurer;
import org.springframework.web.context.WebApplicationContext;

import com.ligalytics.auth.AuthCookies;
import com.ligalytics.auth.AuthInterceptor;
import com.ligalytics.auth.AuthService;

import jakarta.servlet.http.Cookie;

/**
 * Hace que el {@code MockMvc} de una prueba hable ya con sesión iniciada: cada petición lleva la cookie de sesión
 * y la cabecera anti-CSRF. Es opt-in ({@code @Import}) porque desde que toda la API exige sesión salvo la lista de
 * públicas, las pruebas de endpoints de negocio no pueden ir como anónimas; las de autenticación y de protección
 * no lo usan para poder probar justo el caso anónimo.
 *
 * <p>La sesión se vuelve a abrir sola si otra prueba borró usuarios o sesiones de la base compartida.</p>
 */
@TestConfiguration
public class AuthenticatedMockMvcConfig {

    private static final String EMAIL = "pruebas-autenticadas@example.com";
    private static final String PASSWORD = "clave-de-pruebas-1";

    @Bean
    MockMvcBuilderCustomizer authenticatedRequests(AuthService authService) {
        return builder -> builder.apply(new MockMvcConfigurer() {
            private String token;

            @Override
            public RequestPostProcessor beforeMockMvcCreated(ConfigurableMockMvcBuilder<?> b, WebApplicationContext context) {
                return request -> {
                    request.setCookies(new Cookie(AuthCookies.SESSION_COOKIE, currentToken(authService)));
                    request.addHeader(AuthInterceptor.CSRF_HEADER, "XMLHttpRequest");
                    return request;
                };
            }

            private synchronized String currentToken(AuthService service) {
                if (token == null || service.authenticate(token).isEmpty()) {
                    try {
                        token = service.register(EMAIL, "Pruebas", PASSWORD).token();
                    } catch (IllegalArgumentException alreadyRegistered) {
                        token = service.login(EMAIL, PASSWORD).token();
                    }
                }
                return token;
            }
        });
    }
}
