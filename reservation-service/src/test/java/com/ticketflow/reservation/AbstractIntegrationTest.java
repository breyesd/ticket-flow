package com.ticketflow.reservation;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Clase base para tests de integración de {@code reservation-service}
 * que requieren una base de datos PostgreSQL real levantada con
 * Testcontainers.
 *
 * <p>Declara un contenedor Postgres 16 compartido por todos los tests
 * que hereden de esta clase y publica su URL, usuario y contraseña en
 * el contexto de Spring mediante {@link DynamicPropertySource} para
 * que el datasource apunte al contenedor en lugar de a la base de
 * desarrollo. Hoy los tests activos (F1.T1 y F1.T2) usan el perfil
 * {@code test} con H2 embebido; esta base queda reservada para
 * futuras tareas (F1.T3 en adelante) que necesiten características
 * específicas de Postgres.</p>
 */
@Testcontainers
@SpringBootTest
public abstract class AbstractIntegrationTest {

    /**
     * Contenedor Postgres 16 compartido por toda la clase de test.
     * Arranca una vez antes de los tests y se detiene al finalizar,
     * reusándose entre tests para reducir el tiempo total.
     */
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
                    .withDatabaseName("ticketflow")
                    .withUsername("ticketflow")
                    .withPassword("ticketflow");

    /**
     * Publica en el contexto de Spring las propiedades de datasource
     * que apuntan al contenedor {@link #POSTGRES}, sobrescribiendo los
     * valores de {@code application.yml}.
     *
     * @param registry registro dinámico de propiedades de Spring al que
     *                 se añaden {@code spring.datasource.url},
     *                 {@code spring.datasource.username} y
     *                 {@code spring.datasource.password}.
     */
    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}