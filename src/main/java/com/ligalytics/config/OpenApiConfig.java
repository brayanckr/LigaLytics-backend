package com.ligalytics.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;

/**
 * Configuración de la documentación OpenAPI/Swagger de la API REST.
 * Disponible en /api/swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ligaLyticsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("LigaLytics API")
                        .version("v1")
                        .description("API REST para la predicción de resultados de LaLiga: equipos, "
                                + "estadísticas, clasificación y predicciones basadas en patrones de diseño.")
                        .contact(new Contact().name("LigaLytics").email("dev@ligalytics.local")))
                .tags(List.of(
                        new Tag().name("Equipos").description("Consulta y estadísticas de los equipos de LaLiga"),
                        new Tag().name("Predicciones").description("Predicción de partidos"),
                        new Tag().name("Clasificación").description("Tabla de posiciones")));
    }
}
