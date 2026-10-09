package com.ligalytics.auth;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.ligalytics.exception.UnauthorizedException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Exige {@code Authorization: Bearer <token>} en las rutas protegidas y deja el usuario en la petición. */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String USER_ATTRIBUTE = "ligalytics.user";
    public static final String TOKEN_ATTRIBUTE = "ligalytics.token";

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null;
        User user = authService.authenticate(token)
                .orElseThrow(() -> new UnauthorizedException("Inicia sesión para continuar"));
        request.setAttribute(USER_ATTRIBUTE, user);
        request.setAttribute(TOKEN_ATTRIBUTE, token);
        return true;
    }
}
