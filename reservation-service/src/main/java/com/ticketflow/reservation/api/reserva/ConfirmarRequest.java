package com.ticketflow.reservation.api.reserva;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * DTO de entrada para el endpoint
 * {@code POST /api/v1/reservas/confirmar} (spec 0001, sección 2.5;
 * plan Fase 3, F3.T4).
 *
 * <p>Contiene la información necesaria para confirmar una compra:
 * el {@code reservationId} (token del lock obtenido en
 * {@code bloquear}) que demuestra la propiedad del lock, la función y
 * el asiento, el comprador y el importe a cobrar.</p>
 *
 * @param reservationId token de propietario del lock (reservationId)
 *                      devuelto al bloquear el asiento.
 * @param funcionId     identificador de la función a la que pertenece
 *                      el asiento.
 * @param asientoId     identificador del asiento a confirmar.
 * @param usuarioId     identificador del comprador que confirma la
 *                      reserva.
 * @param monto         importe a cobrar; debe ser mayor que cero.
 */
@Schema(description = "Petición para confirmar una compra (pago y persistencia).")
public record ConfirmarRequest(

        @Schema(description = "Token de propietario del lock (reservationId).",
                example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String reservationId,

        @Schema(description = "Identificador de la función.",
                example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long funcionId,

        @Schema(description = "Identificador del asiento dentro de la función.",
                example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long asientoId,

        @Schema(description = "Identificador del comprador.",
                example = "42",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long usuarioId,

        @Schema(description = "Importe a cobrar por la reserva.",
                example = "99.50",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal monto
) {
}