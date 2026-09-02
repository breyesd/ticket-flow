package com.ticketflow.reservation.lock;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketflow.reservation.support.EmbeddedRedisExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Tests de integración de {@link RedisSeatLockService} contra un Redis
 * embebido arrancado por {@link EmbeddedRedisExtension}.
 *
 * <p>Estos tests verifican el criterio de aceptación del plan F2.T2:
 * exclusión mutua, expiración por TTL y liberación por propietario
 * (script Lua). Se usan IDs de función y asiento grandes (a partir de
 * {@code 1_000_000}) para evitar colisiones con datos del seed del
 * perfil {@code test}.</p>
 *
 * <p>Cada test opera contra el bean inyectado por Spring; no se usan
 * mocks: la atomicidad de {@code SET NX PX} y la corrección del script
 * Lua solo pueden verificarse con un servidor Redis real.</p>
 */
@ExtendWith(EmbeddedRedisExtension.class)
@ActiveProfiles("test")
@SpringBootTest
class RedisSeatLockServiceTest {

    /**
     * Identificador de función base usado en los tests. Se elige un
     * valor alto para evitar colisiones con IDs reales del seed.
     */
    private static final Long FUNCION_ID = 1_000_000L;

    /**
     * Identificadores de asiento base; cada test usa una combinación
     * única para garantizar independencia entre tests.
     */
    private static final Long ASIENTO_UNO = 1L;
    private static final Long ASIENTO_DOS = 2L;
    private static final Long ASIENTO_TRES = 3L;
    private static final Long ASIENTO_CUATRO = 4L;
    private static final Long ASIENTO_CINCO = 5L;
    private static final Long ASIENTO_SEIS = 6L;
    private static final Long ASIENTO_SIETE = 7L;
    private static final Long ASIENTO_OCHO = 8L;

    /**
     * TTL "largo" usado para todos los tests salvo los de expiración.
     * Equivale a 60 segundos; suficientemente amplio para que ningún
     * test expire por motivos de tiempo.
     */
    private static final long TTL_LARGO_MS = 60_000L;

    /**
     * Bean de Spring bajo prueba. Es la sut (System Under Test) y el
     * contrato que F2.T3 y F2.T4 consumirán.
     */
    @Autowired
    private SeatLockService seatLockService;

    /**
     * Bean utilizado para limpiar claves residuales entre tests. Se
     * inyecta directamente para borrar sin pasar por la sut (la sut no
     * expone una operación de borrado masivo).
     */
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Verifica la exclusión mutua: una segunda adquisición sobre el
     * mismo par {@code (funcionId, asientoId)} debe fallar con
     * {@link SeatLockService.AcquireResult.AlreadyLocked}, y el token
     * devuelto por la primera adquisición no debe verse alterado.
     */
    @Test
    void acquireLock_thenSecondAcquireOnSameSeat_returnsAlreadyLocked() {
        limpiar(FUNCION_ID, ASIENTO_UNO);
        try {
            SeatLockService.AcquireResult first =
                    seatLockService.acquireLock(FUNCION_ID, ASIENTO_UNO, TTL_LARGO_MS);
            SeatLockService.AcquireResult second =
                    seatLockService.acquireLock(FUNCION_ID, ASIENTO_UNO, TTL_LARGO_MS);

            assertThat(first).isInstanceOf(SeatLockService.AcquireResult.Acquired.class);
            assertThat(second).isInstanceOf(SeatLockService.AcquireResult.AlreadyLocked.class);
            assertThat(((SeatLockService.AcquireResult.Acquired) first).token())
                    .isNotBlank();
        } finally {
            limpiar(FUNCION_ID, ASIENTO_UNO);
        }
    }

