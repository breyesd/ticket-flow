package com.ticketflow.reservation.domain.reserva;

/**
 * Excepción de dominio lanzada al confirmar una compra cuando el lock
 * de Redis del asiento no pertenece al {@code reservationId}
 * presentado, está expirado o ya no existe.
 *
 * <p>Representa una incoherencia entre la fase de selección (lock
 * efímero en Redis) y la fase de pago: el cliente intenta confirmar
 * una compra sobre un asiento cuyo lock no controla. El
 * {@code @RestControllerAdvice} central la traduce a una respuesta
 * HTTP 409 Conflict con cuerpo RFC 7807 (spec 0001, sección 2.5:
 * "lock expirado al confirmar").</p>
 */
public class ReservaLockInvalidoException extends RuntimeException {

    /**
     * Construye la excepción para el identificador de asiento cuyo
     * lock resultó inválido.
     *
     * @param asientoId identificador del asiento cuyo lock no
     *                  pertenece al propietario presentado, expiró o no
     *                  existe; se incluye en el mensaje para facilitar
     *                  la trazabilidad.
     */
    public ReservaLockInvalidoException(Long asientoId) {
        super("Lock inválido o expirado para el asiento: " + asientoId);
    }
}