# Etapa de compilación: JDK 21 (Eclipse Temurin) + Maven wrapper.
# Compila el módulo notification-service y sus dependencias del reactor
# (common) para generar el fat jar ejecutable.
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace

# Copia el wrapper y la configuración de Maven para cachear dependencias.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY common/pom.xml common/
COPY reservation-service/pom.xml reservation-service/
COPY notification-service/pom.xml notification-service/

# Descarga dependencias (se invalida solo si cambian los POM).
RUN ./mvnw -q -pl notification-service -am dependency:go-offline

# Copia el código fuente restante y empaqueta sin ejecutar tests.
COPY common/src common/src/
COPY notification-service/src notification-service/src/
RUN ./mvnw -q -pl notification-service -am package -DskipTests

# Etapa de ejecución: JRE 21 minimalista (Temurin) con usuario no-root.
# El notification-service no expone ningún puerto HTTP: solo consume de
# Kafka, por lo que no se declara EXPOSE.
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Crea un usuario sin privilegios para ejecutar la aplicación.
RUN groupadd --system app && useradd --system --gid app --create-home app

# Copia el fat jar generado en la etapa de compilación.
COPY --from=builder /workspace/notification-service/target/notification-service-*.jar app.jar

# Cambia al usuario no-root antes de arrancar.
USER app

ENTRYPOINT ["java", "-jar", "app.jar"]