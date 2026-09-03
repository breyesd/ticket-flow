package com.ticketflow.reservation.mensajeria;

import com.ticketflow.common.evento.ReservaConfirmadaEvent;

/**
 * Puerto de publicación de eventos de dominio del
 * {@code reservation-service} (spec 0001, sección 4; plan Fase 4,
 * F4.T3).
 *
 * <p>Define el contrato por el que el caso de uso de confirmación de
 * compra emite el evento {@link ReservaConfirmadaEvent} sin acoplarse a
 * una infraestructura concreta de mensajería. La implementación real
 * publica en Kafka (topic {@link ReservaConfirmadaEvent#TOPIC}), pero
 * el core depende únicamente de esta interfaz, lo que permite testear
 * con un publicador falso y sustituir el transporte en el futuro sin
 * tocar la lógica de negocio.</p>
 */
public interface EventPublisher {

    /**
     * Publica un evento de reserva confirmada en el canal de mensajería
     * configurado.
     *
     * <p>Debe invocarse únicamente tras una confirmación de compra
     * exitosa (reserva en estado {@code PAGADA}), para no emitir
     * eventos de compras que luego fallen (spec 0001, §3.2 y §4).</p>
     *
     * @param evento evento de dominio con los datos de la reserva
     *               confirmada; no puede ser {@code null}.
     */
    void publish(ReservaConfirmadaEvent evento);
}