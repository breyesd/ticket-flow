package com.ticketflow.reservation.domain.evento;

/**
 * Excepción de dominio lanzada cuando se solicita un
 * {@link Evento} que no existe en la base de datos.
 *
 * <p>El {@code @RestControllerAdvice} central la traduce a una
 * respuesta HTTP 404 con cuerpo RFC 7807.</p>
 */
public class EventoNotFoundException extends RuntimeException {

    /**
     * Construye la excepción para el identificador de evento
     * indicado.
     *
     * @param eventoId identificador del evento que no se ha
     *                 encontrado; se incluye en el mensaje para
     *                 facilitar la trazabilidad.
     */
    public EventoNotFoundException(Long eventoId) {
        super("Evento no encontrado: " + eventoId);
    }
}
