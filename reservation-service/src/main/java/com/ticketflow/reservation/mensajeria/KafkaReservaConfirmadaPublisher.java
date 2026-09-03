package com.ticketflow.reservation.mensajeria;

import com.ticketflow.common.evento.ReservaConfirmadaEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Implementación Kafka del {@link EventPublisher} que publica el evento
 * {@link ReservaConfirmadaEvent} en el topic
 * {@link ReservaConfirmadaEvent#TOPIC} (spec 0001, sección 4; plan
 * Fase 4, F4.T3).
 *
 * <p>Delega el envío en un {@link KafkaTemplate} configurado por
 * Spring, serializando el valor como JSON (ver la sección
 * {@code spring.kafka.producer} de {@code application.yml}). La clave
 * del mensaje es el identificador de la reserva en formato texto, lo
 * que garantiza un particionado estable por reserva y un orden total
 * por clave dentro de una partición.</p>
 */
@Component
public class KafkaReservaConfirmadaPublisher implements EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Constructor con inyección por constructor.
     *
     * @param kafkaTemplate plantilla de Spring Kafka usada para
     *                      publicar los mensajes serializados como
     *                      JSON; no puede ser {@code null}.
     */
    public KafkaReservaConfirmadaPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publica el evento en el topic {@link ReservaConfirmadaEvent#TOPIC}
     * usando el identificador de la reserva como clave del mensaje.
     *
     * @param evento evento de dominio con los datos de la reserva
     *               confirmada; no puede ser {@code null}.
     */
    @Override
    public void publish(ReservaConfirmadaEvent evento) {
        kafkaTemplate.send(ReservaConfirmadaEvent.TOPIC,
                evento.reservaId().toString(), evento);
    }
}