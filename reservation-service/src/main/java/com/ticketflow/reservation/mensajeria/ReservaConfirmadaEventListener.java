package com.ticketflow.reservation.mensajeria;

import com.ticketflow.common.evento.ReservaConfirmadaEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Puente entre el evento de dominio publicado por {@code ReservaService}
 * y el transportador Kafka, invocado únicamente tras confirmarse la
 * transacción de compra (spec 0001, §3.2 y §4; plan Fase 4, F4.T3).
 *
 * <p>{@code ReservaService} publica un {@link ReservaConfirmadaEvent}
 * como evento de Spring mediante {@code ApplicationEventPublisher}
 * dentro de la transacción. Este componente lo escucha con
 * {@link TransactionalEventListener} en la fase
 * {@link TransactionPhase#AFTER_COMMIT} y lo reenvía al
 * {@link EventPublisher}, garantizando que no se emita ningún mensaje
 * Kafka de una compra cuya transacción haya fallado o esté a punto de
 * revertirse.</p>
 */
@Component
public class ReservaConfirmadaEventListener {

    private final EventPublisher eventPublisher;

    /**
     * Constructor con inyección por constructor.
     *
     * @param eventPublisher transportador de eventos de dominio (en
     *                       producción, la implementación Kafka); no
     *                       puede ser {@code null}.
     */
    public ReservaConfirmadaEventListener(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Reenvía el evento de reserva confirmada al transportador Kafka una
     * vez que la transacción de compra se haya confirmado.
     *
     * <p>Al estar en la fase {@link TransactionPhase#AFTER_COMMIT}, si
     * la transacción que originó el evento se revierte, este método no
     * se ejecuta y por tanto no se publica ningún mensaje en el topic
     * {@link ReservaConfirmadaEvent#TOPIC}.</p>
     *
     * @param evento evento de reserva confirmada cuya transacción ya se
     *               confirmó; no puede ser {@code null}.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservaConfirmada(ReservaConfirmadaEvent evento) {
        eventPublisher.publish(evento);
    }
}