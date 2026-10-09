package com.ligalytics.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Protege con sesión las rutas de apuestas y la cuenta; el resto de la API sigue siendo pública. */
@Configuration
public class AuthWebConfig implements WebMvcConfigurer {

    private final AuthInterceptor interceptor;

    public AuthWebConfig(AuthInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/betting/**", "/auth/me", "/auth/logout");
    }
}
