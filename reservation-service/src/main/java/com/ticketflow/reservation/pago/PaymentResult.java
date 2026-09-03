package com.ticketflow.reservation.pago;

/**
 * Resultado de procesar una {@link PaymentRequest} en el
 * {@link PaymentGateway} (spec 0001, sección 5; plan Fase 3, F3.T3).
 *
 * <p>Un resultado es <strong>exitoso</strong> ({@link #exitoso()}
 * {@code true}) o <strong>fallido</strong>. Cuando falla, {@link
 * #motivo()} expone una descripción legible pensada para mapear a la
 * respuesta HTTP 422 del endpoint de confirmación.</p>
 *
 * @param exitoso {@code true} si el cobro fue aceptado, {@code false} si
 *                fue rechazado.
 * @param motivo  motivo del rechazo cuando {@code exitoso} es
 *                {@code false}; {@code null} cuando el cobro fue exitoso.
 */
public record PaymentResult(boolean exitoso, String motivo) {

    /**
     * Construye un resultado exitoso, sin motivo.
     *
     * @return un {@link PaymentResult} con {@code exitoso == true} y
     *         {@code motivo == null}.
     */
    public static PaymentResult exito() {
        return new PaymentResult(true, null);
    }

    /**
     * Construye un resultado fallido con el motivo indicado.
     *
     * @param motivo motivo legible del rechazo; no puede ser
     *               {@code null} ni vacío.
     * @return un {@link PaymentResult} con {@code exitoso == false} y el
     *         motivo facilitado.
     */
    public static PaymentResult fallo(String motivo) {
        return new PaymentResult(false, motivo);
    }
}