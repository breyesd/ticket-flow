package com.ticketflow.common.evento;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

/**
 * Test unitario del contrato de serialización JSON del
 * {@link ReservaConfirmadaEvent} (spec 0001, sección 4; plan Fase 4,
 * F4.T1).
 *
 * <p>Verifica que el evento es serializable a JSON y deserializable de
 * vuelta con Jackson tal y como lo harán el productor
 * ({@code reservation-service}) y el consumidor
 * ({@code notification-service}) a través de Kafka, y que la constante
 * {@link ReservaConfirmadaEvent#TOPIC} define el nombre esperado del
 * topic.</p>
 */
class ReservaConfirmadaEventTest {

    /**
     * Mapper de Jackson que replica la (de)serialización usada en los
     * transportadores Kafka. Registra el soporte de fechas JSR 310
     * (necesario para {@link OffsetDateTime}).
     */
    private final ObjectMapper objectMapper =
            new ObjectMapper().registerModule(new JavaTimeModule());

    /**
     * Verifica que el evento se serializa a JSON y se deserializa sin
     * pérdida de datos, y que la constante del topic es la esperada.
     *
     * @throws Exception si la serialización/deserialización falla.
     */
    @Test
    void seSerializaYDeserializaSinPerdida() throws Exception {
        assertThat(ReservaConfirmadaEvent.TOPIC).isEqualTo("tickets.orders");

        ReservaConfirmadaEvent evento = new ReservaConfirmadaEvent(
                10L, 5L, 3L, 8L, 42L, new BigDecimal("99.50"),
                OffsetDateTime.parse("2026-09-03T13:00:00+01:00"));

        String json = objectMapper.writeValueAsString(evento);

        assertThat(json).contains("\"reservaId\":10");

        ReservaConfirmadaEvent recuperado =
                objectMapper.readValue(json, ReservaConfirmadaEvent.class);

        assertThat(recuperado.reservaId()).isEqualTo(10L);
        assertThat(recuperado.eventoId()).isEqualTo(5L);
        assertThat(recuperado.funcionId()).isEqualTo(3L);
        assertThat(recuperado.asientoId()).isEqualTo(8L);
        assertThat(recuperado.usuarioId()).isEqualTo(42L);
        assertThat(recuperado.monto()).isEqualByComparingTo("99.50");
        // La comparación de fechas se hace por instante (no por offset),
        // que es lo relevante para el contrato del evento.
        assertThat(recuperado.fechaConfirmacion().toInstant())
                .isEqualTo(evento.fechaConfirmacion().toInstant());
    }
}