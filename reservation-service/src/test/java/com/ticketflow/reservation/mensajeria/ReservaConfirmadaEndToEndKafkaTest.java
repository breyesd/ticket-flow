package com.ticketflow.reservation.mensajeria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketflow.common.evento.ReservaConfirmadaEvent;
import com.ticketflow.reservation.AbstractIntegrationTest;
import com.ticketflow.reservation.domain.evento.Asiento;
import com.ticketflow.reservation.domain.evento.AsientoRepository;
import com.ticketflow.reservation.domain.evento.EventoRepository;
import com.ticketflow.reservation.support.EmbeddedRedisExtension;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Test end-to-end que valida el flujo completo de mensajería: bloquear →
 * confirmar → evento en {@code tickets.orders} (spec 0001, sección 4;
 * plan Fase 4, F4.T5).
 *
 * <p>Punto de corte entre el productor ({@code reservation-service}) y
 * el consumidor ({@code notification-service}): ejecuta el flujo HTTP
 * real de compra contra Postgres (Testcontainers) y Redis (embebido),
 * de modo que el {@code reservation-service} publica el evento en un
 * Kafka real (Testcontainers). A continuación consume el mensaje
 * usando exactamente la misma estrategia de deserialización que el
 * {@code notification-service} (encabezado de tipo {@code __TypeId__}
 * emitido por el productor + paquetes de confianza), verificando la
 * integridad de los datos de un extremo al otro sin intervención
 * manual.</p>
 */
@Testcontainers
@ExtendWith(EmbeddedRedisExtension.class)
@AutoConfigureMockMvc
class ReservaConfirmadaEndToEndKafkaTest extends AbstractIntegrationTest {

    /**
     * Imagen de Kafka usada para el contenedor de test (broker KRaft
     * single-node).
     */
    private static final DockerImageName KAFKA_IMAGE =
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1");

    /**
     * Contenedor Kafka compartido por toda la clase de test.
     */
    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(KAFKA_IMAGE);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AsientoRepository asientoRepository;

    @Autowired
    private EventoRepository eventoRepository;

    /**
     * Publica en el contexto de Spring la URL del broker Kafka del
     * contenedor {@link #KAFKA}.
     *
     * @param registry registro dinámico de propiedades de Spring al que
     *                 se añade {@code spring.kafka.bootstrap-servers}.
     */
    @DynamicPropertySource
    static void configureKafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    /**
     * Ejecuta el flujo completo de compra y verifica que el evento
     * publicado por {@code reservation-service} es consumible mediante
     * la configuración de deserialización del
     * {@code notification-service}, con los datos íntegros.
     *
     * @throws Exception si falla el flujo HTTP o la consumición del
     *                   evento.
     */
    @Test
    void flujoCompletoReservaKafkaNotificacion() throws Exception {
        Asiento asiento = asientoRepository.findByFuncionIdAndNumero(1L, 95).orElseThrow();
        Long funcionId = asiento.getFuncion().getId();
        Long asientoId = asiento.getId();
        Long eventoId = eventoRepository.findAll().get(0).getId();

        String bloquear = mockMvc.perform(post("/api/v1/reservas/bloquear")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"funcionId": %d, "asientoId": %d}
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
                                    "usuarioId": 7,
                                    "monto": 120.00
                                }
                                """.formatted(reservationId, funcionId, asientoId)))
                .andExpect(status().isOk());

        ReservaConfirmadaEvent evento = consumirComoNotificationService();

        assertThat(evento.reservaId()).isNotNull();
        assertThat(evento.eventoId()).isEqualTo(eventoId);
        assertThat(evento.funcionId()).isEqualTo(funcionId);
        assertThat(evento.asientoId()).isEqualTo(asientoId);
        assertThat(evento.usuarioId()).isEqualTo(7L);
        assertThat(evento.monto()).isEqualByComparingTo("120.00");
        assertThat(evento.fechaConfirmacion()).isNotNull();
    }

    /**
     * Consume el evento del topic usando la configuración de
     * deserialización del {@code notification-service}: deserializador
     * JSON guiado por el encabezado de tipo {@code __TypeId__} que
     * emite el productor, restringido al paquete de confianza
     * {@code com.ticketflow.common.evento}.
     *
     * @return el {@link ReservaConfirmadaEvent} consumido del topic.
     */
    private ReservaConfirmadaEvent consumirComoNotificationService() {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                KAFKA.getBootstrapServers(), "notification-consumer", "true");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                JsonDeserializer.class.getName());
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES,
                ReservaConfirmadaEvent.class.getPackageName());

        try (Consumer<String, ReservaConfirmadaEvent> consumer =
                     new DefaultKafkaConsumerFactory<String, ReservaConfirmadaEvent>(
                             consumerProps).createConsumer()) {
            consumer.subscribe(List.of(ReservaConfirmadaEvent.TOPIC));
            ConsumerRecord<String, ReservaConfirmadaEvent> record =
                    KafkaTestUtils.getSingleRecord(consumer,
                            ReservaConfirmadaEvent.TOPIC, Duration.ofSeconds(30));
            return record.value();
        }
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