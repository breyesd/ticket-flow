package com.ticketflow.reservation.pago;

import java.math.BigDecimal;

/**
 * Petición de cobro enviada al {@link PaymentGateway} durante la
 * confirmación de una compra (spec 0001, sección 5; plan Fase 3, F3.T3).
 *
 * <p>Agrupa los datos mínimos que necesita el pasarela de pago para
 * procesar una transacción en la fase de pago y persistencia. El mock
 * usa únicamente el importe, pero el contrato se deja abierto para una
 * implementación real (método de pago, moneda, etc.).</p>
 *
 * @param monto importe a cobrar; debe ser mayor que cero.
 */
public record PaymentRequest(BigDecimal monto) {
}