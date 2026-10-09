# LigaLytics — Backend

API REST de **predicción de resultados y estadísticas de LaLiga** (resultado 1X2, goles, córneres y tarjetas).
Java 17 · Spring Boot 3.3 · PostgreSQL · Weka · 8 patrones de diseño. El frontend está en
[LigaLytics-frontend](https://github.com/brayanckr/LigaLytics-frontend).

Documentación técnica con los diagramas de clases de cada patrón: [`docs/DOCUMENTO_TECNICO.md`](docs/DOCUMENTO_TECNICO.md).

## Requisitos
- JDK 17 o superior (el Maven Wrapper `mvnw` descarga Maven)
- PostgreSQL con una base de datos vacía `ligalytics_db` (las tablas las crea la aplicación)

## Puesta en marcha
```powershell
.\start-backend.ps1
```
El script pide la contraseña de PostgreSQL o la lee de `.env.local` (ignorado por git):
```
DB_URL=jdbc:postgresql://localhost:5432/ligalytics_db
DB_USERNAME=postgres
DB_PASSWORD=tu_contraseña
FOOTBALL_DATA_ORG_KEY=...   # calendario y resultados en vivo (football-data.org, gratis)
BZZOIRO_API_KEY=...         # xG por partido (sports.bzzoiro.com, gratis)
FOOTBALL_CHARTS_API_KEY=... # probabilidades de ganador (football-charts.com, gratis); sin clave se usa el modelo propio
ADMIN_API_KEY=...           # opcional: protege /api/admin/**
```
API en <http://localhost:8080/api> · Swagger en <http://localhost:8080/api/swagger-ui.html>.

## Cargar los datos
- **Histórico (entrenamiento):** CSV de football-data.co.uk. Si la tabla de partidos está vacía y hay red, el backend
  los descarga solo al arrancar (`AUTO_LOAD_DATA=false` lo desactiva). Si tu red lo bloquea, descarga los CSV a mano y
  cárgalos con `POST /api/admin/etl/football-data/folder?path=<carpeta>` o súbelos desde el panel de administración.
- **xG real:** `POST /api/admin/etl/bzzoiro?seasons=2022,2023,2024,2025` (una petición por partido; límite gratuito 7 500/día).
- **Entrenar y validar la IA:** `POST /api/admin/train` (accuracy para el resultado, MAE para goles, córneres y tarjetas).

## Endpoints principales
`/api/teams` · `/api/teams/{id}/stats` · `/api/ranking` · `/api/seasons` · `/api/predict` · `/api/predict/history` ·
`/api/model/report` · `/api/fixtures` · `/api/fixtures/live` · `/api/admin/**`

## Pruebas
```powershell
.\mvnw.cmd test      # usa H2 en memoria, no necesita PostgreSQL
```

## Despliegue
`Dockerfile` + `railway.json` para Railway (variables `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `CORS_ALLOWED_ORIGINS`,
`ADMIN_API_KEY`, `FOOTBALL_DATA_ORG_KEY`, `BZZOIRO_API_KEY`). GitHub Actions (`.github/workflows/ci.yml`) ejecuta las
pruebas y despliega en cada push a `main`.
