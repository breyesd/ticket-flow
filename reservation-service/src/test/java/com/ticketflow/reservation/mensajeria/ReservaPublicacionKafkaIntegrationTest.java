package com.ticketflow.reservation.mensajeria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
 * Test de integración que verifica la publicación del evento
 * {@link ReservaConfirmadaEvent} en el topic Kafka {@code tickets.orders}
 * al confirmarse una compra (spec 0001, sección 4; plan Fase 4, F4.T3).
 *
 * <p>Arranca un Kafka real con Testcontainers ({@link KafkaContainer}),
 * además del PostgreSQL 16 de {@link AbstractIntegrationTest} y el Redis
 * embebido de {@link EmbeddedRedisExtension}, de modo que el flujo
 * completo de confirmación publica realmente en el broker y el test
 * consume el mensaje para validar que llegó con los datos correctos.</p>
 */
@Testcontainers
@ExtendWith(EmbeddedRedisExtension.class)
@AutoConfigureMockMvc
class ReservaPublicacionKafkaIntegrationTest extends AbstractIntegrationTest {

    /**
     * Imagen de Kafka usada para el contenedor de test. Se fija un
     * módulo oficial de Testcontainers que arranca un broker KRaft
     * single-node sin Zookeeper.
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
     * contenedor {@link #KAFKA}, sobrescribiendo el valor por defecto
     * de {@code application.yml}.
     *
     * @param registry registro dinámico de propiedades de Spring al que
     *                 se añade {@code spring.kafka.bootstrap-servers}.
     */
    @DynamicPropertySource
    static void configureKafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    /**
     * Verifica que, al confirmar una compra, se publica un
     * {@link ReservaConfirmadaEvent} en el topic {@code tickets.orders}
     * con los datos correctos de la reserva.
     *
     * @throws Exception si falla la ejecución del flujo HTTP o la
     *                   deserialización del evento consumido.
     */
    @Test
    void confirmarCompra_publicaEventoEnTopic() throws Exception {
        // Se usa el asiento número 96 para no interferir con otros tests.
        Asiento asiento = asientoRepository.findByFuncionIdAndNumero(1L, 96).orElseThrow();
        Long funcionId = asiento.getFuncion().getId();
        Long asientoId = asiento.getId();
        Long eventoId = eventoRepository.findAll().get(0).getId();

        // 1. Bloquear el asiento para obtener el reservationId.
        String bloquear = mockMvc.perform(post("/api/v1/reservas/bloquear")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"funcionId": %d, "asientoId": %d}
                                """.formatted(funcionId, asientoId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String reservationId = extractReservationId(bloquear);

        // 2. Confirmar la compra.
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PAGADA"));

        // 3. Consumir el evento del topic y verificar sus datos.
        ReservaConfirmadaEvent evento = consumeUnicoEvento();

        assertThat(evento.asientoId()).isEqualTo(asientoId);
        assertThat(evento.funcionId()).isEqualTo(funcionId);
        assertThat(evento.eventoId()).isEqualTo(eventoId);
        assertThat(evento.usuarioId()).isEqualTo(42L);
        assertThat(evento.monto()).isEqualByComparingTo("99.50");
        assertThat(evento.reservaId()).isNotNull();
        assertThat(evento.fechaConfirmacion()).isNotNull();
    }

    /**
     * Consume del topic {@code tickets.orders} y espera a recibir un
     * único evento, devolviéndolo ya deserializado.
     *
     * <p>El evento se publica de forma asíncrona tras el commit de la
     * transacción de confirmación, por lo que se usa
     * {@link KafkaTestUtils#getSingleRecord} con un tiempo de espera
     * flexible hasta recibirlo.</p>
     *
     * @return el {@link ReservaConfirmadaEvent} recibido en el topic.
     */
    private ReservaConfirmadaEvent consumeUnicoEvento() {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                KAFKA.getBootstrapServers(), "test-group", "true");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                JsonDeserializer.class.getName());
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES,
                ReservaConfirmadaEvent.class.getPackageName());
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE,
                ReservaConfirmadaEvent.class.getName());

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