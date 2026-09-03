package com.ticketflow.reservation.pago;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Implementación mock de {@link PaymentGateway} para simular el
 * procesamiento del pago de forma predecible y configurable (spec 0001,
 * sección 5; plan Fase 3, F3.T3).
 *
 * <p>Comportamiento:</p>
 * <ul>
 *   <li>Por defecto el cobro es <strong>exitoso</strong>
 *       ({@link PaymentResult#exito()}).</li>
 *   <li>Si la propiedad {@code app.payment.mock.fail} vale {@code true},
 *       el cobro se <strong>rechaza</strong> y se devuelve un
 *       {@link PaymentResult} fallido con motivo. Esto permite inyectar
 *       fallos determinísticos en las pruebas de rollback del flujo de
 *       confirmación.</li>
 * </ul>
 */
@Component
public class PaymentGatewayMock implements PaymentGateway {

    /**
     * Nombre de la propiedad que fuerza el fallo del cobro. Cuando está
     * activa, {@link #charge(PaymentRequest)} devuelve siempre un
     * resultado fallido.
     */
    public static final String FAIL_PROPERTY = "app.payment.mock.fail";

    /**
     * Motivo del rechazo devuelto cuando la propiedad
     * {@link #FAIL_PROPERTY} está activa.
     */
    private static final String MOTIVO_FALLO = "Pago rechazado por el proveedor mock";

    /**
     * Flag leído de la configuración que determina si el mock debe
     * forzar el fallo. Inyectado vía {@link Value} con un valor por
     * defecto {@code false}.
     */
    private final boolean fail;

    /**
     * Constructor con inyección por constructor.
     *
     * @param fail valor de {@code app.payment.mock.fail}; {@code false}
     *             por defecto cuando la propiedad no está definida.
     */
    public PaymentGatewayMock(@Value("${" + FAIL_PROPERTY + ":false}") boolean fail) {
        this.fail = fail;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Devuelve {@link PaymentResult#exito()} salvo que
     * {@code app.payment.mock.fail} esté activa, en cuyo caso devuelve
     * {@link PaymentResult#fallo(String)} con {@link #MOTIVO_FALLO}.</p>
     */
    @Override
    public PaymentResult charge(PaymentRequest request) {
        if (fail) {
            return PaymentResult.fallo(MOTIVO_FALLO);
        }
        return PaymentResult.exito();
    }
}