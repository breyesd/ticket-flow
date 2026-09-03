package com.ticketflow.reservation.domain.evento;

/**
 * Excepción de dominio lanzada cuando se intenta bloquear o confirmar
 * la compra de un {@link Asiento} que ya está en estado
 * {@link AsientoEstado#VENDIDO}.
 *
 * <p>El {@code @RestControllerAdvice} central la traduce a una
 * respuesta HTTP 409 Conflict con cuerpo RFC 7807 (spec 0001, sección
 * 2.5: "asiento ya VENDIDO").</p>
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