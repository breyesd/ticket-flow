package com.ticketflow.notification.mensajeria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.ticketflow.common.evento.ReservaConfirmadaEvent;
import com.ticketflow.notification.notificacion.NotificacionService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Test de integración que verifica que el listener de
 * {@code notification-service} consume y procesa un
 * {@link ReservaConfirmadaEvent} publicado en el topic
 * {@code tickets.orders} (spec 0001, sección 4; plan Fase 4, F4.T4).
 *
 * <p>Arranca un Kafka real con Testcontainers, publica un evento de
 * ejemplo en el topic mediante un productor de test y verifica, con un
 * spy del {@link NotificacionService}, que el listener deserializa el
 * evento y delega el "envío" simulado con los datos correctos.</p>
 */
@Testcontainers
@SpringBootTest
class ReservaConfirmadaListenerIntegrationTest {

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

    /**
     * Spy del servicio de notificación usado para verificar que el
     * listener invoca {@code simularEnvio} con el evento recibido.
     */
    @SpyBean
    private NotificacionService notificacionService;

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
     * Verifica que al publicar un evento en el topic el listener lo
     * consume y delega el envío simulado con los datos correctos.
     *
     * @throws Exception si falla la publicación, la subscripción o la
     *                   verificación con timeout.
     */
    @Test
    void listenerConsumeYProcesaEvento() throws Exception {
        KafkaTemplate<String, Object> template = construirProductor();
        ReservaConfirmadaEvent evento = new ReservaConfirmadaEvent(
                1L, 1L, 1L, 1L, 42L, new BigDecimal("99.50"),
                OffsetDateTime.now());

        template.send(ReservaConfirmadaEvent.TOPIC, "1", evento).get(30, TimeUnit.SECONDS);

        ArgumentCaptor<ReservaConfirmadaEvent> captor = forClass(ReservaConfirmadaEvent.class);
        verify(notificacionService, timeout(30_000).times(1))
                .simularEnvio(captor.capture());

        ReservaConfirmadaEvent recibido = captor.getValue();
        assertThat(recibido.reservaId()).isEqualTo(1L);
        assertThat(recibido.asientoId()).isEqualTo(1L);
        assertThat(recibido.usuarioId()).isEqualTo(42L);
        assertThat(recibido.monto()).isEqualByComparingTo("99.50");
    }

    /**
     * Construye un {@link KafkaTemplate} apuntando al contenedor
     * {@link #KAFKA} para publicar el evento de prueba.
     *
     * @return plantilla productora con serialización JSON del valor.
     */
    private KafkaTemplate<String, Object> construirProductor() {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(
                KAFKA.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                JsonSerializer.class.getName());
        return new KafkaTemplate<>(
                new DefaultKafkaProducerFactory<>(producerProps));
    }
}