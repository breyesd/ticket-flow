package com.ticketflow.reservation.pago;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Test unitario de {@link PaymentGatewayMock} (plan Fase 3, F3.T3).
 *
 * <p>Verifica el comportamiento predecible y configurable del mock:</p>
 * <ul>
 *   <li>con el switch de fallo desactivado, el cobro es exitoso
 *       (éxito por defecto);</li>
 *   <li>con el switch de fallo activado, el cobro es rechazado y el
 *       resultado expone un motivo no vacío.</li>
 * </ul>
 */
class PaymentGatewayMockTest {

    /**
     * Petición de cobro de ejemplo compartida por los tests.
     */
    private static final PaymentRequest REQUEST =
            new PaymentRequest(new BigDecimal("99.50"));

    /**
     * Verifica que con el switch de fallo desactivado
     * ({@code app.payment.mock.fail == false}) el mock devuelve éxito.
     */
    @Test
    void chargeDevuelveExitoPorDefecto() {
        PaymentGatewayMock gateway = new PaymentGatewayMock(false);

        PaymentResult result = gateway.charge(REQUEST);

        assertThat(result.exitoso()).isTrue();
        assertThat(result.motivo()).isNull();
    }

    /**
     * Verifica que con el switch de fallo activado
     * ({@code app.payment.mock.fail == true}) el mock devuelve un
     * resultado fallido con un motivo no vacío.
     */
    @Test
    void chargeDevuelveFalloAlActivarSwitch() {
        PaymentGatewayMock gateway = new PaymentGatewayMock(true);

        PaymentResult result = gateway.charge(REQUEST);

        assertThat(result.exitoso()).isFalse();
        assertThat(result.motivo()).isNotBlank();
    }
}