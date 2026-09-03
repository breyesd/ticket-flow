# TicketFlow Engine — Plan de Implementación (Maestro)

> **Para trabajadores agénticos:** REQUIRED SUB-SKILL: usar
> `superpowers:subagent-driven-development` (recomendado) o
> `superpowers:executing-plans` para implementar este plan tarea a tarea.
> Los pasos usan sintaxis de checkbox (`- [ ]`) para seguimiento.

**Goal:** Construir TicketFlow, una plataforma backend distribuida de
reservas y venta de entradas en tiempo real, resolviendo el problema de
*double-booking* con bloqueo distribuido en Redis y persistencia
transaccional ACID en PostgreSQL.

**Architecture:** Dos microservicios (`reservation-service` y
`notification-service`) en un repositorio Maven multi-module con un módulo
compartido (`common`). El servicio de reservas gestiona selección, locks
Redis, pago y persistencia ACID, y emite `ReservaConfirmadaEvent` a Kafka.
El servicio de notificaciones consume el evento y simula el envío del
boleto.

**Tech Stack:** Java 21, Spring Boot 3.2+, Maven, PostgreSQL 16, Redis 7,
Apache Kafka, Flyway, springdoc-openapi v2, Testcontainers, Docker.

**Spec:** `docs/specs/0001-ticketflow-engine.md` — el plan argumenta desde la
spec; quien ejecuta debe leer ambos.

## Global Constraints

- Java 21 (LTS); solo bibliotecas confiables sin CVEs; pedir antes de
  añadir una dependencia nueva no declarada en la spec.
- Build con Maven: `./mvnw test` (tests), `./mvnw verify` (build + calidad
  + seguridad), `./mvnw checkstyle:check`, `./mvnw spotbugs:check`,
  `./mvnw dependency-check:check`.
- Código e identificadores en **inglés**; mensajes al usuario y
  documentación en **español**.
- Manejo de errores centralizado con `@RestControllerAdvice` bajo RFC 7807
  (Problem Details).
- Métodos de persistencia anotados con `@Transactional` (`readOnly = true`
  en consultas).
- Atomicidad en Redis con comandos atómicos (`SET ... NX PX`) o Lua.
- Datos semilla vía migración Flyway (no `data.sql`).
- Tests de integración con Testcontainers (Postgres, Redis y, en Fase 4,
  Kafka reales).
- Documentación de API con springdoc-openapi (`@Operation`, `@Schema`).
- Cobertura con JaCoCo sin umbral que rompa el build.

## Estructura del Repositorio (multi-module)

```
ticket-flow/
├── pom.xml                     # parent POM (dependencyManagement, plugins de calidad)
├── mvnw / .mvn                 # Maven wrapper
├── config/checkstyle/checkstyle.xml
├── docker-compose.yml          # Postgres 16 + Redis 7 (+ Kafka en Fase 4)
├── common/                     # contrato de dominio compartido (eventos, DTOs, excepciones base)
│   └── pom.xml
├── reservation-service/
│   ├── pom.xml
│   └── src/
│       ├── main/java/...       # dominio, locks, pago, ACID, API
│       ├── main/resources/db/migration/
│       └── test/java/...       # unitarios + Testcontainers
└── notification-service/
    ├── pom.xml
    └── src/main/java/...       # listener Kafka, simulación de envío
```

`common` contiene las piezas compartidas por ambos servicios: el evento
`ReservaConfirmadaEvent` y DTOs/contratos que ambos necesitan sin
duplicación.

## Mapa de Timeline (Fases → Tasks → Entregables → Orden)

