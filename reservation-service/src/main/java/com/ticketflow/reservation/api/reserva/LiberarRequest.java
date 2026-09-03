package com.ticketflow.reservation.api.reserva;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO de entrada para el endpoint {@code POST /api/v1/reservas/liberar}.
 *
 * <p>Contiene los identificadores necesarios para solicitar la liberación
 * manual de un lock de asiento durante la fase de selección (spec 0001,
 * sección 3.1). El {@code reservationId} es el token opaco devuelto por
 * el endpoint {@code POST /api/v1/reservas/bloquear} y demuestra la
 * propiedad del lock.</p>
 *
 * @param funcionId    identificador de la función a la que pertenece el
 *                     asiento; debe existir.
 * @param asientoId    identificador del asiento dentro de la función; debe
 *                     existir.
 * @param reservationId token de propietario (reservationId) devuelto al
 *                      bloquear el asiento; identifica al dueño del lock.
 */
@Schema(description = "Petición para liberar manualmente el lock de un asiento.")
public record LiberarRequest(

        @Schema(description = "Identificador de la función.",
                example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long funcionId,

        @Schema(description = "Identificador del asiento dentro de la función.",
                example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long asientoId,

        @Schema(description = "Token de propietario del lock (reservationId) devuelto al bloquear.",
                example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String reservationId
) {
}