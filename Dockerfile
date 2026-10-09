# ---- Etapa 1: compilar con Maven (JDK 17) ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q -DskipTests package

# ---- Etapa 2: imagen ligera de ejecución (JRE 17) ----
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN useradd --system --create-home ligalytics
COPY --from=build /app/target/ligalytics-backend-*.jar app.jar
RUN mkdir -p /app/models /app/data && chown -R ligalytics:ligalytics /app
USER ligalytics

# Railway inyecta PORT; en local se usa 8080 (ver server.port en application.properties).
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
