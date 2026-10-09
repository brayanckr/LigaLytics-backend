<#
.SYNOPSIS
  Arranca el backend de LigaLytics contra tu PostgreSQL local.

.DESCRIPTION
  Spring Boot no lee archivos .env, asi que este script prepara las variables de
  entorno y lanza el backend. La contrasena de PostgreSQL se lee de
  .env.local (ignorado por git) o se pide por consola; nunca se guarda
  en el repositorio.

  Archivo .env.local (opcional), una variable por linea:
    DB_URL=jdbc:postgresql://localhost:5432/ligalytics_db
    DB_USERNAME=postgres
    DB_PASSWORD=tu_contrasena
    ADMIN_API_KEY=una_clave   (opcional)

  Al arrancar, si la tabla de partidos esta vacia, el backend descarga solo las
  6 temporadas reales de football-data.co.uk y entrena la IA (necesita internet).
#>

$ErrorActionPreference = 'Stop'
$backend = $PSScriptRoot
$envFile = Join-Path $backend '.env.local'

if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$' -and -not $_.TrimStart().StartsWith('#')) {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
        }
    }
    Write-Host "Variables cargadas desde .env.local"
}

if (-not $env:DB_PASSWORD) {
    $secure = Read-Host 'Contrasena de PostgreSQL (usuario postgres)' -AsSecureString
    $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
}

Write-Host "Base de datos: $(if ($env:DB_URL) { $env:DB_URL } else { 'jdbc:postgresql://localhost:5432/ligalytics_db (por defecto)' })"
Write-Host "API en http://localhost:8080/api  |  Swagger en http://localhost:8080/api/swagger-ui.html"

Set-Location $backend
& .\mvnw.cmd spring-boot:run
