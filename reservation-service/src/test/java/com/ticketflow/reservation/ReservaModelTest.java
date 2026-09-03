package com.ticketflow.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketflow.reservation.domain.evento.Asiento;
import com.ticketflow.reservation.domain.evento.AsientoRepository;
import com.ticketflow.reservation.domain.reserva.Reserva;
import com.ticketflow.reservation.domain.reserva.ReservaEstado;
import com.ticketflow.reservation.domain.reserva.ReservaRepository;
import com.ticketflow.reservation.support.EmbeddedRedisExtension;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test de integración del modelo {@link Reserva} y de la migración V3
 * (tabla {@code reserva}).
 *
 * <p>Arranca contra el perfil {@code test} (H2 en modo PostgreSQL +
 * Flyway con V1, V2 y V3 aplicadas) y verifica que:</p>
 * <ul>
 *   <li>la entidad {@link Reserva} se mapea correctamente contra la
 *       tabla {@code reserva} (Hibernate con {@code ddl-auto=validate}
 *       no reporta discrepancias),</li>
 *   <li>el enum {@link ReservaEstado} se persiste como {@code STRING},</li>
 *   <li>el repositorio {@link ReservaRepository} localiza reservas por
 *       asiento.</li>
 * </ul>
 *
 * <p>Usa {@link EmbeddedRedisExtension} para disponer de un Redis
 * embebido durante la carga del contexto.</p>
 */
@ExtendWith(EmbeddedRedisExtension.class)
@ActiveProfiles("test")
@SpringBootTest
class ReservaModelTest {

    /**
     * EntityManager inyectado por Spring a partir del
     * {@code EntityManagerFactory} configurado por JPA. Se usa para
     * persistir manualmente las entidades y forzar el flush antes de
     * las aserciones.
     */
    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private AsientoRepository asientoRepository;

    @Autowired
    private ReservaRepository reservaRepository;

    /**
     * Persiste una reserva {@link ReservaEstado#PAGADA} asociada a un
     * asiento y verifica que se mapea y se recupera correctamente por
     * asiento.
     */
    @Test
    @Transactional
    void reservaPagadaSePersisteYSeRecuperaPorAsiento() {
        Asiento asiento = asientoRepository.findAll().get(0);

        Reserva reserva = new Reserva(
                42L, asiento, new BigDecimal("99.50"), ReservaEstado.PAGADA);
        entityManager.persist(reserva);
        entityManager.flush();
        entityManager.clear();

        assertThat(reservaRepository.findByAsientoIdOrderByFechaCreacionAsc(asiento.getId()))
                .hasSize(1)
                .allSatisfy(r -> {
                    assertThat(r.getUsuarioId()).isEqualTo(42L);
                    assertThat(r.getMonto()).isEqualByComparingTo(new BigDecimal("99.50"));
                    assertThat(r.getEstado()).isEqualTo(ReservaEstado.PAGADA);
                    assertThat(r.getAsiento().getId()).isEqualTo(asiento.getId());
                });
    }
}