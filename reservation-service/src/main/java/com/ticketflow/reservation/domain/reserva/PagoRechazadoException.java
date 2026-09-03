package com.ticketflow.reservation.domain.reserva;

/**
 * Excepción de dominio lanzada al confirmar una compra cuando la
 * pasarela de pago rechaza el cobro.
 *
 * <p>El {@code @RestControllerAdvice} central la traduce a una
 * respuesta HTTP 422 Unprocessable Entity con cuerpo RFC 7807 (spec
 * 0001, sección 2.5: "pago rechazado", y sección 5). El motivo del
 * rechazo proporcionado por la pasarela se incluye en el mensaje para
 * exponerlo al cliente.</p>
 */
public class PagoRechazadoException extends RuntimeException {

    /**
     * Construye la excepción con el motivo del rechazo.
     *
     * @param motivo motivo legible del rechazo proporcionado por la
     *               pasarela de pago; no puede ser {@code null}.
     */
    public PagoRechazadoException(String motivo) {
        super("Pago rechazado: " + motivo);
    }
}