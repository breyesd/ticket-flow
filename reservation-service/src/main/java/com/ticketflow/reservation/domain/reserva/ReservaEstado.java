package com.ticketflow.reservation.domain.reserva;

/**
 * Estados posibles de una {@link Reserva} (spec 0001, sección 2.4).
 *
 * <p>A diferencia del {@code Asiento}, la reserva no tiene un estado
 * intermedio "pendiente": la intención de reserva (bloqueo durante la
 * selección) vive solo en Redis como lock efímero y nunca se persiste.
 * La {@link Reserva} se crea en el flujo {@code confirmar} directamente
 * con uno de estos dos estados terminales:</p>
 * <ul>
 *   <li>{@link #PAGADA} — el pago fue exitoso y el asiento pasó a
 *       {@code VENDIDO}.</li>
 *   <li>{@link #FALLIDA} — el pago fue rechazado; el lock se libera y
 *       el asiento permanece {@code DISPONIBLE}.</li>
 * </ul>
 */
public enum ReservaEstado {

    /**
     * Pago exitoso: la compra se concretó y el asiento asociado quedó
     * en estado {@code VENDIDO}. Es el resultado de una confirmación
     * que superó el pago simulado.
     */
    PAGADA,

    /**
     * Pago rechazado: la compra no se concretó y el asiento asociado
     * permanece {@code DISPONIBLE}, con su lock liberado para permitir
     * un nuevo intento de compra.
     */
    FALLIDA
}