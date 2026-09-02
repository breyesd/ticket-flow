package com.ticketflow.reservation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test de carga del contexto de Spring del servicio
 * {@code reservation-service} usando el perfil {@code test} (H2
 * embebido + Flyway + Redis autoconfigure excluido).
 *
 * <p>Es la verificación mínima de que todas las dependencias del
 * módulo están correctamente cableadas y de que la configuración por
 * defecto es coherente.</p>
 */
@ActiveProfiles("test")
@SpringBootTest
class TicketFlowReservationApplicationTest {

    /**
     * Verifica que el contexto de Spring Boot arranca sin errores
     * bajo el perfil {@code test}. Si alguna dependencia falta, una
     * bean está mal configurada o hay un conflicto de propiedades,
     * este test falla con la excepción subyacente.
     */
    @Test
    void contextLoads() {
    }
}
