package com.ticketflow.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketflow.reservation.domain.evento.Asiento;
import com.ticketflow.reservation.domain.evento.AsientoEstado;
import com.ticketflow.reservation.domain.evento.Evento;
import com.ticketflow.reservation.domain.evento.Funcion;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
class EventoModelTest {

    @PersistenceContext
    private EntityManager entityManager;

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
