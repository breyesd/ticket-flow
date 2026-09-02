# Spec 0001 — TicketFlow Engine

Estado: Borrador (pendiente de revisión)

## 1. Visión y Objetivo

TicketFlow es una plataforma backend distribuida de reservas y venta de
entradas en tiempo real para eventos de alta concurrencia. El objetivo
principal es resolver el problema de compra concurrente de asientos
(*double-booking problem*) garantizando consistencia estricta, baja
latencia y tolerancia a fallos mediante una arquitectura desacoplada y
basada en eventos.

## 2. Decisiones de Diseño

Este documento fija las decisiones de diseño para la primera iteración.
Toda decisión aquí es innegociable salvo que se actualice esta spec.

### 2.1 Stack Tecnológico

- **Lenguaje:** Java 21 (LTS).
- **Build:** Maven (toda la tooling de calidad del proyecto está ligada a
  Maven: checkstyle, spotbugs, dependency-check, JaCoCo, `./mvnw`).
- **Framework:** Spring Boot 3.2+.
- **Persistencia relacional:** PostgreSQL 16 (Spring Data JPA + Flyway).
- **Caché y concurrencia distribuida:** Redis 7 (Spring Data Redis).
- **Mensajería asíncrona:** Apache Kafka.
- **Documentación de API:** springdoc-openapi v2 (OpenAPI 3).
- **Infraestrutura local:** Docker & Docker Compose.
- **Tests de integración:** Testcontainers (Postgres y Redis reales).
- **Despliegue cloud:** AWS (ECS Fargate / App Runner) — documentado, no
  bloqueante para esta iteración (ver sección 8).

### 2.2 Arquitectura: Microservicios

Dos servicios para la primera iteración:

1. **`reservation-service`** — núcleo del dominio. Maneja la selección de
   asientos, los locks distribuidos en Redis, el procesamiento de pago y
   la persistencia transaccional (ACID). Emite `ReservaConfirmadaEvent`.
   Único servicio con acceso a PostgreSQL y Redis.
2. **`notification-service`** — consumidor puro de
   `ReservaConfirmadaEvent` vía Kafka. Hoy simula el envío del boleto.
   Diseñado para que en el futuro convivan otros servicios de notificación
   (email, SMS, push, etc.) sin modificar el núcleo.

Este desglose permite extensibilidad del canal de notificación sin
romper el `reservation-service` ni agregar complejidad al flujo de compra.

### 2.3 Modelo de Dominio

Jerarquía de tres niveles:

```
Evento (1) ──< Funcion (n) ──< Asiento (n)
```

- **`Evento`** — identifica el espectáculo/concierto (`id`, `nombre`,
  descripción, etc.).
- **`Funcion`** — una fecha/horario concreto de un evento (`id`,
  `evento_id`, `fecha_hora`).
- **`Asiento`** — un asiento concreto dentro de una función (`id`,
  `funcion_id`, `numero`, `estado`).

### 2.4 Estados

**`Asiento.estado`** (persistido en PostgreSQL):

- `DISPONIBLE` — libre para reservar.
- `VENDIDO` — vendido definitivamente tras pago exitoso.

> El estado de bloqueo durante la selección **NO** se persiste en BD.
> Vive únicamente en Redis como clave con TTL. Ver sección 5.

**`Reserva.estado`** (persistido en PostgreSQL):

- `PAGADA` — pago exitoso; asiento en `VENDIDO`.
- `FALLIDA` — pago rechazado; lock liberado; asiento vuelve a
  `DISPONIBLE`.

> Nota: no existe estado `PENDIENTE` persistido. La `Reserva` se crea en
> `confirmar` directamente con estado `PAGADA` o `FALLIDA`. El estado de
> "intención de reserva" vive solo en Redis como lock efímero hasta la
> confirmación.

### 2.5 Contrato de API

Estilo RPC-action (verbos en la URL), base `/api/v1`.

Endpoints de `reservation-service`:

| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET`  | `/api/v1/eventos/{eventoId}/funciones/{funcionId}/asientos` | Consultar disponibilidad de asientos de una función. |
| `POST` | `/api/v1/reservas/bloquear` | Bloquear un asiento (solo lock Redis, sin fila en BD). Devuelve `reservationId` efímero. |
| `POST` | `/api/v1/reservas/confirmar` | Confirmar pago: transacción ACID, crea `Reserva`, asiento a `VENDIDO`, libera lock, emite evento. |
| `POST` | `/api/v1/reservas/liberar` | Cancelación manual: libera lock Redis. |

Códigos de respuesta de dominio (RFC 7807):

- `404` — asiento/función/evento no existe.
- `409` — asiento ya bloqueado (`lock` existente en Redis).
- `409` — asiento ya `VENDIDO` o lock expirado al confirmar.
- `422` — validación de payload, o pago rechazado.
- `503` — fallo del proveedor de pago (mock) no controlado.

> Para "pago rechazado" se usa `422 Unprocessable Entity`.

### 2.6 Manejo de Errores

Centralizado mediante `@RestControllerAdvice`, retornando respuestas
consistentes bajo el estándar RFC 7807 (*Problem Details*): campos
`type`, `title`, `status`, `detail`, `instance`.

## 3. Estrategia de Concurrencia (Doble Nivel)

### 3.1 Fase de Selección — Lock Distribuido (Redis)

- Clave: `lock:funcion:{funcionId}:asiento:{asientoId}`.
- TTL: 5 minutos (300000 ms).
- Adquisición atómica: `SET key value NX PX <ttl>` (sin condición de
  carrera). Se rechaza con `409` si la clave ya existe.
- Liberación: `DEL` de la clave (solo a petición del propietario o al
  confirmar).
- **Expiración natural:** si el TTL expira sin confirmación, el asiento
  vuelve a quedar disponible automáticamente, porque el bloqueo vive solo
  en Redis y no hubo transacción pendiente en BD. No se requiere proceso
  "sweeper".

### 3.2 Fase de Pago y Persistencia — Transacción ACID

Al confirmar la compra:

1. Transacción en PostgreSQL con bloqueo pesimista
   (`@Lock(LockModeType.PESSIMISTIC_WRITE)` / `SELECT ... FOR UPDATE`)
   sobre la fila del asiento.
2. Verificar que el asiento está `DISPONIBLE`.
3. Procesar pago vía `PaymentGatewayMock`.
4. Si éxito: asiento a `VENDIDO`, crear `Reserva` (`PAGADA`), liberar lock
   Redis, emitir `ReservaConfirmadaEvent`.
5. Si fallo: `Reserva` (`FALLIDA`), liberar lock, retornar `422`.

## 4. Mensajería Asíncrona

- Al completarse una compra exitosa, `reservation-service` publica el
  evento `ReservaConfirmadaEvent` en el topic Kafka `tickets.orders`.
- `notification-service` consume `tickets.orders` y registra/simula el
  envío del boleto (correo). Es el único consumidor en esta iteración.

## 5. Manejo del Pago (Mock)

`PaymentGatewayMock` simula el procesamiento de pago:

- Por defecto, **éxito**.
- Switch de configuración (property) permite forzar **fallo** para tests
  y pruebas de regresión del flujo de rollback.

## 6. Persistencia y Datos Semilla

- Migraciones con Flyway versionadas en `src/main/resources/db/migration`.
- Datos semilla cargados mediante **migración Flyway** (no `data.sql`):
  1 evento, y 100 asientos distribuidos en sus funciones.

## 7. Testing

- **Tests unitarios** (`mvn test`) para la lógica del dominio y servicios.
- **Tests de integración con Testcontainers** (Postgres y Redis reales)
  para validar:
  - El lock distribuido (clave con TTL, atomicidad).
  - El `SELECT ... FOR UPDATE` y la transacción ACID.
  - **Prueba de concurrencia:** simular N hilos simultáneos (20) sobre el
    mismo asiento y verificar que solo uno obtiene el lock / la compra.
- El `PaymentGatewayMock` permite inyectar fallos de pago para testear el
  rollback.

## 8. Despliegue (Alcance)

Primera iteración:

- `docker-compose.yml` con PostgreSQL 16, Redis 7 y Kafka.
- `Dockerfile` multi-stage optimizado (Eclipse Temurin / Distroless).
- Despliegue AWS (ECR, ECS Fargate / App Runner, ALB) documentado como
  guía, no como entregable bloqueante de esta iteración.

## 9. Fases de Ejecución

### Fase 1 — Infraestructura Base y Modelado
1.1. Inicializar proyecto Maven con dependencias (Web, JPA, Redis,
     Postgres, Flyway, springdoc, Testcontainers).
1.2. `docker-compose.yml` con PostgreSQL 16 y Redis 7 (y Kafka en Fase 4).
1.3. Migraciones Flyway y entidades JPA `Evento`, `Funcion`, `Asiento`.
1.4. Repositorios Spring Data JPA y migración de seed (1 evento, 100
     asientos).
1.5. Endpoint `GET /api/v1/eventos/{eventoId}/funciones/{funcionId}/asientos`.

### Fase 2 — Bloqueo Distribuido con Redis
2.1. Configurar `RedisTemplate` con serializadores JSON y manejo de
     conexiones.
2.2. `SeatLockService` con `acquireLock(funcionId, asientoId, ttlMs)` y
     `releaseLock(funcionId, asientoId)` atómicos.
2.3. Endpoint `POST /api/v1/reservas/bloquear` con validación y lock de 5 min.
2.4. Endpoint `POST /api/v1/reservas/liberar`.
2.5. Prueba de integración de concurrencia (20 hilos sobre el mismo
     asiento) con Testcontainers.

### Fase 3 — Persistencia ACID y Confirmación de Compra
3.1. Entidad y repositorio `Reserva`.
3.2. `@Lock(LockModeType.PESSIMISTIC_WRITE)` en el repositorio de asientos.
3.3. `PaymentGatewayMock` (éxito por defecto + switch de fallo).
3.4. Endpoint `POST /api/v1/reservas/confirmar` con transacción completa.
3.5. Rollback y manejo de fallos si el pago es rechazado (`422`).

### Fase 4 — Mensajería Asíncrona y Despliegue
4.1. Agregar Kafka a `docker-compose.yml` y configurar productor/consumidor.
4.2. Publicar `ReservaConfirmadaEvent` en `tickets.orders`.
4.3. Listener en `notification-service` que consume y simula el envío del
     boleto.
4.4. `Dockerfile` multi-stage (Eclipse Temurin / Distroless).
4.5. Documentación de despliegue AWS (no bloqueante).

## 10. Definición de Hecho

Una fase termina cuando:

- La spec activa está actualizada si el comportamiento cambió.
- Los tests están en verde (`./mvnw test`).
- La calidad pasa (`./mvnw checkstyle:check`).
- La seguridad pasa (`./mvnw dependency-check:check` y
  `./mvnw spotbugs:check`).
- La documentación de API (OpenAPI) y el README están actualizados.

Código e identificadores en inglés; mensajes al usuario y documentación en
español.