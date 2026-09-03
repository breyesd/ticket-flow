package com.ticketflow.reservation.api.reserva;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO de salida para el endpoint {@code POST /api/v1/reservas/bloquear}.
 *
 * <p>Devuelve el {@code reservationId} (token opaco) que identifica
 * al propietario del lock en Redis. Este token debe presentarse en la
 * fase de confirmación (Fase 3) y en la liberación manual
 * ({@code POST /api/v1/reservas/liberar}, F2.T4) para demostrar la
 * propiedad del lock. No corresponde a un identificador persistido en
 * base de datos.</p>
 *
 * @param reservationId token UUID v4 generado por el servidor al
 *                      adquirir el lock; único por adquisición
 *                      exitosa.
 */
@Schema(description = "Respuesta de bloqueo exitoso con token de propietario.")
public record BloquearResponse(

        @Schema(description = "Token de propietario del lock (reservationId).",
                example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String reservationId
) {
}