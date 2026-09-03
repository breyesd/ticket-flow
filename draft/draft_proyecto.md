# Contexto del Proyecto: TicketFlow Engine

## 1. Visión y Objetivo
TicketFlow es una plataforma backend distribuida de reservas y venta de entradas en tiempo real para eventos de alta concurrencia. El objetivo principal es resolver el problema de compra concurrente de asientos (*double-booking problem*) garantizando consistencia estricta, baja latencia y tolerancia a fallos mediante una arquitectura desacoplada y basada en eventos.

---

## 2. Plan de Trabajo e Hitos de Ejecución

### Fase 1: Infraestructura Base y Modelado
- [ ] **Tarea 1.1:** Inicializar proyecto Gradle/Maven con dependencias (Web, JPA, Redis, Postgres, Lombok, Flyway).
- [ ] **Tarea 1.2:** Crear `docker-compose.yml` para levantar instancias locales de PostgreSQL 16 y Redis 7.
- [ ] **Tarea 1.3:** Crear script de migración SQL y entidades JPA: `Evento`, `Funcion` y `Asiento` (`id`, `numero`, `estado`).
- [ ] **Tarea 1.4:** Crear repositorios Spring Data JPA y cargar datos semilla (`data.sql`) con 1 evento y 100 asientos.
- [ ] **Tarea 1.5:** Implementar endpoint `GET /api/v1/eventos/{id}/asientos` para consultar disponibilidad.

### Fase 2: Bloqueo Distribuido con Redis
- [ ] **Tarea 2.1:** Configurar `RedisTemplate` con serializadores Jackson JSON y manejo de conexiones.
- [ ] **Tarea 2.2:** Crear `SeatLockService` con métodos atómicos `acquireLock(eventoId, asientoId, ttlMs)` y `releaseLock(eventoId, asientoId)`.
- [ ] **Tarea 2.3:** Crear endpoint `POST /api/v1/reservas/bloquear` con validación de disponibilidad y asignación de lock de 5 minutos.
- [ ] **Tarea 2.4:** Crear endpoint `POST /api/v1/reservas/liberar` para cancelaciones manuales.
- [ ] **Tarea 2.5:** Escribir prueba de integración de concurrencia simulando 20 hilos simultáneos sobre el mismo asiento.

### Fase 3: Persistencia ACID y Confirmación de Compra
- [ ] **Tarea 3.1:** Crear entidad y repositorio `Reserva` (`id`, `usuarioId`, `asientoId`, `monto`, `estado`, `fechaCreacion`).
- [ ] **Tarea 3.2:** Implementar método con `@Lock(LockModeType.PESSIMISTIC_WRITE)` en el repositorio de asientos.
- [ ] **Tarea 3.3:** Crear componente `PaymentGatewayMock` que simule procesamiento de pago (éxito/fallo).
- [ ] **Tarea 3.4:** Crear endpoint `POST /api/v1/reservas/confirmar` que ejecute la transacción completa: cobrar -> persistir orden -> actualizar estado de asiento a `VENDIDO` -> liberar lock en Redis.
- [ ] **Tarea 3.5:** Implementar rollback y manejo de fallos si el pago es rechazado.

### Fase 4: Mensajería Asíncrona y Despliegue
- [ ] **Tarea 4.1:** Agregar RabbitMQ/Kafka a `docker-compose.yml` y configurar consumidor/productor en Spring Boot.
- [ ] **Tarea 4.2:** Publicar `ReservaConfirmadaEvent` al finalizar la compra en el topic `tickets.orders`.
- [ ] **Tarea 4.3:** Crear listener que consuma el evento y registre/simule el envío del boleto por correo.
- [ ] **Tarea 4.4:** Crear `Dockerfile` multi-stage optimizado (Eclipse Temurin / Distroless).
- [ ] **Tarea 4.5:** Configurar manifiestos o scripts para despliegue en servicio Cloud (AWS App Runner / ECS).