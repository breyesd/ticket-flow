package com.ticketflow.reservation.domain.evento;

/**
 * Estados posibles de un {@link Asiento} dentro de una {@link Funcion}
 * (spec 0001, sección 2.4).
 *
 * <ul>
 *   <li>{@link #DISPONIBLE} — libre para reservar; es el estado por
 *       defecto al crear un asiento.</li>
 *   <li>{@link #VENDIDO} — vendido definitivamente tras un pago exitoso;
 *       no vuelve a {@link #DISPONIBLE}.</li>
 * </ul>
 *
 * <p>El estado de bloqueo durante la selección <strong>no</strong> se
 * persiste: vive únicamente en Redis como clave con TTL. Ver spec 0001,
 * sección 3.1.</p>
 */
public enum AsientoEstado {

    /**
     * El asiento está libre y puede ser seleccionado y reservado. Es el
     * valor por defecto al persistir un {@link Asiento} nuevo.
     */
    DISPONIBLE,

    /**
     * El asiento ha sido vendido definitivamente tras la confirmación de
     * una reserva con pago exitoso. Esta transición es unidireccional:
     * una vez {@code VENDIDO}, el asiento no vuelve a
     * {@link #DISPONIBLE}.
     */
    VENDIDO
}
