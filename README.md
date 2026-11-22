# ms-identidad

Identidad y perfiles de usuario de la plataforma Respirar y Soltar. Cubre **RF-01**.
Las credenciales y MFA se delegan a AWS Cognito; este servicio persiste el perfil de
negocio (nombre, email, rol) y los contactos de emergencia del usuario.

**Puerto:** 8081 · **Base de datos:** `identidad_db` · **Swagger:** http://localhost:8081/swagger-ui.html

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/usuarios` | Registra un usuario (201) |
| GET | `/api/usuarios?rol=` | Lista usuarios, filtro opcional por rol |
| GET | `/api/usuarios/{id}` | Obtiene un usuario (404 si no existe) |
| PUT | `/api/usuarios/{id}` | Actualiza el perfil |
| DELETE | `/api/usuarios/{id}` | Elimina el usuario (204) |
| POST | `/api/usuarios/{id}/contactos` | Agrega contacto de emergencia (201) |
| GET | `/api/usuarios/{id}/contactos` | Lista contactos del usuario |
| DELETE | `/api/usuarios/{id}/contactos/{contactoId}` | Elimina el contacto (204) |

Roles válidos: `PACIENTE`, `PSICOLOGO`, `ADMIN`.

## Cómo correr localmente

```bash
# Opción A — todo el sistema en contenedores (repo rys-infraestructura, clonado al lado de este)
cd ../rys-infraestructura && docker compose up -d --build
curl http://localhost:8081/api/usuarios

# Opción B — solo este servicio con Maven (requiere PostgreSQL locales)
mvn spring-boot:run

# Opción C — solo la imagen Docker de este servicio
docker build -t rys/ms-identidad:local .
docker run --rm -p 8081:8081 -e DB_HOST=host.docker.internal rys/ms-identidad:local
curl http://localhost:8081/actuator/health
```

## Arquitectura

Capas `api` → `service` → `repository` → `domain`, con DTOs validados
(jakarta.validation) y errores centralizados en `GlobalExceptionHandler`.
Relación JPA destacada: `Usuario` **OneToMany** `ContactoEmergencia` (y **ManyToOne** inversa).

## Imagen Docker (EP3)

`Dockerfile` **multi-stage**: la etapa `build` (Maven + JDK 21) descarga dependencias en una capa aparte (caché)
y separa el jar en las capas de Spring Boot; la etapa `runtime` usa solo **JRE 21 sobre Alpine**, corre con un
**usuario sin privilegios** (UID 10001), expone el puerto 8081, define un `HEALTHCHECK` y ajusta la JVM al
límite de memoria del contenedor (`MaxRAMPercentage`). `.dockerignore` deja fuera `target/`, `.git/` y el resto.
Tamaño aproximado: ~145 MB comprimida (≈400 MB descomprimida, de los cuales ~180 MB son el JRE compartido).

## Despliegue en la nube (EP3)

Corre en el clúster **Docker Swarm** de AWS como el servicio `rys_ms-identidad` (definido en
`rys-infraestructura/docker-compose.yml`), detrás del **API Gateway**:
`https://<api>.execute-api.us-east-1.amazonaws.com/prod/identidad/api/usuarios` (cabecera `x-api-key`).

### Pipeline CI/CD (`.github/workflows/ci-cd.yml`)

| Etapa | Cuándo | Qué hace |
|---|---|---|
| 1 · Build y pruebas | push y pull request a `main`/`develop` | `mvn verify` + reporte de pruebas |
| 2 · Recursos cloud | push a `main` | Crea/verifica el repositorio ECR `rys/ms-identidad` (análisis de vulnerabilidades al subir) |
| 3 · Imagen | push a `main` | `docker build` multi-stage y push a Amazon ECR con etiqueta = commit (+ `latest`) |
| 4 · Despliegue | push a `main` | `docker service update --image …` por SSH al manager: rolling update `start-first` con rollback automático |
| 5 · Prueba de humo | push a `main` | Por el API Gateway: `/actuator/info` muestra la versión del commit, `/actuator/health` = UP, `GET /api/usuarios` = 200 y sin API key = 403 |

Secrets del repositorio: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN` (Learner Lab, se renuevan
en cada sesión), `SWARM_HOST`, `SWARM_SSH_KEY`, `API_URL`, `API_KEY`.

## Correcciones aplicadas sobre la EP2

- **Configuración externa:** la conexión a la base de datos (y a Redis) estaba fija en `localhost`, lo que impedía
  ejecutar el servicio en contenedores; ahora se toma de `DB_HOST`, `DB_PORT`, `DB_NAME`, `REDIS_HOST` (con
  valores por defecto para desarrollo).
- **Salud del servicio:** se agregó Spring Boot Actuator (`/actuator/health/liveness`, `/readiness`, `/info` con la
  versión desplegada), que usan el `HEALTHCHECK` de Docker, Swarm y la prueba de humo del pipeline.
- **Apagado ordenado** (`server.shutdown: graceful`): al escalar hacia abajo o actualizar, termina las peticiones en curso.
- **Secretos fuera del código:** la clave de la base de datos llega como secreto de Docker Swarm
  (`/run/secrets/spring.datasource.password`, leído con `spring.config.import: configtree`).
- `spring.jpa.open-in-view: false` y pool de conexiones acotado (`DB_POOL_SIZE`, 4 por réplica) para que
  varias réplicas compartan la misma instancia RDS.
