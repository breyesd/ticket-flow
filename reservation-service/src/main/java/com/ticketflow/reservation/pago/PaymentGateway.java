package com.ticketflow.reservation.pago;

/**
 * Contrato de la pasarela de pago de TicketFlow (spec 0001, sección 5;
 * plan Fase 3, F3.T3).
 *
 * <p>Abstrae el cobro durante la fase de pago y persistencia: el caso de
 * uso de confirmación delega en esta interfaz el procesamiento del pago
 * de cada reserva, de modo que la implementación real pueda sustituirse
 * sin tocar el núcleo del flujo de compra. En esta iteración la única
 * implementación es {@link PaymentGatewayMock}.</p>
 */
public interface PaymentGateway {

    /**
     * Procesa el cobro descrito por {@code request}.
     *
     * @param request petición de cobro con el importe; no puede ser
     *                {@code null}.
     * @return un {@link PaymentResult} indicando éxito o fallo del cobro;
     *         nunca {@code null}.
     */
    PaymentResult charge(PaymentRequest request);
}