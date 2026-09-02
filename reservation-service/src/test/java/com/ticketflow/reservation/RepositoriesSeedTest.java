package com.ticketflow.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketflow.reservation.domain.evento.Asiento;
import com.ticketflow.reservation.domain.evento.AsientoEstado;
import com.ticketflow.reservation.domain.evento.AsientoRepository;
import com.ticketflow.reservation.domain.evento.Evento;
import com.ticketflow.reservation.domain.evento.EventoRepository;
import com.ticketflow.reservation.domain.evento.Funcion;
import com.ticketflow.reservation.domain.evento.FuncionRepository;
import com.ticketflow.reservation.support.EmbeddedRedisExtension;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test de integración de la capa de acceso a datos del modelo de
 * dominio y de la migración de seed V2.
 *
 * <p>Arranca contra el perfil {@code test} (H2 en modo PostgreSQL +
 * Flyway con V1 y V2 aplicadas) y verifica que, tras las
 * migraciones, existen 1 evento, 1 función y 100 asientos en estado
 * {@link AsientoEstado#DISPONIBLE}, y que los métodos de consulta de
 * los repositorios devuelven los datos esperados.</p>
 *
 * <p><strong>Nota:</strong> la spec original marca este test como
 * candidato a correr contra Testcontainers con Postgres real. En este
 * entorno sin Docker se usa H2 en modo PostgreSQL, que es suficiente
 * para validar el contrato de los repositorios y la corrección de las
 * migraciones; las verificaciones específicas de Postgres (p. ej.
 * {@code SELECT ... FOR UPDATE}) se abordarán en fases posteriores
 * con la base real.</p>
 *
 * <p>Usa {@link EmbeddedRedisExtension} para disponer de un Redis
 * embebido durante la carga del contexto (necesario porque el perfil
 * {@code test} ya no excluye {@code RedisAutoConfiguration}).</p>
 */
@ExtendWith(EmbeddedRedisExtension.class)
@ActiveProfiles("test")
@SpringBootTest
class RepositoriesSeedTest {

    @Autowired
    private EventoRepository eventoRepository;

    @Autowired
    private FuncionRepository funcionRepository;

    @Autowired
    private AsientoRepository asientoRepository;

    /**
     * Verifica que la migración V2 de seed carga exactamente 1 evento,
     * 1 función y 100 asientos en estado {@link AsientoEstado#DISPONIBLE},
     * distribuidos con números del 1 al 100 dentro de la función.
     */
    @Test
    void seedMigrationsLoadExpectedData() {
        List<Evento> eventos = eventoRepository.findAll();
        assertThat(eventos).hasSize(1);

        Evento evento = eventos.get(0);
        List<Funcion> funciones = funcionRepository.findByEventoId(evento.getId());
        assertThat(funciones).hasSize(1);

        Funcion funcion = funciones.get(0);
        List<Asiento> asientos =
                asientoRepository.findByFuncionIdOrderByNumeroAsc(funcion.getId());
        assertThat(asientos).hasSize(100);
        assertThat(asientos)
                .extracting(Asiento::getNumero)
                .containsExactlyElementsOf(
                        java.util.stream.IntStream.rangeClosed(1, 100)
                                .boxed()
                                .toList());
        assertThat(asientos)
                .allMatch(a -> a.getEstado() == AsientoEstado.DISPONIBLE,
                        "todos los asientos del seed deben empezar DISPONIBLE");
    }

    /**
     * Verifica que {@code findByFuncionIdAndNumero} localiza un asiento
     * concreto por la combinación de función y número. Esta consulta
     * es la que usará el lock pesimista en fases posteriores.
     */
    @Test
    void findByFuncionIdAndNumeroReturnsExpectedAsiento() {
        Long funcionId = funcionRepository.findAll().get(0).getId();

        Optional<Asiento> asiento = asientoRepository.findByFuncionIdAndNumero(funcionId, 1);

        assertThat(asiento).isPresent();
        assertThat(asiento.get().getNumero()).isEqualTo(1);
        assertThat(asiento.get().getEstado()).isEqualTo(AsientoEstado.DISPONIBLE);
    }

    /**
     * Verifica que {@code findByFuncionIdAndNumero} devuelve Optional
     * vacío cuando el número no existe en la función.
     */
    @Test
    void findByFuncionIdAndNumeroReturnsEmptyWhenNotFound() {
        Long funcionId = funcionRepository.findAll().get(0).getId();

        Optional<Asiento> asiento = asientoRepository.findByFuncionIdAndNumero(funcionId, 999);

        assertThat(asiento).isEmpty();
    }
}
