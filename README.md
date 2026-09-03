# TicketFlow

Plataforma backend distribuida de reservas y venta de entradas en tiempo real
para eventos de alta concurrencia.

El objetivo principal es resolver el problema de compra concurrente de asientos
(*double-booking problem*), garantizando consistencia estricta, baja latencia y
tolerancia a fallos mediante una arquitectura desacoplada y basada en eventos.

## Tabla de contenidos

- [Arquitectura](#arquitectura)
- [Stack tecnológico](#stack-tecnológico)
- [Estrategia de concurrencia](#estrategia-de-concurrencia)
- [Modelo de dominio](#modelo-de-dominio)
- [Contrato de API](#contrato-de-api)
- [Estructura del repositorio](#estructura-del-repositorio)
- [Requisitos previos](#requisitos-previos)
- [Puesta en marcha](#puesta-en-marcha)
- [Build, calidad y seguridad](#build-calidad-y-seguridad)
- [Documentación](#documentación)

## Arquitectura

Dos microservicios en un repositorio Maven multi-module con un módulo común
(`common`):

1. **`reservation-service`** — núcleo del dominio. Gestiona la selección de
   asientos, los locks distribuidos en Redis, el procesamiento de pago y la
   persistencia transaccional (ACID). Emite `ReservaConfirmadaEvent`. Es el
   único servicio con acceso a PostgreSQL y Redis.
2. **`notification-service`** — consumidor puro de `ReservaConfirmadaEvent` vía
   Kafka. Hoy simula el envío del boleto. Diseñado para que en el futuro
   convivan otros canales de notificación (email, SMS, push, etc.) sin
   modificar el núcleo.

`common` contiene las piezas compartidas por ambos servicios: el evento
`ReservaConfirmadaEvent` y los DTOs/contratos de dominio.

## Stack tecnológico

| Área | Tecnología |
|------|-----------|
| Lenguaje | Java 21 (LTS) |
| Build | Maven (wrapper `./mvnw`) |
| Framework | Spring Boot 3.2+ |
| Persistencia relacional | PostgreSQL 16 (Spring Data JPA + Flyway) |
| Caché y concurrencia | Redis 7 (Spring Data Redis) |
| Mensajería asíncrona | Apache Kafka |
| Documentación de API | springdoc-openapi v2 (OpenAPI 3) |
| Tests de integración | Testcontainers (Postgres, Redis y Kafka reales) |
| Infraestructura local | Docker & Docker Compose |
| Despliegue cloud | AWS (ECS Fargate / App Runner) |

## Estrategia de concurrencia

La solución se resuelve en dos niveles:

### Fase de selección — lock distribuido (Redis)

- Clave: `lock:funcion:{funcionId}:asiento:{asientoId}`.
- TTL: 5 minutos (300000 ms).
- Adquisición atómica: `SET key value NX PX <ttl>`, sin condición de carrera.
  Se rechaza con `409` si la clave ya existe.
- Liberación: `DEL` de la clave (solo por el propietario o al confirmar).
- Si el TTL expira sin confirmación, el asiento vuelve a quedar disponible.

### Fase de pago y persistencia — transacción ACID

Al confirmar la compra se ejecuta una transacción en PostgreSQL con bloqueo
pesimista (`@Lock(LockModeType.PESSIMISTIC_WRITE)` / `SELECT ... FOR UPDATE`)
sobre la fila del asiento:

1. Verificar que el asiento está `DISPONIBLE`.
2. Procesar el pago vía `PaymentGatewayMock`.
3. Éxito → asiento `VENDIDO`, `Reserva` (`PAGADA`), liberar lock, emitir
   `ReservaConfirmadaEvent`.
4. Fallo → `Reserva` (`FALLIDA`), liberar lock, retornar `422`.

## Modelo de dominio

```
Evento (1) ──< Funcion (n) ──< Asiento (n)
```

- **`Evento`** — identifica el espectáculo/concierto.
- **`Funcion`** — una fecha/horario concreto de un evento.
- **`Asiento`** — un asiento concreto dentro de una función, con estado
  `DISPONIBLE` o `VENDIDO`.

## Contrato de API

Estilo RPC-action (verbos en la URL), base `/api/v1`.

| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET` | `/api/v1/eventos/{eventoId}/funciones/{funcionId}/asientos` | Consultar disponibilidad de asientos de una función. |
| `POST` | `/api/v1/reservas/bloquear` | Bloquear un asiento (lock Redis, sin fila en BD). |
| `POST` | `/api/v1/reservas/confirmar` | Confirmar pago: transacción ACID, asiento a `VENDIDO`. |
| `POST` | `/api/v1/reservas/liberar` | Cancelación manual: libera el lock Redis. |

Los errores se devuelven de forma centralizada bajo el estándar RFC 7807
(*Problem Details*).

La documentación OpenAPI se expone en `/v3/api-docs` y la Swagger UI en
`/swagger-ui.html`.

## Estructura del repositorio

```
ticket-flow/
├── pom.xml                     # parent POM (dependencyManagement, plugins de calidad)
├── mvnw / .mvn                 # Maven wrapper
├── config/checkstyle/checkstyle.xml
├── docker-compose.yml          # Postgres 16 + Redis 7 (+ Kafka)
├── common/                     # contrato de dominio compartido (eventos, DTOs)
├── reservation-service/        # dominio, locks, pago, ACID, API
└── notification-service/       # listener Kafka, simulación de envío
```

## Requisitos previos

- JDK 21 (LTS).
- Docker y Docker Compose (para la infraestructura local y los tests con
  Testcontainers).

### Nota: Testcontainers con Docker Desktop (Linux/macOS)

Los tests de integración de las Fases 2 y 3 usan **Testcontainers** para
levantar un PostgreSQL 16 real. Si usas **Docker Desktop**, el socket que
Testcontainers necesita no es `docker.sock` sino el socket "raw" del motor,
y además el contenedor de limpieza `ryuk` no puede arrancar sobre él. Para
que `./mvnw test` funcione en una instalación de Docker Desktop ajena, hay
que configurar dos cosas fuera del repositorio:

1. Apuntar Testcontainers al socket raw del motor creando (o editando)
   `~/.testcontainers.properties`:

   ```properties
   docker.host=unix\:///home/TU_USUARIO/.docker/desktop/docker.raw.sock
   ```

   > El path exacto puede variar entre versiones de Docker Desktop. Puedes
   > descubrirlo con `ls ~/.docker/desktop/*.sock` y verificarlo con
   > `curl --unix-socket <path> http://localhost/info` (debe devolver un
   > JSON con `ContainersRunning` y `Images` poblados).

2. Desactivar `ryuk` (el reaper de Testcontainers) al ejecutar los tests,
   porque no puede montarse sobre el socket raw:

   ```bash
   TESTCONTAINERS_RYUK_DISABLED=true ./mvnw test
   ```

Si en vez de Docker Desktop usas un `dockerd` nativo (socket
`/var/run/docker.sock` accesible y con API ≥ 1.40), no hace falta ninguno de
estos ajustes. En ese caso Testcontainers funciona con la configuración por
defecto.

## Puesta en marcha

1. Levantar la infraestructura local:

   ```bash
   docker compose up -d
   ```

   Esto arranca los contenedores:

   | Servicio | Contenedor | Puerto | Acceso |
   |----------|-----------|--------|--------|
   | PostgreSQL 16 | `ticketflow-postgres` | `5433` | `jdbc:postgresql://localhost:5433/ticketflow` |
   | Redis 7 | `ticketflow-redis` | `6379` | `redis://localhost:6379` |

   Credenciales por defecto de PostgreSQL: usuario `ticketflow`, contraseña
   `ticketflow`, base de datos `ticketflow`.

   > El puerto del host del contenedor es `5433` (mapeado a `5432` interno)
   > para no chocar con un PostgreSQL local que ya use el 5432.

   > En Fase 4 se agregará el servicio Kafka al mismo archivo.

2. Compilar y empaquetar todos los módulos:

   ```bash
   ./mvnw package
   ```

3. Ejecutar los servicios (opcional, según el módulo):

   ```bash
   ./mvnw -pl reservation-service spring-boot:run
   ./mvnw -pl notification-service spring-boot:run
   ```

## Build, calidad y seguridad

| Comando | Descripción |
|---------|-------------|
| `./mvnw test` | Ejecuta los tests (unitarios + integración con Testcontainers). |
| `./mvnw checkstyle:check` | Verifica el estilo de código. |
| `./mvnw dependency-check:check` | Analiza dependencias contra CVEs (OWASP). |
| `./mvnw spotbugs:check` | Análisis estático de seguridad (SpotBugs + Find Security Bugs). |
| `./mvnw verify` | Build completo: tests + calidad + seguridad. |

La cobertura se mide con JaCoCo (sin umbral que falle el build).

## Documentación

- **Constitución** — `docs/constitution.md` (principios innegociables).
- **Spec activa** — `docs/specs/0001-ticketflow-engine.md`.
- **Planes** — `docs/plans/`.
