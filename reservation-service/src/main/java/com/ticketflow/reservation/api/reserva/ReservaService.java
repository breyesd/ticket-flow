package com.ticketflow.reservation.api.reserva;

import com.ticketflow.common.evento.ReservaConfirmadaEvent;
import com.ticketflow.reservation.domain.evento.Asiento;
import com.ticketflow.reservation.domain.evento.AsientoEstado;
import com.ticketflow.reservation.domain.evento.AsientoRepository;
import com.ticketflow.reservation.domain.evento.AsientoVendidoException;
import com.ticketflow.reservation.domain.evento.FuncionNotFoundException;
import com.ticketflow.reservation.domain.evento.FuncionRepository;
import com.ticketflow.reservation.domain.reserva.Reserva;
import com.ticketflow.reservation.domain.reserva.ReservaEstado;
import com.ticketflow.reservation.domain.reserva.ReservaRepository;
import com.ticketflow.reservation.domain.reserva.ReservaLockInvalidoException;
import com.ticketflow.reservation.lock.SeatLockService;
import com.ticketflow.reservation.pago.PaymentGateway;
import com.ticketflow.reservation.pago.PaymentRequest;
import com.ticketflow.reservation.pago.PaymentResult;
import java.time.OffsetDateTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de aplicación que ejecuta la transacción completa de
 * confirmación de compra (spec 0001, sección 3.2; plan Fase 3, F3.T4).
 *
 * <p>Orquesta la fase de pago y persistencia. El método
 * {@link #confirmar(ConfirmarRequest)} corre dentro de una transacción
 * {@link Transactional @Transactional} que:</p>
 * <ol>
 *   <li>Valida que la función y el asiento existen y que el asiento
 *       pertenece a la función.</li>
 *   <li>Verifica que el {@code reservationId} sigue siendo el
 *       propietario del lock Redis del asiento (si expiró o no
 *       pertenece, lanza {@link ReservaLockInvalidoException} → 409).</li>
 *   <li>Recupera el asiento con bloqueo pesimista
 *       ({@code SELECT ... FOR UPDATE}).</li>
 *   <li>Verifica que el asiento está {@link AsientoEstado#DISPONIBLE};
 *       si está {@link AsientoEstado#VENDIDO}, lanza
 *       {@link AsientoVendidoException} → 409 y fuerza rollback.</li>
 *   <li>Cobra vía {@link PaymentGateway}.</li>
 * </ol>
 *
 * <p>El pago exitoso deja el asiento en {@code VENDIDO}, crea una
 * {@link Reserva} {@link ReservaEstado#PAGADA} y libera el lock Redis;
 * el pago rechazado conserva el asiento {@code DISPONIBLE}, crea una
 * {@link Reserva} {@link ReservaEstado#FALLIDA} y libera el lock. En
 * ambos casos la reserva se persiste en la transacción; el fallo de pago
 * se comunica al controlador mediante {@link ConfirmacionResult.Fallida}
 * (que se mapea a 422) sin deshacer la transacción, de modo que la
 * reserva {@code FALLIDA} queda registrada.</p>
 *
 * <p>El orden de liberación del lock Redis es best-effort: se libera
 * tras confirmar la persistencia dentro del mismo flujo; si la
 * liberación fallara, el TTL del lock lo recupera (spec 0001, §3.2).</p>
 */
@Service
public class ReservaService {

    private final FuncionRepository funcionRepository;
    private final AsientoRepository asientoRepository;
    private final ReservaRepository reservaRepository;
    private final SeatLockService seatLockService;
    private final PaymentGateway paymentGateway;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Constructor con inyección por constructor.
     *
     * @param funcionRepository   repositorio de funciones; no puede ser
     *                            {@code null}.
     * @param asientoRepository   repositorio de asientos; no puede ser
     *                            {@code null}.
     * @param reservaRepository   repositorio de reservas; no puede ser
     *                            {@code null}.
     * @param seatLockService     servicio de lock distribuido; no puede
     *                            ser {@code null}.
     * @param paymentGateway      pasarela de pago; no puede ser
     *                            {@code null}.
     * @param eventPublisher      publicador de eventos de dominio de
     *                            Spring; no puede ser {@code null}.
     */
    public ReservaService(FuncionRepository funcionRepository,
                          AsientoRepository asientoRepository,
                          ReservaRepository reservaRepository,
                          SeatLockService seatLockService,
                          PaymentGateway paymentGateway,
                          ApplicationEventPublisher eventPublisher) {
        this.funcionRepository = funcionRepository;
        this.asientoRepository = asientoRepository;
        this.reservaRepository = reservaRepository;
        this.seatLockService = seatLockService;
        this.paymentGateway = paymentGateway;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Confirmación de compra de un asiento bloqueado.
     *
     * @param request petición con {@code reservationId},
     *                {@code funcionId}, {@code asientoId},
     *                {@code usuarioId} y {@code monto}; no puede ser
     *                {@code null}.
     * @return {@link ConfirmacionResult.Exitosa} con el resumen de la
     *         reserva {@code PAGADA} si el cobro tuvo éxito, o
     *         {@link ConfirmacionResult.Fallida} con el motivo si el
     *         cobro fue rechazado. Nunca {@code null}.
     * @throws FuncionNotFoundException  si la función no existe o el
     *                                   asiento no le pertenece (404).
     * @throws ReservaLockInvalidoException si el lock no pertenece al
     *                                   {@code reservationId}, expiró o no
     *                                   existe (409).
     * @throws AsientoVendidoException   si el asiento ya está
     *                                   {@code VENDIDO} (409, con
     *                                   rollback).
     */
    @Transactional
    public ConfirmacionResult confirmar(ConfirmarRequest request) {
        Long funcionId = request.funcionId();
        Long asientoId = request.asientoId();
        String token = request.reservationId();

        // 1. Validar que el asiento existe y pertenece a la función.
        Asiento asiento = asientoRepository.findById(asientoId)
                .orElseThrow(() -> new FuncionNotFoundException(null, funcionId));
        if (!asiento.getFuncion().getId().equals(funcionId)
                || !funcionRepository.existsById(funcionId)) {
            throw new FuncionNotFoundException(null, funcionId);
        }

        // 2. Validar que el reservationId sigue siendo dueño del lock.
        if (!seatLockService.isOwner(funcionId, asientoId, token)) {
            throw new ReservaLockInvalidoException(asientoId);
        }

        // 3. Bloqueo pesimista sobre el asiento (SELECT ... FOR UPDATE).
        Asiento bloqueado = asientoRepository.findByIdForUpdate(asientoId)
                .orElseThrow(() -> new FuncionNotFoundException(null, funcionId));

        // 4. Verificar estado DISPONIBLE; si está VENDIDO → 409 + rollback.
        if (bloqueado.getEstado() == AsientoEstado.VENDIDO) {
            throw new AsientoVendidoException(asientoId);
        }

        // 5. Cobrar vía la pasarela de pago.
        PaymentResult payment = paymentGateway.charge(new PaymentRequest(request.monto()));

        if (payment.exitoso()) {
            return confirmarPagada(bloqueado, request, token);
        }
        return confirmarFallida(bloqueado, request, payment.motivo(), token);
    }

    /**
     * Concreta la compra con pago exitoso: transiciona el asiento a
     * {@code VENDIDO}, persiste una {@link Reserva} {@code PAGADA},
     * emite el evento de dominio {@link ReservaConfirmadaEvent} y
     * libera el lock Redis (best-effort).
     *
     * <p>El evento se publica mediante el
     * {@link ApplicationEventPublisher} de Spring <strong>después de
     * confirmarse la transacción</strong> (post-commit): la publicación
     * real se delega en un <em>listener</em> transaccional anotado con
     * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} que
     * invoca al transportador Kafka. Así se garantiza que jamás se
     * emite un evento de una compra cuya transacción luego falle
     * (spec 0001, §3.2 y §4).</p>
     *
     * @param asiento asiento bajo bloqueo pesimista a vender.
     * @param request petición original de confirmación.
     * @param token   token de propietario del lock.
     * @return resultado exitoso con el resumen de la reserva {@code PAGADA}.
     */
    private ConfirmacionResult confirmarPagada(Asiento asiento,
                                               ConfirmarRequest request,
                                               String token) {
        asiento.vender();
        Reserva reserva = new Reserva(request.usuarioId(), asiento,
                request.monto(), ReservaEstado.PAGADA);
        reservaRepository.save(reserva);

        ReservaConfirmadaEvent evento = new ReservaConfirmadaEvent(
                reserva.getId(),
                asiento.getFuncion().getEvento().getId(),
                request.funcionId(),
                asiento.getId(),
                request.usuarioId(),
                reserva.getMonto(),
                OffsetDateTime.now());
        eventPublisher.publishEvent(evento);

        seatLockService.releaseLock(request.funcionId(), request.asientoId(), token);

        return new ConfirmacionResult.Exitosa(new ConfirmarResponse(
                reserva.getId(), reserva.getEstado(),
                asiento.getId(), reserva.getMonto()));
    }

    /**
     * Registra el rechazo del pago: conserva el asiento
     * {@code DISPONIBLE}, persiste una {@link Reserva} {@code FALLIDA}
     * y libera el lock Redis (best-effort).
     *
     * @param asiento asiento (sin vender) sobre el que recayó el intento.
     * @param request petición original de confirmación.
     * @param motivo  motivo del rechazo proporcionado por la pasarela.
     * @param token   token de propietario del lock.
     * @return resultado fallido con el motivo del rechazo.
     */
    private ConfirmacionResult confirmarFallida(Asiento asiento,
                                                ConfirmarRequest request,
                                                String motivo,
                                                String token) {
        Reserva reserva = new Reserva(request.usuarioId(), asiento,
                request.monto(), ReservaEstado.FALLIDA);
        reservaRepository.save(reserva);

        seatLockService.releaseLock(request.funcionId(), request.asientoId(), token);

        return new ConfirmacionResult.Fallida(motivo);
    }

    /**
     * Resultado de la confirmación de compra. Se modela como jerarquía
     * sellada (Java 21) para forzar el manejo de ambos casos en el
     * controlador: pago aceptado (reserva {@code PAGADA}) o pago
     * rechazado (reserva {@code FALLIDA}, mapeado a 422).
     */
    public sealed interface ConfirmacionResult {

        /**
         * Confirmación con pago aceptado: el asiento quedó
         * {@code VENDIDO} y se creó una reserva {@code PAGADA}.
         *
         * @param response resumen de la reserva creada.
         */
        record Exitosa(ConfirmarResponse response) implements ConfirmacionResult {
        }

        /**
         * Confirmación con pago rechazado: el asiento permaneció
         * {@code DISPONIBLE} y se creó una reserva {@code FALLIDA}.
         *
         * @param motivo motivo del rechazo proporcionado por la pasarela.
         */
        record Fallida(String motivo) implements ConfirmacionResult {
        }
    }
}