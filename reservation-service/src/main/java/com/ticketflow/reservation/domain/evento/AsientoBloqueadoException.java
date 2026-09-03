package com.ticketflow.reservation.domain.evento;

/**
 * Excepción de dominio lanzada cuando se intenta bloquear un
 * {@link Asiento} que ya tiene un lock activo en Redis.
 *
 * <p>El {@code @RestControllerAdvice} central la traduce a una
 * respuesta HTTP 409 Conflict con cuerpo RFC 7807.</p>
 */
public class AsientoBloqueadoException extends RuntimeException {

    /**
     * Construye la excepción para el identificador de asiento
     * indicado.
     *
     * @param asientoId identificador del asiento que ya está
     *                  bloqueado; se incluye en el mensaje para
     *                  facilitar la trazabilidad.
     */
    public AsientoBloqueadoException(Long asientoId) {
        super("Asiento ya bloqueado: " + asientoId);
    }
}