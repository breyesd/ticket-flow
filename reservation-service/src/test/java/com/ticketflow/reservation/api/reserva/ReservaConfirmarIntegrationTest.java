package com.ticketflow.reservation.api.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketflow.reservation.AbstractIntegrationTest;
import com.ticketflow.reservation.domain.evento.Asiento;
import com.ticketflow.reservation.domain.evento.AsientoEstado;
import com.ticketflow.reservation.domain.evento.AsientoRepository;
import com.ticketflow.reservation.domain.reserva.Reserva;
import com.ticketflow.reservation.domain.reserva.ReservaEstado;
import com.ticketflow.reservation.domain.reserva.ReservaRepository;
import com.ticketflow.reservation.lock.SeatLockService;
import com.ticketflow.reservation.support.EmbeddedRedisExtension;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Test de integración del endpoint
 * {@code POST /api/v1/reservas/confirmar} (plan Fase 3, F3.T4).
 *
 * <p>Arranca contra un PostgreSQL 16 real (vía
 * {@link AbstractIntegrationTest} con Testcontainers) y un Redis
 * embebido (vía {@link EmbeddedRedisExtension}) para completar el flujo
 * completo de compra: bloquear un asiento y luego confirmarlo, y
 * verificar que el estado final en base de datos queda consistente
 * (asiento {@code VENDIDO}, reserva {@code PAGADA}, lock liberado).</p>
 */
