package com.ticketflow.common.evento;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Evento de dominio compartido que viaja por Kafka entre
 * {@code reservation-service} (productor) y
 * {@code notification-service} (consumidor) al confirmarse una compra
 * (spec 0001, secciones 2.2 y 4; plan Fase 4, F4.T1).
 *
 * <p>Es el contrato serializable (JSON) emitido en el topic
 * {@link #TOPIC} cuando un asiento queda definitivamente vendido tras
 * un pago exitoso. Ambos microservicios dependen del módulo
 * {@code common} para compartirlo sin duplicar la definición: el
 * productor lo construye y serializa, y el consumidor lo deserializa
 * para simular el envío del boleto.</p>
 *
 * <p>Al ser un {@code record} inmutable, su serialización/deserialización
 * con Jackson es directa y predecible, sin getters adicionales ni
 * estado mutable.</p>
 *
 * @param reservaId        identificador de la reserva persistida como
 *                         {@code PAGADA}.
 * @param eventoId         identificador del evento al que pertenece la
 *                         función del asiento vendido.
 * @param funcionId        identificador de la función del asiento vendido.
 * @param asientoId        identificador del asiento vendido.
 * @param usuarioId        identificador del comprador que confirmó la
 *                         reserva.
 * @param monto            importe cobrado por la reserva; siempre mayor
 *                         que cero.
 * @param fechaConfirmacion instante (con zona horaria) en que se confirmó
 *                         la compra y se emitió el evento.
 */
public record ReservaConfirmadaEvent(
        Long reservaId,
        Long eventoId,
        Long funcionId,
        Long asientoId,
        Long usuarioId,
        BigDecimal monto,
        OffsetDateTime fechaConfirmacion
) {

    /**
     * Nombre del topic Kafka por el que viaja el evento de reserva
     * confirmada (spec 0001, sección 4). Se define como constante
     * compartida para que productor ({@code reservation-service}) y
     * consumidor ({@code notification-service}) referencien el mismo
     * nombre sin duplicar el literal.
     */
    public static final String TOPIC = "tickets.orders";
}