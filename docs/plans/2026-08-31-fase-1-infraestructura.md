# TicketFlow Engine — Plan Fase 1: Infraestructura Base y Modelado

**Spec:** `docs/specs/0001-ticketflow-engine.md` (secciones 2.3, 2.4, 2.5, 6, 7, 9 Fase 1)
**Pre-requisito:** Fase 0 (Scaffold) completada.

**Resumen:** dejar `reservation-service` arrancando, con el modelo de
dominio persistido (Evento → Función → Asiento), datos semilla cargados
por Flyway y el endpoint de disponibilidad funcionando. Sin locks ni pagos
aún.

## Dependencias y artefactos de esta fase

Artefactos que esta fase introduce (las fases siguientes los consumen):

- Entidades JPA: `Evento`, `Funcion`, `Asiento`.
- Enums de estado: `AsientoEstado` (`DISPONIBLE`, `VENDIDO`).
- Repositorios: `EventoRepository`, `FuncionRepository`, `AsientoRepository`.
- Endpoint: `GET /api/v1/eventos/{eventoId}/funciones/{funcionId}/asientos`.
- Migración de seed: 1 evento con 100 asientos distribuidos en funciones.

## F1.T1 — Dependencias y arranque de `reservation-service`

**Objetivo:** dejar el módulo `reservation-service` dependiente de los
starters de Spring Boot y verificable que arranca.

**Archivos:**
- Modificar: `reservation-service/pom.xml`
- Crear: clase de aplicación `TicketFlowReservationApplication` y
  `application.yml` base.

**Descripción:**
- Añadir starters: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`,
  `spring-boot-starter-data-redis`, driver `postgresql`, `flyway-core`
  (y `flyway-database-postgresql` si el runtime lo requiere), y
  `springdoc-openapi-starter-webmvc-ui` (v2 compatible con Boot 3.x).
- Declarar dependencias de test: `spring-boot-starter-test` y
  `testcontainers` (postgresql + redis).
- Crear la clase main `TicketFlowReservationApplication` con
  `@SpringBootApplication`.
- Crear `application.yml` con configuración base (datasource, redis,
  jpa ddl-auto `validate` para que el esquema lo gobierne Flyway, y
  springdoc habilitado).
- Los tests de integración con contenedores reales se definen en las
  tareas siguientes; aquí solo debe arrancar el contexto con la config por
  defecto.

**Criterios de aceptación:** `reservation-service` compila y el contexto
Spring arranca (test `@SpringBootTest` con la config de test apuntando a
contenedores desechables o a una base embebida).

## F1.T2 — Migración Flyway inicial + entidades JPA

**Objetivo:** crear el esquema de base de datos y las entidades de domino
`Evento`, `Funcion`, `Asiento` con su jerarquía.

**Archivos:**
- Crear: migración Flyway `V1__crear_eventos_funciones_asientos.sql` en
  `reservation-service/src/main/resources/db/migration/`.
- Crear: entidades `Evento`, `Funcion`, `Asiento` y enum `AsientoEstado`.

**Descripción:**
- La migración crea las tablas `evento`, `funcion` y `asiento` con sus
  columnas, claves primarias, y claves foráneas que reflejen
  `Evento (1) ─< Funcion (n) ─< Asiento (n)`.
- `asiento` incluye `numero` y `estado` (enum `AsientoEstado`), con valor
  por defecto `DISPONIBLE` y una restricción de unicidad por
  (`funcion_id`, `numero`).
- Las entidades JPA mapean esas tablas con relaciones `@ManyToOne` desde
  hijo a padre (o colección `@OneToMany` si se justifica; preferir el lado
  simple) y `Enumerated(EnumType.STRING)` para el enum.
- Usar `ddl-auto = validate` para que Hibernate verifique el esquema
  contra Flyway en vez de crearlo.

**Criterios de aceptación:** la migración se aplica y Hibernate valida el
mapeo sin discrepancias (test que arranca el contexto con Flyway activo).

## F1.T3 — Repositorios JPA + migración de seed

**Objetivo:** exponer acceso a datos y cargar datos semilla versionados.

**Archivos:**
- Crear: `EventoRepository`, `FuncionRepository`, `AsientoRepository`
  (Spring Data JPA).
- Crear: migración Flyway `V2__seed_evento_asientos.sql`.

**Descripción:**
- Los repositorios extienden `JpaRepository` y añaden las consultas
  mínimas necesarias: listar asientos por función, y buscar un asiento por
  `funcionId` + `numero` (necesario más adelante para el lock y el
  `FOR UPDATE`).
- La migración de seed inserta **1 evento** con **100 asientos**
  distribuidos en sus funciones (números del 1 al 100; si hay más de una
  función, repartidos indicando la función concreta en el propio SQL).
- No usar `data.sql`: todo a través de Flyway, de forma idempotente.

**Criterios de aceptación:** test de integración (Testcontainers con
Postgres) que verifica que tras aplicar las migraciones existen 1 evento y
100 asientos.

## F1.T4 — Endpoint de disponibilidad

**Objetivo:** exponer la consulta de asientos disponibles de una función.

**Archivos:**
- Crear: controlador (p. ej. `AsientoController`), DTO de respuesta
  (record `AsientoDisponibilidadDto`), y servicio/infra de consulta.

**Descripción:**
- Implementar `GET /api/v1/eventos/{eventoId}/funciones/{funcionId}/asientos`
  que valida que el evento y la función existen (si no, `404` Problem
  Details) y devuelve la lista de asientos con `numero` y `estado`.
- Consulta con `@Transactional(readOnly = true)`.
- El DTO de respuesta es un Java record anotado con `@Schema` para
  OpenAPI; el endpoint se anota con `@Operation`.
- Retornar `404` (RFC 7807) si evento o función no existen; usar el
  `@RestControllerAdvice` central para el formato del error.

**Criterios de aceptación:** test de integración que, contra Testcontainers,
consulta los asientos del evento/función sembrados y valida la forma de la
respuesta; y test de `404` para evento/función inexistente.

## Orden y commits sugeridos

1. F1.T1 → commit `feat(reservation-service): boot base con dependencias`
2. F1.T2 → commit `feat(reservation-service): modelo evento/funcion/asiento y migracion V1`
3. F1.T3 → commit `feat(reservation-service): repositorios y seed`
4. F1.T4 → commit `feat(reservation-service): endpoint de disponibilidad`

## Definición de Hecho de la fase

- `./mvnw test` en verde en `reservation-service`.
- Migraciones Flyway + seed aplicadas correctamente.
- Esquema y consulta de disponibilidad validados con Testcontainers.
- Calidad (`./mvnw checkstyle:check`) y seguridad (`./mvnw spotbugs:check`,
  `./mvnw dependency-check:check`) sin errores.
- Documentación OpenAPI generada en `/v3/api-docs` y `/swagger-ui.html`.