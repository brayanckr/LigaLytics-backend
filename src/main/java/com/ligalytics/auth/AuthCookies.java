package com.ligalytics.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Cookie de sesión. El token viaja solo en una cookie {@code HttpOnly}: el JavaScript de la página no puede
 * leerla, así que un XSS no se la lleva. {@code Secure} y {@code SameSite} se configuran por entorno:
 *
 * <ul>
 *   <li>{@code ligalytics.auth.cookie.secure} (por defecto {@code true}): solo por HTTPS (los navegadores también
 *       la aceptan en {@code http://localhost}).</li>
 *   <li>{@code ligalytics.auth.cookie.same-site} (por defecto {@code Lax}): usa {@code None} únicamente si
 *       frontend y backend están en sitios distintos; entonces exige {@code Secure}.</li>
 * </ul>
 */
@Component
public class AuthCookies {

    public static final String SESSION_COOKIE = "ligalytics_session";

    private final boolean secure;
    private final String sameSite;

    public AuthCookies(@Value("${ligalytics.auth.cookie.secure:true}") boolean secure,
            @Value("${ligalytics.auth.cookie.same-site:Lax}") String sameSite) {
        this.secure = secure;
        this.sameSite = sameSite;
    }

    /** Token de la cookie de sesión de la petición, o {@code null} si no viene (o viene vacío). */
    public static String tokenFrom(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (SESSION_COOKIE.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /** Añade a la respuesta la cookie con el token de una sesión recién abierta. */
    public void write(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(token, AuthService.SESSION_LIFETIME.toSeconds()).toString());
    }

    /** Pide al navegador que borre la cookie (cierre de sesión o cookie que ya no vale). */
    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", 0).toString());
    }

    private ResponseCookie build(String value, long maxAgeSeconds) {
        return ResponseCookie.from(SESSION_COOKIE, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }
}
