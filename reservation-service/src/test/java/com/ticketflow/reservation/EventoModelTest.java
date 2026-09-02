package com.ticketflow.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketflow.reservation.domain.evento.Asiento;
import com.ticketflow.reservation.domain.evento.AsientoEstado;
import com.ticketflow.reservation.domain.evento.Evento;
import com.ticketflow.reservation.domain.evento.Funcion;
import com.ticketflow.reservation.support.EmbeddedRedisExtension;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test de integración de la jerarquía JPA
 * {@code Evento (1) ─< Funcion (n) ─< Asiento (n)} y del default
 * {@link AsientoEstado#DISPONIBLE} al crear un {@link Asiento}.
 *
 * <p>Arranca contra el perfil {@code test} (H2 en modo PostgreSQL +
 * Flyway con la migración V1 aplicada) y comprueba que Hibernate,
 * configurado con {@code ddl-auto=validate}, reconoce el esquema sin
 * discrepancias.</p>
 *
 * <p>Usa {@link EmbeddedRedisExtension} para disponer de un Redis
 * embebido durante la carga del contexto (necesario porque el perfil
 * {@code test} ya no excluye {@code RedisAutoConfiguration}).</p>
 */
@ExtendWith(EmbeddedRedisExtension.class)
@ActiveProfiles("test")
@SpringBootTest
class EventoModelTest {

    /**
     * EntityManager inyectado por Spring a partir del
     * {@code EntityManagerFactory} configurado por JPA. Se usa para
     * persistir manualmente las entidades y forzar el flush antes de
     * las aserciones.
     */
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Persiste una jerarquía completa (evento → función → asiento) en
     * una sola transacción y verifica:
     * <ul>
     *   <li>que el asiento recibe un id surrogate al persistir,</li>
     *   <li>que su estado inicial es {@link AsientoEstado#DISPONIBLE},</li>
     *   <li>que las relaciones ManyToOne asiento→función y
     *       función→evento quedan correctamente materializadas.</li>
     * </ul>
     */
    @Test
    @Transactional
    void eventoFuncionAsientoHierarchyIsPersisted() {
        Evento evento = new Evento("Concierto Demo", "Demo");
        entityManager.persist(evento);

        Funcion funcion = new Funcion(evento, java.time.OffsetDateTime.now().plusDays(7));
        entityManager.persist(funcion);

        Asiento asiento = new Asiento(funcion, 1);
        entityManager.persist(asiento);
        entityManager.flush();

        assertThat(asiento.getId()).isNotNull();
        assertThat(asiento.getEstado()).isEqualTo(AsientoEstado.DISPONIBLE);
        assertThat(asiento.getFuncion().getId()).isEqualTo(funcion.getId());
        assertThat(funcion.getEvento().getId()).isEqualTo(evento.getId());
    }
}
