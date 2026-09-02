# AGENTS.md — ticket-flow

## 1. Proyecto
TicketFlow es una plataforma backend distribuida de reservas y venta de entradas en tiempo real para eventos de alta concurrencia. El objetivo principal es resolver el problema de compra concurrente de asientos (*double-booking problem*) garantizando consistencia estricta, baja latencia y tolerancia a fallos mediante una arquitectura desacoplada y basada en eventos.

## 2. Stack Tecnológico y Versiones
- **Lenguaje:** Java 21 (LTS)
- **Framework:** Spring Boot 3.2+
- **Persistencia Relacional:** PostgreSQL 16 (Spring Data JPA + Flyway para migraciones)
- **Caché y Concurrencia Distribuida:** Redis 7 (Spring Data Redis)
- **Mensajería Asíncrona:** Apache Kafka o RabbitMQ
- **Documentación de API:** springdoc-openapi v2 (OpenAPI 3)
- **Infraestructura Local:** Docker & Docker Compose
- **Despliegue Cloud:** AWS (ECS Fargate / App Runner)

---

## 3. Principios de Arquitectura y Patrones Técnicos

### A. Estrategia de Concurrencia en Dos Niveles
1. **Fase de Selección (Pico de Concurrencia):** 
   - Se implementa un bloqueo distribuido en Redis con TTL de 5 minutos mediante la clave `lock:evento:{eventoId}:asiento:{asientoId}`.
   - Si la clave ya existe, se rechaza la solicitud retornando código HTTP `409 Conflict`.
2. **Fase de Pago y Persistencia (Transacción ACID):**
   - Al confirmar la compra, se ejecuta una transacción en PostgreSQL utilizando bloqueo pesimista (`@Lock(LockModeType.PESSIMISTIC_WRITE)` / `SELECT ... FOR UPDATE`) sobre la fila del asiento.
   - El estado del asiento pasa de `DISPONIBLE` a `VENDIDO`.
   - Se libera la clave en Redis.

### B. Comunicación Asíncrona (Event-Driven)
- Al completar una orden con éxito, el servicio emite un evento `ReservaConfirmadaEvent` a la cola/topic de mensajería.
- Un consumidor en segundo plano procesa el evento y gestiona el envío de tickets o notificaciones.

---

## 4. Reglas de Código para el Agente
- **Java Moderno:** Usar Java Records para DTOs, inyección por constructor (`@RequiredArgsConstructor` o constructores explícitos) y `Optional` adecuadamente.
- **Manejo de Errores:** Centralizar excepciones de dominio mediante `@RestControllerAdvice` retornando respuestas consistentes bajo el estándar RFC 7807 (*Problem Details*).
- **Control de Transacciones:** Métodos de persistencia anotados estrictamente con `@Transactional` (especificar `readOnly = true` en consultas).
- **Atomicidad en Redis:** Utilizar scripts Lua o comandos atómicos (`SET key value NX PX milliseconds`) para evitar condiciones de carrera al bloquear/liberar recursos.
- **Documentación obligatoria (Javadoc exhaustivo):** todo código generado, modificado o refactorizado por el agente debe ir documentado con Javadoc sin excepciones. Requisitos estrictos:
  - Documentar **toda clase, interfaz, enum, método público y método protegido** de producción **y de tests** (incluye getters, setters, constructores, `main`, campos públicos/estáticos y métodos `@Test`).
  - Cada bloque Javadoc debe incluir una descripción clara y concisa del propósito del elemento.
  - Etiquetas obligatorias cuando apliquen: `@param` con descripción extendida para cada parámetro, `@return` con descripción extendida del valor devuelto, `@throws` con la condición que origina la excepción.
  - Si se modifica código existente que carece de documentación, agregarla antes de cerrar la tarea. No omitir documentación bajo ninguna circunstancia, ni siquiera para métodos cortos o autodescriptivos.
  - Comentarios en líneas o bloques cuando contengan lógica no evidente: decisiones de diseño, condiciones de carrera evitadas, *workarounds* justificados o referencias a la spec/sección correspondiente.
  - El Javadoc de clases debe indicar el rol de la clase en el dominio cuando aplique y referenciar la sección de la spec activa cuando el comportamiento venga fijado por ella (p. ej. "spec 0001, sección 3.1").
  - Idioma: español (alineado con constitución §8). Los identificadores y nombres técnicos siguen en inglés.

---

## 5. Estilo
- Java moderno (ver sección 4): records para DTOs, inyección por constructor, `Optional` adecuado.
- Solo bibliotecas seguras y útiles; no reinventar lo que ya existe en una biblioteca confiable. Se debe preguntar antes de importar bibliotecas.
- Código e identificadores en inglés; mensajes de usuario en español.

---

## 6. Build, Calidad y Documentación

### Build y Tests
- **Tests:** `./mvnw test`
- **Cobertura:** plugin JaCoCo. Genera el reporte de cobertura sin umbral que falle el build; la cobertura se revisa como guía, no como puerta numérica.
- **Verificación completa (build + tests + calidad/seguridad configuradas):** `./mvnw verify`

### Calidad de Código
- **Checkstyle:** plugin de Maven con ruleset propio del proyecto en `config/checkstyle/checkstyle.xml`. Se ejecuta automáticamente en `./mvnw verify` (y manualmente con `./mvnw checkstyle:check`).

### Documentación de API
- **springdoc-openapi v2** (compatible con Spring Boot 3.x): genera OpenAPI 3 en `/v3/api-docs` y Swagger UI en `/swagger-ui.html`.
- Los endpoints y DTOs se anotan con anotaciones OpenAPI (`@Operation`, `@Schema`, etc.).

### Verificación de Seguridad
- **OWASP Dependency-Check:** escanea dependencias contra la base NVD por CVEs conocidos. Se ejecuta con `./mvnw dependency-check:check`.
- **SpotBugs + Find Security Bugs:** análisis estático de patrones inseguros en el código propio (SQL injection, XSS, uso inseguro de crypto, etc.). Se ejecuta con `./mvnw spotbugs:check`.
- Ambos se enlazan a `./mvnw verify`.

---

## Skills

Usa las skills disponibles (superpowers) cuando corresponda al contexto, para elevar la calidad del trabajo:

- **brainstorming**: antes de crear una feature, componente o modificar comportamiento; refina intención, requisitos y diseño antes de implementar.
- **writing-plans**: cuando haya una spec o requisitos para una tarea de varios pasos, antes de tocar código.
- **test-driven-development**: antes de escribir código de implementación, para features o bugfixes.
- **systematic-debugging**: ante cualquier bug, fallo de test o comportamiento inesperado, antes de proponer fixes.
- **requesting-code-review** / **receiving-code-review**: al completar tareas o al recibir feedback de revisión.
- **verification-before-completion**: antes de afirmar que algo está completo o pasa las pruebas.

Consulta y carga la skill relevante antes de responder o actuar cuando aplique.

---

## Reglas
- Lee `docs/constitution.md` y la spec activa en `docs/specs/` antes de tocar código.
- No añadas dependencias ni cambies código sin actualizar antes la spec.
- No modifiques archivos dentro de `docs/specs/` salvo petición explícita.
- La constitución es la fuente única de principios innegociables; este documento detalla el cómo.

## Al terminar cualquier tarea
- Ejecuta las pruebas (`./mvnw test`) e indica que todo pasa.
- Ejecuta las métricas de calidad de código (`./mvnw checkstyle:check` dentro de `verify`; cobertura JaCoCo sin umbral).
- Ejecuta la verificación de seguridad del código (`./mvnw dependency-check:check` y `./mvnw spotbugs:check` dentro de `verify`).