# TicketFlow Engine — Plan Fase 4: Mensajería Asíncrona y Despliegue

**Spec:** `docs/specs/0001-ticketflow-engine.md` (secciones 4, 2.2, 8, 9 Fase 4)
**Pre-requisito:** Fases 0–3 completadas.

**Resumen:** conectar ambos microservicios vía Kafka: `reservation-service`
publica `ReservaConfirmadaEvent` al confirmar la compra y
`notification-service` lo consume para simular el envío del boleto.
Cerrar con Dockerfile multi-stage y documentación de despliegue AWS.

## Artefactos que introduce esta fase

- `common`: el evento de dominio compartido `ReservaConfirmadaEvent`.
- Kafka en `docker-compose.yml` y configuración Spring de productor/consumidor.
- Productor integrado al flujo de confirmación y listener consumidor.
- `Dockerfile` multi-stage y documentación de despliegue.

## F4.T1 — Evento de dominio compartido `ReservaConfirmadaEvent`

**Objetivo:** definir el contrato del evento en el módulo `common`.

**Archivos:**
- Crear: `ReservaConfirmadaEvent` (record) en `common`.

**Descripción:**
- `ReservaConfirmadaEvent` es un Java record con los datos necesarios para
  notificar: `reservaId`, `eventoId`, `funcionId`, `asientoId`, `usuarioId`,
  `monto`, `fechaConfirmacion` (y cualquier dato del boleto que el
  consumidor requiera).
- Es el contrato serializado que viaja por Kafka; ambos servicios dependen
  de `common` para compartirlo sin duplicación.
- Definir el nombre del topic como constante compartida, p. ej.
  `TICKETS_ORDERS_TOPIC = "tickets.orders"`.

**Criterios de aceptación:** `common` compila y `ReservaConfirmadaEvent` es
serializable (JSON) con los campos definidos.

## F4.T2 — Kafka en docker-compose + config productor/consumidor

**Objetivo:** levantar Kafka local y configurar la conexión en Spring.

**Archivos:**
- Modificar: `docker-compose.yml` (añadir `kafka` y, si se usa, `zookeeper`
  o un broker sin zookeeper según la imagen elegida).
- Modificar: `application.yml` de ambos servicios (propiedades de
  productor/consumidor, serializadores JSON, group-id).

**Descripción:**
- Añadir al compose un servicio Kafka (imagen confiable, p. ej.
  `confluentinc/cp-kafka` en caso de requerir zookeeper, o una variante
  single-node KRaft) con su publicación de puerto y healthcheck.
- Configurar `spring-kafka` en `reservation-service` (productor) y
  `notification-service` (consumidor): serializador/deserializador JSON
  para el valor, `bootstrap-servers`, y un `group-id` para el consumidor.
- Añadir la dependencia `spring-kafka` en ambos `pom.xml`.

**Criterios de aceptación:** `docker compose up` levanta Kafka sano y los
contextos Spring conectan sin errores (test con Testcontainers Kafka).

## F4.T3 — Publicar `ReservaConfirmadaEvent` en `tickets.orders`

**Objetivo:** emitir el evento al confirmarse la compra.

**Archivos:**
- Crear: puerto/adaptador de publicación de eventos en
  `reservation-service` (p. ej. interfaz `EventPublisher` + implementación
  Kafka `KafkaReservaConfirmadaPublisher`).
- Modificar: `ReservaService` (flujo de confirmación de F3.T4) para invocar
  al publicador tras una compra exitosa.

**Descripción:**
- Tras una `Reserva` en estado `PAGADA`, construir el
  `ReservaConfirmadaEvent` y publicarlo en el topic `tickets.orders`.
- La publicación debe ocurrir **después** de confirmar la transacción
  (post-commit o inmediatamente tras persistir con éxito), para no emitir
  eventos de compras que luego fallen.
- Inyectar el publicador como una dependencia del caso de uso de
  confirmación (interface, no implementación concreta, para poder testear
  con un publicador fake).

**Criterios de aceptación:** test de integración con Testcontainers Kafka
que, al confirmar una compra, verifica que el evento llega al topic con los
datos correctos.

## F4.T4 — Listener en `notification-service`

**Objetivo:** consumir el evento y simular el envío del boleto.

**Archivos:**
- Crear: `notification-service` clase main (`TicketFlowNotificationApplication`),
  listener `@KafkaListener` y un servicio de "envío" simulado (log/registro).

**Descripción:**
- `@KafkaListener(topics = "tickets.orders")` que deserializa
  `ReservaConfirmadaEvent` y delega en un `NotificacionService`.
- `NotificacionService.simularEnvío(evento)` registra en log (y/o en una
  tabla/memoria) que el boleto del evento fue "enviado", sin lógica real de
  correo.
- Diseñado para que en el futuro convivan otros consumidores de
  notificación (email, SMS, push) sin tocar el core.

**Criterios de aceptación:** test de integración que publica un evento en
Kafka y verifica que el listener lo recibe y procesa (envío simulado
registrado).

## F4.T5 — Test end-to-end Kafka con Testcontainers

**Objetivo:** validar el flujo completo reservas → Kafka → notificaciones.

**Archivos:**
- Crear: prueba de integración end-to-end (puede vivir en un módulo de
  test de integración o en `reservation-service`/`notification-service`
  según conveniencia).

**Descripción:**
- Con Testcontainers (Postgres, Redis, Kafka) arrancar ambos servicios (o
  sus contextos) y ejecutar el flujo completo: bloquear → confirmar →
  evento en `tickets.orders` → consumido por `notification-service` →
  envío simulado registrado.
- Verificar el orden y la integridad de los datos de un extremo al otro.

**Criterios de aceptación:** el test en verde demuestra el flujo completo
sin intervención manual.

## F4.T6 — Dockerfile multi-stage + doc de despliegue AWS

**Objetivo:** empaquetar los servicios y documentar el despliegue cloud.

**Archivos:**
- Crear: `Dockerfile` multi-stage por servicio (base Eclipse Temurin /
  Distroless).
- Crear: documentación de despliegue (README o `docs/despliegue-aws.md`).

**Descripción:**
- `Dockerfile` multi-stage: etapa de build con JDK 21 (Temurin) que corre
  `./mvnw package`, y etapa runtime minimalista (Distroless o Temurin JRE)
  que arranca el jar con un usuario no-root.
- Documentar el despliegue en AWS (ECS Fargate / App Runner): build de
  imagen, push a ECR, task definition, variables de entorno para las
  conexiones a Postgres/Redis/Kafka gestionados, y notas de
  configuración.

**Criterios de aceptación:** la imagen se construye con `docker build` y
  corre; la guía de despliegue describe los pasos de ECR/ECS/App Runner.

## Orden y commits sugeridos

1. F4.T1 → commit `feat(common): ReservaConfirmadaEvent`
2. F4.T2 → commit `chore: kafka en docker-compose y config spring-kafka`
3. F4.T3 → commit `feat(reservation-service): publicar evento de reserva`
4. F4.T4 → commit `feat(notification-service): listener de notificacion`
5. F4.T5 → commit `test: flujo end-to-end con kafka`
6. F4.T6 → commit `chore: dockerfile multi-stage y doc de despliegue`

## Definición de Hecho de la fase

- `./mvnw test` en verde, incluido el test end-to-end con Kafka.
- Evento publicado y consumido validado con Testcontainers.
- Imágenes Docker construibles y ejecutables.
- Documentación de despliegue AWS actualizada.
- Calidad y seguridad sin errores.