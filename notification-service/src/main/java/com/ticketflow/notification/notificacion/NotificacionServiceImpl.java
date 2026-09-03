package com.ticketflow.notification.notificacion;

import com.ticketflow.common.evento.ReservaConfirmadaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implementación del {@link NotificacionService} que simula el envío del
 * boleto registrando la operación en el log (spec 0001, sección 2.2;
 * plan Fase 4, F4.T4).
 *
 * <p>No efectúa ningún envío real; el registro en el log sirve como
 * evidencia observable (y verificable en tests) de que el evento fue
 * consumido y procesado. El mensaje incluye los datos del boleto para
 * facilitar la depuración y las futuras implementaciones reales de
 * correo.</p>
 */
@Service
public class NotificacionServiceImpl implements NotificacionService {

    /**
     * Logger dedicado de la clase, usado para registrar el envío
     * simulado del boleto.
     */
    private static final Logger LOGGER =
            LoggerFactory.getLogger(NotificacionServiceImpl.class);

    /**
     * Registra en el log el envío simulado del boleto de la reserva
     * confirmada.
     *
     * @param evento evento de reserva confirmada con los datos del
     *               boleto; no puede ser {@code null}.
     */
    @Override
    public void simularEnvio(ReservaConfirmadaEvent evento) {
        LOGGER.info("Boleto enviado (simulado) para la reserva {}: "
                        + "asiento {} de la función {} del evento {}, "
                        + "usuario {}, monto {}. Confirmada el {}.",
                evento.reservaId(), evento.asientoId(), evento.funcionId(),
                evento.eventoId(), evento.usuarioId(), evento.monto(),
                evento.fechaConfirmacion());
    }
}