@ExtendWith(EmbeddedRedisExtension.class)
@AutoConfigureMockMvc
class ReservaConfirmarIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AsientoRepository asientoRepository;

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private SeatLockService seatLockService;

    /**
     * Verifica que confirmar un asiento libre completa la compra: el
     * asiento queda {@code VENDIDO}, se crea una reserva {@code PAGADA}
     * y el lock Redis se libera.
     */
    @Test
    void confirmarAsientoLibreCompletaCompra() throws Exception {
        // Se usa el asiento número 100 para no interferir con otros
        // tests que leen el asiento número 1 de la misma función.
        Asiento asiento = asientoRepository.findByFuncionIdAndNumero(1L, 100).orElseThrow();
        Long funcionId = asiento.getFuncion().getId();
        Long asientoId = asiento.getId();

        // 1. Bloquear el asiento para obtener el reservationId.
        String bloquear = mockMvc.perform(post("/api/v1/reservas/bloquear")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "funcionId": %d,
                                    "asientoId": %d
                                }
                                """.formatted(funcionId, asientoId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String reservationId = extractReservationId(bloquear);

        // 2. Confirmar la compra con el reservationId.
        String confirmarBody = """
                {
                    "reservationId": "%s",
                    "funcionId": %d,
                    "asientoId": %d,
                    "usuarioId": 42,
                    "monto": 99.50
                }
                """.formatted(reservationId, funcionId, asientoId);

        mockMvc.perform(post("/api/v1/reservas/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmarBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservaId").value(notNullValue()))
                .andExpect(jsonPath("$.estado").value("PAGADA"))
                .andExpect(jsonPath("$.asientoId").value(asientoId));

        // 3. Verificar el estado final en base de datos.
        Asiento vendido = asientoRepository.findById(asientoId).orElseThrow();
        assertThat(vendido.getEstado()).isEqualTo(AsientoEstado.VENDIDO);

        Reserva reserva = reservaRepository.findByAsientoIdOrderByFechaCreacionAsc(asientoId)
                .stream()
                .findFirst()
                .orElseThrow();
        assertThat(reserva.getEstado()).isEqualTo(ReservaEstado.PAGADA);
        assertThat(reserva.getMonto()).isEqualByComparingTo(new BigDecimal("99.50"));
        assertThat(reserva.getUsuarioId()).isEqualTo(42L);

        // 4. Verificar que el lock Redis fue liberado.
        assertThat(seatLockService.isLocked(funcionId, asientoId)).isFalse();
    }

    /**
     * Verifica que confirmar un asiento ya {@code VENDIDO} devuelve 409
     * sin efectos colaterales: no se crea ninguna reserva adicional y
     * el asiento sigue {@code VENDIDO}.
     */
    @Test
    void confirmarAsientoYaVendido_devuelve409SinEfectos() throws Exception {
        // Se usa el asiento número 98.
        Asiento asiento = asientoRepository.findByFuncionIdAndNumero(1L, 98).orElseThrow();
        Long funcionId = asiento.getFuncion().getId();
        Long asientoId = asiento.getId();

        // 1. Vender el asiento mediante el flujo normal.
        String bloquear = mockMvc.perform(post("/api/v1/reservas/bloquear")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "funcionId": %d,
                                    "asientoId": %d
                                }
                                """.formatted(funcionId, asientoId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String reservationId = extractReservationId(bloquear);
        mockMvc.perform(post("/api/v1/reservas/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "reservationId": "%s",
                                    "funcionId": %d,
                                    "asientoId": %d,
                                    "usuarioId": 42,
                                    "monto": 99.50
                                }
                                """.formatted(reservationId, funcionId, asientoId)))
                .andExpect(status().isOk());

        int reservasAntes = reservaRepository
                .findByAsientoIdOrderByFechaCreacionAsc(asientoId).size();

        // 2. Adquirir un nuevo lock sobre el asiento ya vendido (otro
        //    cliente con un lock huérfano/stale) y reintentar confirmar.
        SeatLockService.AcquireResult adquirido =
                seatLockService.acquireLock(funcionId, asientoId, 300_000L);
        String tokenStale = ((SeatLockService.AcquireResult.Acquired) adquirido).token();

        mockMvc.perform(post("/api/v1/reservas/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "reservationId": "%s",
                                    "funcionId": %d,
                                    "asientoId": %d,
                                    "usuarioId": 43,
                                    "monto": 99.50
                                }
                                """.formatted(tokenStale, funcionId, asientoId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // 3. Sin efectos: mismo número de reservas y asiento VENDIDO.
        int reservasDespues = reservaRepository
                .findByAsientoIdOrderByFechaCreacionAsc(asientoId).size();
        assertThat(reservasDespues).isEqualTo(reservasAntes);
        assertThat(asientoRepository.findById(asientoId).orElseThrow().getEstado())
                .isEqualTo(AsientoEstado.VENDIDO);
    }

    /**
     * Verifica que reintentar confirmar con el mismo {@code reservationId}
     * tras un éxito devuelve 409 (lock ya liberado) y no duplica la
     * compra.
     */
    @Test
    void reconfirmarMismoReservationId_devuelve409() throws Exception {
        // Se usa el asiento número 97.
        Asiento asiento = asientoRepository.findByFuncionIdAndNumero(1L, 97).orElseThrow();
        Long funcionId = asiento.getFuncion().getId();
        Long asientoId = asiento.getId();

        String bloquear = mockMvc.perform(post("/api/v1/reservas/bloquear")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "funcionId": %d,
                                    "asientoId": %d
                                }
                                """.formatted(funcionId, asientoId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String reservationId = extractReservationId(bloquear);

        String confirmarBody = """
                {
                    "reservationId": "%s",
                    "funcionId": %d,
                    "asientoId": %d,
                    "usuarioId": 42,
                    "monto": 99.50
                }
                """.formatted(reservationId, funcionId, asientoId);

        // Primera confirmación: éxito.
        mockMvc.perform(post("/api/v1/reservas/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmarBody))
                .andExpect(status().isOk());

        // Segunda confirmación con el mismo token: el lock ya se liberó,
        // por lo que no pertenece al propietario → 409.
        mockMvc.perform(post("/api/v1/reservas/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmarBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // Solo debe existir una reserva PAGADA y el asiento VENDIDO.
        assertThat(reservaRepository.findByAsientoIdOrderByFechaCreacionAsc(asientoId))
                .hasSize(1)
                .allSatisfy(r -> assertThat(r.getEstado()).isEqualTo(ReservaEstado.PAGADA));
    }

    /**
     * Extrae el valor del campo {@code reservationId} del JSON de
     * respuesta del endpoint {@code bloquear}.
     *
     * @param jsonRespuesta respuesta JSON como {@link String}.
     * @return valor del campo {@code reservationId}.
     */
    private String extractReservationId(String jsonRespuesta) {
        int start = jsonRespuesta.indexOf("\"reservationId\":\"")
                + "\"reservationId\":\"".length();
        int end = jsonRespuesta.indexOf('"', start);
        return jsonRespuesta.substring(start, end);
    }
}