    /**
     * Verifica la expiración del TTL: tras
     * {@link Thread#sleep(long)} por encima del TTL, una nueva
     * adquisición debe tener éxito y devolver un token distinto al
     * inicial.
     */
    @Test
    void acquireLock_expiresAfterTtl_andCanBeAcquiredAgain() throws InterruptedException {
        limpiar(FUNCION_ID, ASIENTO_DOS);
        try {
            long ttlCortoMs = 100L;
            SeatLockService.AcquireResult first =
                    seatLockService.acquireLock(FUNCION_ID, ASIENTO_DOS, ttlCortoMs);
            assertThat(first).isInstanceOf(SeatLockService.AcquireResult.Acquired.class);
            String tokenPrimero = ((SeatLockService.AcquireResult.Acquired) first).token();

            Thread.sleep(150L);

            SeatLockService.AcquireResult segundo =
                    seatLockService.acquireLock(FUNCION_ID, ASIENTO_DOS, TTL_LARGO_MS);
            assertThat(segundo).isInstanceOf(SeatLockService.AcquireResult.Acquired.class);
            assertThat(((SeatLockService.AcquireResult.Acquired) segundo).token())
                    .as("un nuevo acquire post-expiración debe generar un token distinto")
                    .isNotEqualTo(tokenPrimero);
        } finally {
            limpiar(FUNCION_ID, ASIENTO_DOS);
        }
    }

    /**
     * Verifica la liberación por propietario: tras adquirir y
     * liberar con el token correcto, una nueva adquisición debe
     * tener éxito (devolviendo un token distinto).
     */
    @Test
    void releaseLock_withOwnerToken_returnsTrue_andAllowsReacquire() {
        limpiar(FUNCION_ID, ASIENTO_TRES);
        try {
            SeatLockService.AcquireResult first =
                    seatLockService.acquireLock(FUNCION_ID, ASIENTO_TRES, TTL_LARGO_MS);
            String token = ((SeatLockService.AcquireResult.Acquired) first).token();

            boolean released =
                    seatLockService.releaseLock(FUNCION_ID, ASIENTO_TRES, token);

            assertThat(released).isTrue();
            assertThat(seatLockService.isLocked(FUNCION_ID, ASIENTO_TRES)).isFalse();

            SeatLockService.AcquireResult reacquire =
                    seatLockService.acquireLock(FUNCION_ID, ASIENTO_TRES, TTL_LARGO_MS);
            assertThat(reacquire).isInstanceOf(SeatLockService.AcquireResult.Acquired.class);
            assertThat(((SeatLockService.AcquireResult.Acquired) reacquire).token())
                    .isNotEqualTo(token);
        } finally {
            limpiar(FUNCION_ID, ASIENTO_TRES);
        }
    }

    /**
     * Verifica que liberar con un token ajeno no borra la clave: el
     * lock sigue existiendo (atomicidad del script Lua).
     */
    @Test
    void releaseLock_withForeignToken_returnsFalse_andLeavesKeyIntact() {
        limpiar(FUNCION_ID, ASIENTO_CUATRO);
        try {
            SeatLockService.AcquireResult first =
                    seatLockService.acquireLock(FUNCION_ID, ASIENTO_CUATRO, TTL_LARGO_MS);
            assertThat(first).isInstanceOf(SeatLockService.AcquireResult.Acquired.class);
            String tokenReal = ((SeatLockService.AcquireResult.Acquired) first).token();

            boolean releasedAjeno = seatLockService.releaseLock(
                    FUNCION_ID, ASIENTO_CUATRO, tokenReal + "-ajeno");

            assertThat(releasedAjeno)
                    .as("un token que no coincide no debe poder liberar el lock")
                    .isFalse();
            assertThat(seatLockService.isLocked(FUNCION_ID, ASIENTO_CUATRO)).isTrue();
            assertThat(seatLockService.acquireLock(FUNCION_ID, ASIENTO_CUATRO, TTL_LARGO_MS))
                    .as("el lock del propietario real debe seguir vigente")
                    .isInstanceOf(SeatLockService.AcquireResult.AlreadyLocked.class);
        } finally {
            limpiar(FUNCION_ID, ASIENTO_CUATRO);
        }
    }

