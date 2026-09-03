package com.ticketflow.reservation.api.reserva;

import com.ticketflow.reservation.domain.reserva.ReservaEstado;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * DTO de salida para el endpoint
 * {@code POST /api/v1/reservas/confirmar} (spec 0001, sección 2.5;
 * plan Fase 3, F3.T4).
 *
 * <p>Devuelve el resumen de la reserva creada como resultado de la
 * confirmación: su identificador, el estado terminal y el importe
 * cobrado.</p>
 *
 * @param reservaId   identificador de la reserva persistida.
 * @param estado      estado terminal de la reserva
 *                    ({@link ReservaEstado#PAGADA}).
 * @param asientoId   identificador del asiento vendido.
 * @param monto       importe cobrado por la reserva.
 */
@Schema(description = "Respuesta de confirmación de compra exitosa.")
public record ConfirmarResponse(

        @Schema(description = "Identificador de la reserva creada.",
                example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long reservaId,

        @Schema(description = "Estado de la reserva.",
                example = "PAGADA",
                requiredMode = Schema.RequiredMode.REQUIRED)
        ReservaEstado estado,

        @Schema(description = "Identificador del asiento vendido.",
                example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long asientoId,

        @Schema(description = "Importe cobrado.",
                example = "99.50",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal monto
) {
}