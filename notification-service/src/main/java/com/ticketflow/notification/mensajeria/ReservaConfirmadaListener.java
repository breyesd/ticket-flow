package com.ticketflow.notification.mensajeria;

import com.ticketflow.common.evento.ReservaConfirmadaEvent;
import com.ticketflow.notification.notificacion.NotificacionService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listener Kafka que consume los eventos de reserva confirmada del topic
 * {@code tickets.orders} y los delega al {@link NotificacionService}
 * (spec 0001, sección 4; plan Fase 4, F4.T4).
 *
 * <p>Deserializa cada mensaje de valor como
 * {@link ReservaConfirmadaEvent} (configurado en la sección
 * {@code spring.kafka.consumer} de {@code application.yml} mediante un
 * {@code JsonDeserializer} con el tipo por defecto declarado) y lo
 * reenvía al servicio de notificación, que hoy simula el envío del
 * boleto. El diseño desacopla el transporte del caso de uso, de modo
 * que en el futuro puedan añadirse otros consumidores (email, SMS,
 * push) sin tocar el núcleo.</p>
 */
@Component
public class ReservaConfirmadaListener {

    private final NotificacionService notificacionService;

    /**
     * Constructor con inyección por constructor.
     *
     * @param notificacionService servicio que procesa el evento (hoy,
     *                            simula el envío del boleto); no puede
     *                            ser {@code null}.
     */
    public ReservaConfirmadaListener(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    /**
     * Procesa un evento {@link ReservaConfirmadaEvent} recibido en el
     * topic {@code tickets.orders}, delegando en
     * {@link NotificacionService#simularEnvio}.
     *
     * @param evento evento deserializado desde Kafka; no puede ser
     *               {@code null}.
     */
    @KafkaListener(topics = ReservaConfirmadaEvent.TOPIC)
    public void onReservaConfirmada(ReservaConfirmadaEvent evento) {
        notificacionService.simularEnvio(evento);
    }
}