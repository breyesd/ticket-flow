package com.ticketflow.reservation.api.reserva;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO de entrada para el endpoint {@code POST /api/v1/reservas/bloquear}.
 *
 * <p>Contiene los identificadores mínimos para solicitar el bloqueo de
 * un asiento durante la fase de selección (spec 0001, sección 3.1).
 * El bloqueo se materializa como una clave en Redis con TTL de
 * 5 minutos; no se crea ninguna fila en la base de datos.</p>
 *
 * @param funcionId identificador de la función a la que pertenece el
 *                  asiento; debe existir.
 * @param asientoId identificador del asiento dentro de la función; debe
 *                  existir y estar en estado {@code DISPONIBLE}.
 */
@Schema(description = "Petición para bloquear un asiento durante la selección.")
public record BloquearRequest(

        @Schema(description = "Identificador de la función.",
                example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long funcionId,

        @Schema(description = "Identificador del asiento dentro de la función.",
                example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long asientoId
) {
}