# TicketFlow Engine — Plan Fase 3: Persistencia ACID y Confirmación de Compra

**Spec:** `docs/specs/0001-ticketflow-engine.md` (secciones 3.2, 2.4, 2.5, 5, 9 Fase 3)
**Pre-requisito:** Fase 1 y Fase 2 completadas.

**Resumen:** implementar la fase de pago y persistencia: transacción ACID
con bloqueo pesimista sobre el asiento, pago simulado, y el endpoint
`confirmar` que completa la venta de forma atómica y consistente.

## Artefactos que introduce esta fase

- Entidad y repositorio `Reserva` (con migración de tabla).
- Consulta con bloqueo pesimista (`SELECT ... FOR UPDATE`) en asientos.
- `PaymentGatewayMock` (éxito por defecto, switch de fallo).
- Endpoint `POST /api/v1/reservas/confirmar`.
- Estados de `Reserva`: `PAGADA`, `FALLIDA` (enum `ReservaEstado`).

## F3.T1 — Entidad y repositorio `Reserva`

**Objetivo:** modelar y persistir la reserva resultante de una compra.

**Archivos:**
- Crear: migración Flyway `V3__crear_reserva.sql`.
- Crear: entidad `Reserva`, enum `ReservaEstado`, y `ReservaRepository`.

**Descripción:**
- La tabla `reserva` incluye: `id`, `usuario_id`, `asiento_id` (FK a
  `asiento`), `monto`, `estado`, `fecha_creacion`.
- Enum `ReservaEstado` con valores `PAGADA` y `FALLIDA` (no existe
  `PENDIENTE`; ver spec sección 2.4).
- Restricción de unicidad que impida dos reservas `PAGADA` sobre el mismo
  asiento como refuerzo de la consistencia.
- `ReservaRepository` extiende `JpaRepository` con consultas mínimas (p. ej.
  buscar por `asiento_id`).

**Criterios de aceptación:** la migración se aplica y la entidad se mapea
correctamente (test de contexto con Flyway).

## F3.T2 — Bloqueo pesimista en el repositorio de asientos

**Objetivo:** disponer de `SELECT ... FOR UPDATE` sobre el asiento para la
transacción de confirmación.

**Archivos:**
- Modificar: `AsientoRepository`.

**Descripción:**
- Añadir un método de consulta que recupere un asiento con bloqueo
  pesimista de escritura, p. ej.:
  `findByIdForUpdate(Long id)` anotado con
  `@Lock(LockModeType.PESSIMISTIC_WRITE)` y su correspondiente `@Query`.
- Este método debe usarse **solo dentro de una transacción** por lo que el
  caso de uso de confirmación lo invocará desde un método `@Transactional`.

**Criterios de aceptación:** test de integración que, dentro de una
transacción, adquiere el bloqueo sobre una fila y verifica que una segunda
transacción concurrente se bloquea hasta que la primera termina.

## F3.T3 — `PaymentGatewayMock`

**Objetivo:** simular el procesamiento de pago de forma predecible y
configurable.

**Archivos:**
- Crear: interfaz `PaymentGateway` y su implementación `PaymentGatewayMock`.

**Descripción:**
- `PaymentGateway` expone un método, p. ej.
  `PaymentResult charge(PaymentRequest request)`.
- `PaymentGatewayMock` devuelve **éxito por defecto**.
- Un switch de configuración (property, p. ej.
  `app.payment.mock.fail=true`) fuerza el **fallo** (resultado rechazado)
  para pruebas de rollback.
- `PaymentResult` indica éxito/fallo y, si falla, un motivo para mapear a
  la respuesta `422`.

**Criterios de aceptación:** tests unitarios que validan éxito por defecto
y fallo al activar el switch.

## F3.T4 — Endpoint `POST /reservas/confirmar`

**Objetivo:** ejecutar la transacción completa de compra de forma atómica.

**Archivos:**
- Crear: caso de uso/servicio de confirmación (`ReservaService` o similar)
  y DTOs de request/response.
- Modificar: controlador de reservas.

**Descripción:**
- `POST /api/v1/reservas/confirmar` recibe `reservationId` (el token del
  lock obtenido en `bloquear`), `funcionId`, `asientoId`, `usuarioId` y
  `monto`.
- Verifica que el lock pertenece al `reservationId` presentado; si expiró o
  no existe → `409`.
- Dentro de una transacción `@Transactional`:
  1. `findByIdForUpdate` sobre el asiento (`SELECT ... FOR UPDATE`).
  2. Verifica que el asiento está `DISPONIBLE`; si está `VENDIDO` → `409` y
     rollback.
  3. Cobra vía `PaymentGateway`:
     - Éxito → asiento a `VENDIDO`, crear `Reserva` (`PAGADA`) con `monto`,
       emitir el evento de dominio (en Fase 4; aquí puede dejarse un hook/
       puerto para emisión), y liberar el lock Redis.
     - Fallo → crear `Reserva` (`FALLIDA`), liberar el lock, retornar `422`.
- Orden de liberación: soltar el lock Redis tras confirmar la persistencia,
  dentro del mismo flujo (liberación best-effort; si falla, el TTL lo
  recupera).
- Emplear el `@RestControllerAdvice` para mapear errores a RFC 7807.

**Criterios de aceptación:** test de integración con Testcontainers que
completa una compra (asiento libre → `VENDIDO`, reserva `PAGADA`, lock
liberado) y verifica el estado final en BD.

## F3.T5 — Rollback y manejo de fallos de pago

**Objetivo:** validar el comportamiento consistente ante pago rechazado.

**Archivos:**
- Crear: tests de integración del flujo de fallo.

**Descripción:**
- Con el switch de fallo activo, verificar que al confirmar:
  - No se marca el asiento como `VENDIDO` (permanece `DISPONIBLE`).
  - Se crea `Reserva` con estado `FALLIDA`.
  - El lock se libera y el asiento queda disponible para un nuevo intento.
  - La respuesta es `422`.
- Verificar también el caso de asiento ya `VENDIDO` (segundo intento) que
  retorna `409` sin efectos.
- Verificar idempotencia/consistencia si se reintenta confirmar el mismo
  `reservationId` tras un éxito.

**Criterios de aceptación:** tests en verde que demuestran el rollback y
que el estado de BD queda consistente tras el fallo.

## Orden y commits sugeridos

1. F3.T1 → commit `feat(reservation-service): entidad reserva y migracion V3`
2. F3.T2 → commit `feat(reservation-service): bloqueo pesimista en asientos`
3. F3.T3 → commit `feat(reservation-service): PaymentGatewayMock`
4. F3.T4 → commit `feat(reservation-service): endpoint confirmar compra`
5. F3.T5 → commit `test(reservation-service): rollback y fallos de pago`

## Definición de Hecho de la fase

- `./mvnw test` en verde, incluidos los tests de rollback.
- Compra completa validada end-to-end contra Postgres y Redis reales
  (Testcontainers).
- Métodos de persistencia `@Transactional` (consultas `readOnly = true`).
- Calidad y seguridad sin errores; OpenAPI actualizado con `confirmar`.