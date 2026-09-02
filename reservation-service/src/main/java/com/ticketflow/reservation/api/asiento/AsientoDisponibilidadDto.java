package com.ticketflow.reservation.api.asiento;

import com.ticketflow.reservation.domain.evento.AsientoEstado;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO de salida del endpoint
 * {@code GET /api/v1/eventos/{eventoId}/funciones/{funcionId}/asientos}.
 *
 * <p>Representa la vista pública de un {@code Asiento} en la que solo
 * se exponen los campos relevantes para el cliente del API: número y
 * estado. Se omite el identificador interno y la referencia a la
 * función para no filtrar detalles de implementación ni permitir
 * enlaces directos por id.</p>
 *
 * @param numero número del asiento dentro de la función; único por
 *               función (restricción
 *               {@code uk_asiento_funcion_numero} en V1).
 * @param estado estado actual del asiento en la base de datos; el
 *               bloqueo durante la selección <strong>no</strong> se
 *               persiste, solo vive en Redis (spec 0001, sección
 *               3.1), por lo que este campo nunca refleja un lock
 *               activo.
 */
@Schema(description = "Vista pública de un asiento en una función.")
public record AsientoDisponibilidadDto(

        @Schema(description = "Número del asiento dentro de la función.",
                example = "1")
        Integer numero,

        @Schema(description = "Estado actual del asiento en la base de datos.",
                example = "DISPONIBLE")
        AsientoEstado estado
) {
}