    /**
     * Verifica que {@link SeatLockService#releaseLock} devuelve
     * {@code false} (sin lanzar) cuando el token es {@code null} o
     * vacío, y que no altera la clave de Redis.
     */
    @Test
    void releaseLock_withNullOrEmptyToken_returnsFalse_andLeavesKeyIntact() {
        limpiar(FUNCION_ID, ASIENTO_CINCO);
        try {
            SeatLockService.AcquireResult first =
                    seatLockService.acquireLock(FUNCION_ID, ASIENTO_CINCO, TTL_LARGO_MS);
            assertThat(first).isInstanceOf(SeatLockService.AcquireResult.Acquired.class);

            assertThat(seatLockService.releaseLock(FUNCION_ID, ASIENTO_CINCO, null))
                    .as("token null no debe borrar el lock ni lanzar NPE")
                    .isFalse();
            assertThat(seatLockService.releaseLock(FUNCION_ID, ASIENTO_CINCO, ""))
                    .as("token vacío no debe borrar el lock")
                    .isFalse();
            assertThat(seatLockService.isLocked(FUNCION_ID, ASIENTO_CINCO)).isTrue();
        } finally {
            limpiar(FUNCION_ID, ASIENTO_CINCO);
        }
    }

    /**
     * Verifica el contrato de {@link SeatLockService#isLocked}:
     * {@code false} inicialmente, {@code true} tras adquirir,
     * {@code false} tras liberar.
     */
    @Test
    void isLocked_reflectsAcquireAndReleaseState() {
        limpiar(FUNCION_ID, ASIENTO_SEIS);
        try {
            assertThat(seatLockService.isLocked(FUNCION_ID, ASIENTO_SEIS))
                    .as("inicialmente el asiento no está lockeado")
                    .isFalse();

            SeatLockService.AcquireResult first =
                    seatLockService.acquireLock(FUNCION_ID, ASIENTO_SEIS, TTL_LARGO_MS);
            String token = ((SeatLockService.AcquireResult.Acquired) first).token();
            assertThat(seatLockService.isLocked(FUNCION_ID, ASIENTO_SEIS))
                    .as("tras acquire debe estar lockeado")
                    .isTrue();

            seatLockService.releaseLock(FUNCION_ID, ASIENTO_SEIS, token);
            assertThat(seatLockService.isLocked(FUNCION_ID, ASIENTO_SEIS))
                    .as("tras release con token válido debe dejar de estar lockeado")
                    .isFalse();
        } finally {
            limpiar(FUNCION_ID, ASIENTO_SEIS);
        }
    }

    /**
     * Verifica independencia entre asientos: adquirir sobre
     * {@code (funcionId, asientoA)} no afecta al estado de
     * {@code (funcionId, asientoB)} ni viceversa.
     */
    @Test
    void acquireLock_onDifferentSeats_areIndependent() {
        limpiar(FUNCION_ID, ASIENTO_SIETE);
        limpiar(FUNCION_ID, ASIENTO_OCHO);
        try {
            SeatLockService.AcquireResult a =
                    seatLockService.acquireLock(FUNCION_ID, ASIENTO_SIETE, TTL_LARGO_MS);
            SeatLockService.AcquireResult b =
                    seatLockService.acquireLock(FUNCION_ID, ASIENTO_OCHO, TTL_LARGO_MS);

            assertThat(a).isInstanceOf(SeatLockService.AcquireResult.Acquired.class);
            assertThat(b).as("un asiento distinto debe poder adquirirse en paralelo")
                    .isInstanceOf(SeatLockService.AcquireResult.Acquired.class);
            assertThat(seatLockService.isLocked(FUNCION_ID, ASIENTO_SIETE)).isTrue();
            assertThat(seatLockService.isLocked(FUNCION_ID, ASIENTO_OCHO)).isTrue();
        } finally {
            limpiar(FUNCION_ID, ASIENTO_SIETE);
            limpiar(FUNCION_ID, ASIENTO_OCHO);
        }
    }

    /**
     * Borra directamente la clave de Redis asociada al par
     * {@code (funcionId, asientoId)}. Se usa en los bloques
     * {@code finally} para que los tests no contaminen el estado del
     * Redis embebido entre ejecuciones, dado que el servidor se
     * mantiene vivo durante toda la JVM.
     *
     * @param funcionId identificador de función de la clave a borrar.
     * @param asientoId identificador de asiento de la clave a borrar.
     */
    private void limpiar(Long funcionId, Long asientoId) {
        redisTemplate.delete("lock:funcion:" + funcionId + ":asiento:" + asientoId);
    }
}
