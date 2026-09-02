package com.ticketflow.reservation.domain.evento;

/**
 * Excepción de dominio lanzada cuando se solicita una {@link Funcion}
 * que no existe o no pertenece al evento indicado.
 *
 * <p>El {@code @RestControllerAdvice} central la traduce a una
 * respuesta HTTP 404 con cuerpo RFC 7807.</p>
 */
public class FuncionNotFoundException extends RuntimeException {

    /**
     * Construye la excepción para el par (eventoId, funcionId)
     * indicado.
     *
     * @param eventoId  identificador del evento bajo el que se
     *                  buscaba la función.
     * @param funcionId identificador de la función que no se ha
     *                  encontrado.
     */
    public FuncionNotFoundException(Long eventoId, Long funcionId) {
        super("Función no encontrada: " + funcionId + " (evento " + eventoId + ")");
    }
}