| ID | Tarea | Entregable | Depende de |
|----|-------|-----------|-------------|
| **Fase 0 — Scaffold** (plan maestro) |  |  |  |
| F0.T1 | Estructura multi-module Maven + parent POM + wrapper | Repo compila vacío con 3 módulos | — |
| F0.T2 | Config de calidad: checkstyle, spotbugs, dependency-check, JaCoCo | `./mvnw verify` corre con plugins | F0.T1 |
| F0.T3 | `docker-compose.yml` (Postgres + Redis) + README de arranque | Infra local levanta con `docker compose up` | F0.T1 |
| **Fase 1 — Infraestructura Base y Modelado** |  |  |  |
| F1.T1 | Dependencias Spring Boot (Web, JPA, Redis, Postgres, Flyway, springdoc, Testcontainers) | `reservation-service` arranca | F0.T1 |
| F1.T2 | Migración Flyway inicial + entidades `Evento`, `Funcion`, `Asiento` | Esquema en BD + entidades JPA | F1.T1 |
| F1.T3 | Repositorios JPA + migración de seed (1 evento, 100 asientos) | Datos semilla cargados | F1.T2 |
| F1.T4 | Endpoint `GET /asientos` de disponibilidad | Consulta de disponibilidad funcionando | F1.T3 |
| **Fase 2 — Bloqueo Distribuido con Redis** |  |  |  |
| F2.T1 | Config `RedisTemplate` (serializadores JSON) | Conexión Redis tipada | F1.T1 |
| F2.T2 | `SeatLockService` con `acquireLock`/`releaseLock` atómicos | Lock distribuido unit tested | F2.T1 |
| F2.T3 | Endpoint `POST /reservas/bloquear` | Bloqueo de asiento expuesto | F2.T2, F1.T4 |
| F2.T4 | Endpoint `POST /reservas/liberar` | Liberación manual expuesta | F2.T2 |
| F2.T5 | Test de concurrencia (20 hilos, mismo asiento) con Testcontainers | Garantía de exclusión mutua validada | F2.T3 |
| **Fase 3 — Persistencia ACID y Confirmación de Compra** |  |  |  |
| F3.T1 | Entidad y repositorio `Reserva` (+ migración) | Persistencia de reservas | F1.T3 |
| F3.T2 | `@Lock(PESSIMISTIC_WRITE)` en repo de asientos | `SELECT ... FOR UPDATE` disponible | F3.T1 |
| F3.T3 | `PaymentGatewayMock` (éxito por defecto + switch de fallo) | Pago simulado configurable | — |
| F3.T4 | Endpoint `POST /reservas/confirmar` (transacción completa) | Compra completa end-to-end | F3.T2, F3.T3, F2.T2 |
| F3.T5 | Rollback y manejo de fallos (pago rechazado → 422) | Flujo de fallo validado | F3.T4 |
| **Fase 4 — Mensajería Asíncrona y Despliegue** |  |  |  |
| F4.T1 | `common`: `ReservaConfirmadaEvent` compartido | Contrato de evento | F0.T1 |
| F4.T2 | Kafka en `docker-compose.yml` + config productor/consumidor | Broker local + config Spring | F0.T3 |
| F4.T3 | Publicar `ReservaConfirmadaEvent` en `tickets.orders` | Productor integrado al confirmar | F4.T1, F3.T4 |
| F4.T4 | Listener en `notification-service` que simula envío | Consumidor end-to-end | F4.T1, F4.T2 |
| F4.T5 | Test end-to-end Kafka con Testcontainers | Flujo completo validado | F4.T3, F4.T4 |
| F4.T6 | `Dockerfile` multi-stage + doc de despliegue AWS | Artefacto desplegable + guía | F4.T4 |

## Detalle de la Fase 0 (Scaffold)

La Fase 0 se ejecuta **antes** de las fases 1–4 y su entregable es un
repositorio que compila y pasa `./mvnw verify` con la infra local lista.
No incluye lógica de negocio.

### F0.T1 — Estructura multi-module + parent POM

**Objetivo:** crear la estructura Maven multi-module con `common`,
`reservation-service` y `notification-service`, y el Maven wrapper
(`./mvnw`) funcionando.

**Pasos:**
- [X] Crear `pom.xml` padre (packaging `pom`) con `dependencyManagement`
      para Spring Boot 3.2+ (import BOM), el `spring-boot-maven-plugin`, y
      los módulos `common`, `reservation-service`, `notification-service`.
- [X] Crear `pom.xml` de `common` (jar de librería, sin app main).
- [X] Crear `pom.xml` de `reservation-service` y `notification-service`
      (dependientes de `common` y del starter de Spring Boot).
- [X] Generar el Maven wrapper (`mvn wrapper:wrapper`) para fijar
      `./mvnw`.
- [X] Verificar: `./mvnw -q package` compila los 3 módulos sin errores.

**Criterios de aceptación:** el build multi-module funciona desde la raíz
y produce los 3 artefactos.

### F0.T2 — Config de calidad

**Objetivo:** conectar los plugins de calidad/seguridad del AGENTS.md al
build.

**Pasos:**
- [X] Añadir plugin de Checkstyle con ruleset propio en
      `config/checkstyle/checkstyle.xml`.
- [X] Añadir plugins de SpotBugs (+ findsecbugs) y OWASP
      dependency-check.
- [X] Añadir plugin JaCoCo (sin umbral que falle el build).
- [X] Enlazar todos a la fase `verify`.
- [X] Verificar: `./mvnw verify` ejecuta checkstyle, spotbugs y
      dependency-check sin errores.

**Criterios de aceptación:** `./mvnw verify` pasa completo con la config de
calidad activa.

### F0.T3 — docker-compose + README de arranque

**Objetivo:** infraestructura local levantable con un comando.

**Pasos:**
- [X] Crear `docker-compose.yml` con `postgres:16` (credenciales y volumen)
      y `redis:7`.
- [X] Documentar en README cómo levantar (`docker compose up`) y las
      credenciales/URLs de conexión por defecto.
- [X] Verificar: `docker compose up -d` levanta ambos contenedores sanos.

**Criterios de aceptación:** Postgres y Redis accesibles desde local.

## Definición de Hecho (por tarea y por fase)

Cada tarea termina con: spec actualizada si el comportamiento cambió,
tests en verde, calidad y seguridad revisadas, documentación actualizada.

Se considera la iteración completa cuando las Fases 0–4 cumplen la
"Definición de Hecho" de `specs/0001-ticketflow-engine.md` sección 10.

## Planes por Fase

- Fase 1: `docs/plans/2026-08-31-fase-1-infraestructura.md`
- Fase 2: `docs/plans/2026-08-31-fase-2-redis-lock.md`
- Fase 3: `docs/plans/2026-08-31-fase-3-persistencia-acid.md`
- Fase 4: `docs/plans/2026-08-31-fase-4-mensajeria-despliegue.md`