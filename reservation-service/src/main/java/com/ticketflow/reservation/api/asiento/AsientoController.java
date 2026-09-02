package com.ticketflow.reservation.api.asiento;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST del recurso {@code asiento} dentro del servicio
 * de reservas.
 *
 * <p>Expone la consulta de disponibilidad de asientos de una
 * {@code Funcion} concreta, devolviendo una lista de
 * {@link AsientoDisponibilidadDto} con el número y estado de cada
 * asiento.</p>
 *
 * <p>El endpoint delega la lógica en {@link AsientoService} y deja
 * que las excepciones de dominio se propaguen hasta el
 * {@code @RestControllerAdvice} central, que las traduce a respuestas
 * RFC 7807.</p>
 */
@RestController
@RequestMapping("/api/v1/eventos/{eventoId}/funciones/{funcionId}/asientos")
@Tag(name = "Asientos", description = "Consulta de disponibilidad de asientos.")
public class AsientoController {

    private final AsientoService asientoService;

    /**
     * Constructor con inyección por constructor.
     *
     * @param asientoService servicio de aplicación con la lógica de
     *                       consulta; no puede ser {@code null}.
     */
    public AsientoController(AsientoService asientoService) {
        this.asientoService = asientoService;
    }

    /**
     * Lista los asientos de la función indicada (ordenados por
     * número).
     *
     * @param eventoId  identificador del evento; debe existir.
     * @param funcionId identificador de la función; debe existir y
     *                  pertenecer al evento.
     * @return lista de DTOs de disponibilidad. Nunca {@code null};
     *         vacía si la función no tiene asientos.
     */
    @Operation(
            summary = "Consulta de disponibilidad de asientos",
            description = "Devuelve la lista de asientos de la función con su número "
                    + "y estado actual.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de asientos."),
        @ApiResponse(responseCode = "404",
                description = "El evento o la función no existen.")
    })
    @GetMapping
    public List<AsientoDisponibilidadDto> getAsientos(
            @Parameter(description = "Identificador del evento.")
            @PathVariable Long eventoId,
            @Parameter(description = "Identificador de la función.")
            @PathVariable Long funcionId) {
        return asientoService.listarAsientos(eventoId, funcionId);
    }
}
