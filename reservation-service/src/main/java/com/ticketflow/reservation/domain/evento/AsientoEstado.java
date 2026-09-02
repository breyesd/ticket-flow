package com.ticketflow.reservation.domain.evento;

/**
 * Estados posibles de un {@link Asiento} dentro de una {@link Funcion}.
 *
 * <ul>
 *   <li>{@link #DISPONIBLE} — libre para reservar.</li>
 *   <li>{@link #VENDIDO} — vendido definitivamente tras un pago exitoso.</li>
 * </ul>
 *
 * <p>El estado de bloqueo durante la selección <strong>no</strong> se
 * persiste: vive únicamente en Redis como clave con TTL. Ver spec 0001
 * sección 2.4.</p>
 */
public enum AsientoEstado {
    DISPONIBLE,
    VENDIDO
}
