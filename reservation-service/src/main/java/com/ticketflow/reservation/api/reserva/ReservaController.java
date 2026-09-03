package com.ticketflow.reservation.api.reserva;

import com.ticketflow.reservation.domain.evento.Asiento;
import com.ticketflow.reservation.domain.evento.AsientoBloqueadoException;
import com.ticketflow.reservation.domain.evento.AsientoEstado;
import com.ticketflow.reservation.domain.evento.AsientoNoPropietarioException;
import com.ticketflow.reservation.domain.evento.AsientoRepository;
import com.ticketflow.reservation.domain.evento.AsientoVendidoException;
import com.ticketflow.reservation.domain.evento.FuncionNotFoundException;
import com.ticketflow.reservation.domain.evento.FuncionRepository;
import com.ticketflow.reservation.lock.SeatLockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST del recurso {@code reserva} para la fase de
 * selección de asientos (spec 0001, sección 3.1).
 *
 * <p>Expone dos endpoints:</p>
 * <ul>
 *   <li>{@code POST /api/v1/reservas/bloquear}: permite a un cliente
 *       bloquear un asiento disponible durante 5 minutos (TTL) mientras
 *       completa el flujo de compra. El bloqueo se materializa como una
 *       clave en Redis con formato
 *       {@code lock:funcion:{funcionId}:asiento:{asientoId}}; no se
 *       crea ninguna fila en la base de datos en esta fase.</li>
 *   <li>{@code POST /api/v1/reservas/liberar}: permite la liberación
 *       manual idempotente del lock por parte del propietario (token
 *       devuelto en la adquisición). Si el lock no existe o ya expiró,
 *       la operación es un no-op que devuelve 204 (idempotencia). Si el
 *       token no coincide con el propietario actual, devuelve 409.</li>
 * </ul>
 *
 * <p>El endpoint de bloqueo valida que:</p>
 * <ul>
 *   <li>La función exista (404 si no).</li>
 *   <li>El asiento exista dentro de la función (404 si no).</li>
 *   <li>El asiento esté en estado {@link AsientoEstado#DISPONIBLE} en
 *       base de datos (422 si está {@link AsientoEstado#VENDIDO}).</li>
 *   <li>No exista ya un lock activo en Redis sobre el asiento (409
 *       si ya está bloqueado).</li>
 * </ul>
 *
 * <p>En caso de éxito en el bloqueo, devuelve un {@code reservationId}
 * (token opaco) que el cliente debe conservar para confirmar la compra
 * (Fase 3) o liberar manualmente (F2.T4).</p>
 */
@RestController
@RequestMapping("/api/v1/reservas")
@Tag(name = "Reservas", description = "Bloqueo y liberación de asientos en fase de selección.")
public class ReservaController {

    private final FuncionRepository funcionRepository;
    private final AsientoRepository asientoRepository;
    private final SeatLockService seatLockService;

    /**
     * Constructor con inyección por constructor.
     *
     * @param funcionRepository   repositorio de funciones; no puede ser {@code null}.
     * @param asientoRepository   repositorio de asientos; no puede ser {@code null}.
     * @param seatLockService     servicio de lock distribuido; no puede ser {@code null}.
     */
    public ReservaController(FuncionRepository funcionRepository,
                             AsientoRepository asientoRepository,
                             SeatLockService seatLockService) {
        this.funcionRepository = funcionRepository;
        this.asientoRepository = asientoRepository;
        this.seatLockService = seatLockService;
    }

    /**
     * Bloquea un asiento para la fase de selección.
     *
     * <p>Flujo:</p>
     * <ol>
     *   <li>Valida que la {@code funcionId} existe.</li>
     *   <li>Valida que el {@code asientoId} existe y pertenece a la función.</li>
     *   <li>Valida que el asiento está {@link AsientoEstado#DISPONIBLE} en BD.</li>
     *   <li>Intenta adquirir el lock en Redis con TTL de 5 minutos (300000 ms).</li>
     *   <li>Si el lock ya existe → 409 Conflict.</li>
     *   <li>Si se obtiene → 200 OK con {@link BloquearResponse} conteniendo el token.</li>
     * </ol>
     *
     * @param request petición con {@code funcionId} y {@code asientoId};
     *         no puede ser {@code null}.
     * @return {@link BloquearResponse} con el {@code reservationId}
     *         (token) si éxito; nunca {@code null}.
     * @throws FuncionNotFoundException     si la función no existe (mapeado a 404).
     * @throws AsientoVendidoException      si el asiento está VENDIDO (mapeado a 422).
     */
    @Operation(
            summary = "Bloquea un asiento para selección",
            description = "Intenta adquirir un lock distribuido sobre el "
                    + "asiento indicado durante 5 minutos. "
                    + "Devuelve un reservationId (token) que identifica al "
                    + "propietario del lock."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200",
                description = "Lock adquirido; devuelve reservationId."),
        @ApiResponse(responseCode = "404",
                description = "La función o el asiento no existen."),
        @ApiResponse(responseCode = "409",
                description = "El asiento ya está bloqueado por otro cliente."),
        @ApiResponse(responseCode = "422",
                description = "El asiento ya está vendido (estado VENDIDO en BD).")
    })
    @PostMapping("/bloquear")
    public ResponseEntity<BloquearResponse> bloquear(@RequestBody BloquearRequest request) {
        Long funcionId = request.funcionId();
        Long asientoId = request.asientoId();

        // 1. Validar que la función existe
        if (!funcionRepository.existsById(funcionId)) {
            throw new FuncionNotFoundException(null, funcionId);
        }

        // 2. Validar que el asiento existe y pertenece a la función
        Asiento asiento = asientoRepository.findById(asientoId)
                .orElseThrow(() -> new FuncionNotFoundException(
                        null, funcionId)); // Reuse 404 for seat not found

        if (!asiento.getFuncion().getId().equals(funcionId)) {
            throw new FuncionNotFoundException(null, funcionId);
        }

        // 3. Validar estado DISPONIBLE en BD
        if (asiento.getEstado() == AsientoEstado.VENDIDO) {
            throw new AsientoVendidoException(asientoId);
        }

        // 4. Intentar adquirir lock en Redis (TTL 5 minutos = 300000 ms)
        SeatLockService.AcquireResult result = seatLockService
                .acquireLock(funcionId, asientoId, 300_000L);

        if (result instanceof SeatLockService.AcquireResult.AlreadyLocked) {
            throw new AsientoBloqueadoException(asientoId);
        }

        // Success - return reservationId (token)
        SeatLockService.AcquireResult.Acquired acquired =
                (SeatLockService.AcquireResult.Acquired) result;
        BloquearResponse response = new BloquearResponse(acquired.token());
        return ResponseEntity.ok(response);
    }

    /**
     * Libera manualmente el lock de un asiento (idempotente).
     *
     * <p>Flujo:</p>
     * <ol>
     *   <li>Valida que la {@code funcionId} existe.</li>
     *   <li>Valida que el {@code asientoId} existe y pertenece a la función.</li>
     *   <li>Llama a {@link SeatLockService#releaseLock} con el token
     *       (reservationId) facilitado.</li>
     *   <li>Si {@code releaseLock} devuelve {@code true} → 204 No Content
     *       (lock liberado).</li>
     *   <li>Si {@code releaseLock} devuelve {@code false}:
     *       <ul>
     *         <li>Si no hay lock activo ({@link SeatLockService#isLocked}
     *             es {@code false}) → 204 No Content (idempotente).</li>
     *         <li>Si hay lock activo pero el token no coincide → 409
     *             Conflict (no es propietario).</li>
     *       </ul>
     *   </li>
     * </ol>
     *
     * @param request petición con {@code funcionId}, {@code asientoId} y
     *         {@code reservationId} (token); no puede ser {@code null}.
     * @return 204 No Content si se liberó o era idempotente; nunca
     *         {@code null}.
     * @throws FuncionNotFoundException        si la función no existe (mapeado a 404).
     * @throws AsientoNoPropietarioException   si el token no coincide con
     *         el propietario del lock activo (mapeado a 409).
     */
    @Operation(
            summary = "Libera manualmente el lock de un asiento",
            description = "Libera el lock distribuido sobre el asiento "
                    + "solo si el reservationId (token) coincide con el "
                    + "propietario actual. Es idempotente: si el lock ya "
                    + "no existe (expiró o fue liberado), devuelve 204 sin "
                    + "error. Si el token no coincide con el propietario "
                    + "actual, devuelve 409 Conflict."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204",
                description = "Lock liberado o ya no existía (idempotente)."),
        @ApiResponse(responseCode = "404",
                description = "La función o el asiento no existen."),
        @ApiResponse(responseCode = "409",
                description = "El token no coincide con el propietario del lock activo.")
    })
    @PostMapping("/liberar")
    public ResponseEntity<Void> liberar(@RequestBody LiberarRequest request) {
        Long funcionId = request.funcionId();
        Long asientoId = request.asientoId();
        String token = request.reservationId();

        // 1. Validar que la función existe
        if (!funcionRepository.existsById(funcionId)) {
            throw new FuncionNotFoundException(null, funcionId);
        }

        // 2. Validar que el asiento existe y pertenece a la función
        Asiento asiento = asientoRepository.findById(asientoId)
                .orElseThrow(() -> new FuncionNotFoundException(
                        null, funcionId));

        if (!asiento.getFuncion().getId().equals(funcionId)) {
            throw new FuncionNotFoundException(null, funcionId);
        }

        // 3. Intentar liberar el lock con el token proporcionado
        boolean released = seatLockService.releaseLock(funcionId, asientoId, token);

        if (released) {
            // Lock liberado con éxito
            return ResponseEntity.noContent().build();
        }

        // releaseLock devolvió false: no había lock o token no coincide
        // Diferenciar: si no hay lock → idempotente (204)
        // Si hay lock pero token no coincide → 409
        if (!seatLockService.isLocked(funcionId, asientoId)) {
            // No hay lock activo: operación idempotente
            return ResponseEntity.noContent().build();
        }

        // Hay lock pero token no coincide: no es propietario
        throw new AsientoNoPropietarioException(asientoId);
    }
}