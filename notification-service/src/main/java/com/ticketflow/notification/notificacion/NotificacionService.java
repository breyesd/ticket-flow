package com.ticketflow.notification.notificacion;

import com.ticketflow.common.evento.ReservaConfirmadaEvent;

/**
 * Contrato del servicio de notificación que simula el envío del boleto
 * al confirmarse una reserva (spec 0001, sección 2.2; plan Fase 4,
 * F4.T4).
 *
 * <p>En esta iteración no existe lógica real de correo/SMS/push: la
 * implementación registra que el boleto del evento fue "enviado". El
 * contrato se define como interfaz para que en el futuro puedan
 * convivir varios canales de notificación (email, SMS, push) sin tocar
 * el core del {@code notification-service}.</p>
 */
public interface NotificacionService {

    /**
     * Simula el envío del boleto asociado a una reserva confirmada.
     *
     * <p>La implementación registra la operación (p. ej. en el log o en
     * memoria) como evidencia de que el evento fue consumido y
     * procesado, sin efectuar ningún envío real.</p>
     *
     * @param evento evento de reserva confirmada con los datos del
     *               boleto (reserva, evento, función, asiento, usuario,
     *               monto y fecha); no puede ser {@code null}.
     */
    void simularEnvio(ReservaConfirmadaEvent evento);
}