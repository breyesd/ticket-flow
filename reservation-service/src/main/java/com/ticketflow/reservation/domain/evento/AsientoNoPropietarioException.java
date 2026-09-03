package com.ticketflow.reservation.domain.evento;

/**
 * Excepción de dominio lanzada cuando se intenta liberar un
 * {@link Asiento} cuyo lock pertenece a otro propietario (el token
 * facilitado no coincide con el almacenado en Redis).
 *
 * <p>El {@code @RestControllerAdvice} central la traduce a una
 * respuesta HTTP 409 Conflict con cuerpo RFC 7807.</p>
 */
public class AsientoNoPropietarioException extends RuntimeException {

    /**
     * Construye la excepción para el identificador de asiento
     * indicado.
     *
     * @param asientoId identificador del asiento cuyo lock pertenece
     *                  a otro propietario; se incluye en el mensaje
     *                  para facilitar la trazabilidad.
     */
    public AsientoNoPropietarioException(Long asientoId) {
        super("No es propietario del lock del asiento: " + asientoId);
    }
}