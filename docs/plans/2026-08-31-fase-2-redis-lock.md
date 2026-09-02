# TicketFlow Engine — Plan Fase 2: Bloqueo Distribuido con Redis

**Spec:** `docs/specs/0001-ticketflow-engine.md` (secciones 3.1, 2.5, 9 Fase 2)
**Pre-requisito:** Fase 1 completada.

**Resumen:** implementar el bloqueo distribuido en Redis para la fase de
selección de asientos, con adquisición y liberación atómicas, expuesto vía
API y validado con una prueba de concurrencia real.

## Artefactos que introduce esta fase

- `RedisTemplate` tipado con serializadores JSON.
- `SeatLockService` con `acquireLock(funcionId, asientoId, ttlMs)` y
  `releaseLock(funcionId, asientoId)`.
- Endpoints `POST /api/v1/reservas/bloquear` y `POST /api/v1/reservas/liberar`.
- Formato de clave de lock: `lock:funcion:{funcionId}:asiento:{asientoId}`.

## F2.T1 — Configurar `RedisTemplate` con serializadores JSON

**Objetivo:** dejar Redis accesible con serialización JSON y manejo de
conexiones consistente.

**Archivos:**
- Modificar: `reservation-service` config (`application.yml`) y crear una
  clase de configuración `RedisConfig`.

**Descripción:**
- Configurar `RedisTemplate<String, Object>` con `StringRedisSerializer`
  para las claves y un serializador JSON (p. ej. `GenericJackson2JsonRedisSerializer`
  o `Jackson2JsonRedisSerializer`) para los valores.
- Definir las propiedades de conexión a Redis (host/port desde
  `application.yml`, con valores por defecto para local).
- Exponer el `RedisTemplate` como bean inyectable para el servicio de lock.
- Añadir la dependencia `spring-boot-starter-data-redis` (ya declarada en
  F1.T1) y verificar la conexión en un test de integración con Testcontainers
  (Redis real).

**Criterios de aceptación:** test que escribe/lee una clave con el
serializador configurado y lee el valor tipado correctamente.

## F2.T2 — `SeatLockService` con operaciones atómicas

**Objetivo:** encapsular la adquisición/liberación de locks con
atomicidad.

**Archivos:**
- Crear: `SeatLockService` (y su interfaz si se separa contrato de impl).

**Descripción:**
- `acquireLock(String funcionId, String asientoId, long ttlMs)`:
  - Construye la clave `lock:funcion:{funcionId}:asiento:{asientoId}`.
  - Ejecuta `SET key value NX PX ttlMs` (adquirir solo si no existe, con
    expiración). Devuelve un resultado booleano/`Optional` que indica si el
    lock se obtuvo (nunca sobrescribe un lock existente).
  - El `value` identifica al propietario del lock (token de la reserva),
    para poder liberar solo el lock propio.
- `releaseLock(String funcionId, String asientoId)`:
  - Libera la clave con `DEL`, preferiblemente verificando el propietario
    con un script Lua para evitar liberar un lock ajeno (atomicidad).
- Otras operaciones: `isLocked(...)` para consultas si la API lo necesita.
- Criterio de test de mayor interés: dos adquisiciones consecutivas del
  mismo asiento — la segunda debe fallar; tras liberar, una nueva
  adquisición debe tener éxito.

**Criterios de aceptación:** tests unitarios (con un `RedisTemplate`
mockeado o un servidor Redis embebido) que validan exclusión mutua,
expiración y liberación por propietario.

## F2.T3 — Endpoint `POST /reservas/bloquear`

**Objetivo:** exponer el bloqueo de un asiento para la fase de selección.

**Archivos:**
- Crear: controlador `ReservaController` (o `BloqueoController`), DTOs de
  request/response (records), y el flujo de validación.

**Descripción:**
- `POST /api/v1/reservas/bloquear` recibe `funcionId` y `asientoId`
  (y opcionalmente identificador de usuario).
- Valida que la función y el asiento existen; si no, `404` Problem Details.
- Valida que el asiento está `DISPONIBLE` en BD; si está `VENDIDO`,
  rechaza.
- Llama a `acquireLock(funcionId, asientoId, 300000)` (TTL 5 minutos):
  - Si el lock ya existe → `409` (asiento ya bloqueado).
  - Si se obtiene → devuelve `reservationId` efímero (token de propietario,
    sin crear fila en BD) con `200`/`201`.
- El `reservationId` es el token que se usará en Fase 3 para confirmar.

**Criterios de aceptación:** test de integración (Testcontainers Redis +
Postgres) que bloquea un asiento libre, y test de `409` al bloquear de
nuevo el mismo asiento.

## F2.T4 — Endpoint `POST /reservas/liberar`

**Objetivo:** permitir cancelación manual liberando el lock.

**Archivos:**
- Modificar: `ReservaController`; DTO de request de liberación.

**Descripción:**
- `POST /api/v1/reservas/liberar` recibe `funcionId`, `asientoId` y el
  `reservationId` (propietario).
- Llama a `releaseLock` verificando el propietario.
- Casos: si no hay lock → no-op o `404` según se defina (preferir no-op
  idempotente); si el lock pertenece a otro propietario → `409`.
- Mantener consistencia con el manejo de errores central (RFC 7807).

**Criterios de aceptación:** test que bloquea y luego libera, verificando
que una nueva adquisición del mismo asiento tiene éxito; y test de
intento de liberación por no-propietario.

## F2.T5 — Prueba de concurrencia (20 hilos)

**Objetivo:** validar la exclusión mutua real ante concurrencia.

**Archivos:**
- Crear: test de integración de concurrencia en
  `reservation-service/src/test`.

**Descripción:**
- Test de integración con Testcontainers (Redis + Postgres reales) que
  dispara **20 hilos simultáneos** intentando `acquireLock` sobre el
  **mismo asiento**.
- Verificar que **exactamente 1** hilo obtiene el lock y los otros 19
  fallan (método de exclusión mutua garantizado por `SET NX`).
- Tras liberar el lock, verificar que un nuevo intento tiene éxito.
- Usar un `CountDownLatch`/`ExecutorService` para lanzar los 20 hilos a la
  vez y recolectar cuántos tuvieron éxito.

**Criterios de aceptación:** el test en verde demuestra que solo un hilo
adquiere el lock y el resto recibe rechazo, sin condiciones de carrera.

## Orden y commits sugeridos

1. F2.T1 → commit `feat(reservation-service): config de RedisTemplate`
2. F2.T2 → commit `feat(reservation-service): SeatLockService atomico`
3. F2.T3 → commit `feat(reservation-service): endpoint bloquear asiento`
4. F2.T4 → commit `feat(reservation-service): endpoint liberar asiento`
5. F2.T5 → commit `test(reservation-service): prueba de concurrencia de lock`

## Definición de Hecho de la fase

- `./mvnw test` en verde, incluida la prueba de concurrencia.
- Exclusión mutua y expiración del lock validadas con Redis real
  (Testcontainers).
- Endpoints `bloquear`/`liberar` documentados con OpenAPI y con manejo de
  errores RFC 7807.
- Calidad y seguridad (`checkstyle`, `spotbugs`, `dependency-check`) sin
  errores.