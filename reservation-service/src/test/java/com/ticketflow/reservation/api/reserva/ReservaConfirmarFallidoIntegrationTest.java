package com.ticketflow.reservation.api.reserva;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Test de integración del flujo de fallo de pago en
 * {@code POST /api/v1/reservas/confirmar} (plan Fase 3, F3.T5).
 *
 * <p>Activa el switch {@code app.payment.mock.fail=true} mediante
 * {@link TestPropertySource}, de modo que el
 * {@link com.ticketflow.reservation.pago.PaymentGatewayMock} rechaza
 * siempre el cobro. Verifica que, al confirmar con pago rechazado:</p>
 * <ul>
 *   <li>la respuesta es HTTP 422,</li>
 *   <li>el asiento permanece {@code DISPONIBLE} (no se vende),</li>
 *   <li>se crea una {@link Reserva} {@code FALLIDA},</li>
 *   <li>el lock Redis se libera (el asiento queda disponible para un
 *       nuevo intento).</li>
 * </ul>
 *
 * <p>Arranca contra un PostgreSQL 16 real (Testcontainers) y Redis
 * embebido.</p>
 */
@ExtendWith(EmbeddedRedisExtension.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.payment.mock.fail=true")
class ReservaConfirmarFallidoIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AsientoRepository asientoRepository;

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private SeatLockService seatLockService;

    /**
     * Verifica que, con el switch de fallo activo, confirmar una compra
     * devuelve 422, deja el asiento {@code DISPONIBLE}, registra una
     * reserva {@code FALLIDA} y libera el lock.
     */
    @Test
    void confirmarConPagoRechazado_devuelve422ConReservaFallida() throws Exception {
        // Se usa el asiento número 99 (distinto de los usados por otros
        // tests para no interferir en el contenedor compartido).
        Asiento asiento = asientoRepository.findByFuncionIdAndNumero(1L, 99).orElseThrow();
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

        // 2. Confirmar con pago rechazado → 422 con Problem Details.
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
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));

        // 3. El asiento permanece DISPONIBLE.
        Asiento trasConfirmar = asientoRepository.findById(asientoId).orElseThrow();
        assertThat(trasConfirmar.getEstado()).isEqualTo(AsientoEstado.DISPONIBLE);

        // 4. Se creó una reserva FALLIDA.
        Reserva reserva = reservaRepository.findByAsientoIdOrderByFechaCreacionAsc(asientoId)
                .stream()
                .findFirst()
                .orElseThrow();
        assertThat(reserva.getEstado()).isEqualTo(ReservaEstado.FALLIDA);

        // 5. El lock se liberó y el asiento queda disponible.
        assertThat(seatLockService.isLocked(funcionId, asientoId)).isFalse();
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