package com.ticketflow.reservation.domain.evento;

/**
 * Excepción de dominio lanzada cuando se intenta bloquear un
 * {@link Asiento} que ya está en estado {@link AsientoEstado#VENDIDO}.
 *
 * <p>El {@code @RestControllerAdvice} central la traduce a una
 * respuesta HTTP 422 Unprocessable Entity con cuerpo RFC 7807.</p>
 */
public class AsientoVendidoException extends RuntimeException {

    /**
     * Construye la excepción para el identificador de asiento
     * indicado.
     *
     * @param asientoId identificador del asiento que ya está vendido;
     *                  se incluye en el mensaje para facilitar la
     *                  trazabilidad.
     */
    public AsientoVendidoException(Long asientoId) {
        super("Asiento ya vendido: " + asientoId);
    }
}