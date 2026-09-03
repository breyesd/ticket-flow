package com.ticketflow.reservation.lock;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketflow.reservation.support.EmbeddedRedisExtension;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test de integración de concurrencia para {@link RedisSeatLockService}
 * (plan F2.T5).
 *
 * <p>Este test valida la exclusión mutua real del lock distribuido bajo
 * concurrencia: 20 hilos simultáneos intentando {@code acquireLock} sobre
 * el mismo asiento. Verifica que exactamente 1 hilo obtiene el lock y los
 * otros 19 fallan (método de exclusión mutua garantizado por {@code SET
 * NX}). Tras liberar el lock, verifica que un nuevo intento tiene éxito.</p>
 *
 * <p>La implementación de {@code SeatLockService} (F2.T2) ya es atómica;
 * este test solo valida que la atomicidad aguanta 20 hilos concurrentes
 * reales.</p>
 *
 * @since 1.0
 */
@ExtendWith(EmbeddedRedisExtension.class)
@ActiveProfiles("test")
@SpringBootTest
class SeatLockConcurrencyTest {

    /**
     * Número de hilos concurrentes para la prueba de estrés, fijado en 20
     * según el plan F2.T5.
     */
    private static final int NUM_HILOS = 20;

    /**
     * Identificador de función para la prueba. Se elige un valor alto para
     * evitar colisiones con datos del seed del perfil {@code test}.
     */
    private static final Long FUNCION_ID = 1_000_001L;

    /**
     * Identificador de asiento para la prueba.
     */
    private static final Long ASIENTO_ID = 1L;

    /**
     * TTL del lock en milisegundos (5 minutos = 300000 ms), según spec
     * 0001 §3.1.
     */
    private static final long TTL_MS = 300_000L;

    /**
     * Bean de Spring bajo prueba. Inyección por constructor vía
     * {@code @Autowired}.
     */
    @Autowired
    private SeatLockService seatLockService;

    /**
     * Bean utilizado para limpiar claves residuales entre tests.
     */
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Limpia la clave de Redis asociada al par {@code (FUNCION_ID,
     * ASIENTO_ID)} tras la ejecución de todos los tests de la clase.
     *
     * <p>Se ejecuta una sola vez al final para evitar contaminar el estado
     * del Redis embebido, que se mantiene vivo durante toda la JVM.</p>
     */
    @AfterAll
    static void limpiarGlobal(@Autowired RedisTemplate<String, Object> redisTemplate) {
        redisTemplate.delete(RedisSeatLockService.keyFor(FUNCION_ID, ASIENTO_ID));
    }

    /**
     * Verifica la exclusión mutua bajo concurrencia real: 20 hilos
     * simultáneos intentando adquirir el lock del mismo asiento.
     *
     * <p>Pasos:</p>
     * <ol>
     *   <li>Arrancar 20 hilos con {@code ExecutorService.newFixedThreadPool(20)}.</li>
     *   <li>Sincronizar arranque con {@code CountDownLatch} (1 latch para
     *       arranque simultáneo, 1 latch para esperar finalización).</li>
     *   <li>Cada hilo llama a {@code seatLockService.acquireLock(FUNCION_ID,
     *       ASIENTO_ID, TTL_MS)}.</li>
     *   <li>Cada hilo registra su resultado: {@code ACQUIRED(token)} o
     *       {@code ALREADY_LOCKED}.</li>
     *   <li>Esperar a que todos terminen.</li>
     *   <li>Asertar: exactamente 1 {@code ACQUIRED}, 19
     *       {@code ALREADY_LOCKED}.</li>
     *   <li>Verificar que el lock sigue activo con {@code isLocked} (el
     *       dueño no lo liberó).</li>
     *   <li>Liberar el lock con el token del ganador.</li>
     *   <li>Verificar que {@code isLocked} → {@code false}.</li>
     *   <li>Nuevo {@code acquireLock} → éxito (nuevo token).</li>
     * </ol>
     *
     * @throws InterruptedException si la espera de los latches es
     *                                interrumpida.
     */
    @Test
    void veinteHilosIntentanAdquirirMismoAsiento_soloUnoTieneExito()
            throws InterruptedException {
        // Limpieza inicial
        redisTemplate.delete(RedisSeatLockService.keyFor(FUNCION_ID, ASIENTO_ID));

        ExecutorService executor = Executors.newFixedThreadPool(NUM_HILOS);
        CountDownLatch latchArranque = new CountDownLatch(1);
        CountDownLatch latchFinalizacion = new CountDownLatch(NUM_HILOS);

        AtomicInteger adquiridos = new AtomicInteger(0);
        AtomicInteger yaBloqueados = new AtomicInteger(0);
        AtomicReference<String> tokenGanador = new AtomicReference<>();

        try {
            for (int i = 0; i < NUM_HILOS; i++) {
                executor.submit(() -> {
                    try {
                        // Esperar señal de arranque simultáneo
                        latchArranque.await();
                        SeatLockService.AcquireResult resultado =
                                seatLockService.acquireLock(FUNCION_ID, ASIENTO_ID, TTL_MS);
                        if (resultado instanceof SeatLockService.AcquireResult.Acquired acquired) {
                            adquiridos.incrementAndGet();
                            tokenGanador.set(acquired.token());
                        } else if (resultado instanceof
                                SeatLockService.AcquireResult.AlreadyLocked) {
                            yaBloqueados.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latchFinalizacion.countDown();
                    }
                });
            }

            // Disparar arranque simultáneo
            latchArranque.countDown();

            // Esperar a que todos terminen (con timeout generoso)
            assertThat(latchFinalizacion.await(30, TimeUnit.SECONDS))
                    .as("todos los hilos deben finalizar en menos de 30s")
                    .isTrue();

            // Asertar: exactamente 1 ACQUIRED, 19 ALREADY_LOCKED
            assertThat(adquiridos.get())
                    .as("exactamente un hilo debe adquirir el lock")
                    .isEqualTo(1);
            assertThat(yaBloqueados.get())
                    .as("los otros 19 hilos deben recibir AlreadyLocked")
                    .isEqualTo(NUM_HILOS - 1);

            // Verificar que el lock sigue activo (el dueño no lo liberó)
            assertThat(seatLockService.isLocked(FUNCION_ID, ASIENTO_ID))
                    .as("el lock debe seguir activo tras la adquisición")
                    .isTrue();

            // Liberar el lock con el token del ganador
            String token = tokenGanador.get();
            assertThat(token).as("debe existir un token ganador").isNotNull();

            boolean liberado = seatLockService.releaseLock(FUNCION_ID, ASIENTO_ID, token);
            assertThat(liberado).as("releaseLock con token correcto debe retornar true").isTrue();

            // Verificar que isLocked → false
            assertThat(seatLockService.isLocked(FUNCION_ID, ASIENTO_ID))
                    .as("tras release, isLocked debe ser false")
                    .isFalse();

            // Nuevo acquireLock → éxito (nuevo token)
            SeatLockService.AcquireResult nuevoAcquire =
                    seatLockService.acquireLock(FUNCION_ID, ASIENTO_ID, TTL_MS);
            assertThat(nuevoAcquire)
                    .as("tras liberar, un nuevo acquire debe tener éxito")
                    .isInstanceOf(SeatLockService.AcquireResult.Acquired.class);
            assertThat(((SeatLockService.AcquireResult.Acquired) nuevoAcquire).token())
                    .as("el nuevo token debe ser distinto al anterior")
                    .isNotEqualTo(token);

        } finally {
            executor.shutdownNow();
            // Limpieza final (belt-and-suspenders)
            redisTemplate.delete(RedisSeatLockService.keyFor(FUNCION_ID, ASIENTO_ID));
        }
    }

    /**
     * Verifica que después de liberar el lock, otro hilo puede adquirirlo
     * exitosamente.
     *
     * <p>Este test es una variante simplificada que se centra exclusivamente
     * en la propiedad de re-adquisición tras liberación.</p>
     *
     * @throws InterruptedException si la espera de los latches es
     *                                interrumpida.
     */
    @Test
    void despuesDeLiberarOtroHiloPuedeAdquirir() throws InterruptedException {
        // Limpieza inicial
        redisTemplate.delete(RedisSeatLockService.keyFor(FUNCION_ID, ASIENTO_ID));

        ExecutorService executor = Executors.newFixedThreadPool(NUM_HILOS);
        CountDownLatch latchArranque = new CountDownLatch(1);
        CountDownLatch latchFinalizacion = new CountDownLatch(NUM_HILOS);

        AtomicInteger adquiridos = new AtomicInteger(0);
        AtomicReference<String> tokenGanador = new AtomicReference<>();

        try {
            // Primera ronda: 20 hilos, solo 1 debe ganar
            for (int i = 0; i < NUM_HILOS; i++) {
                executor.submit(() -> {
                    try {
                        latchArranque.await();
                        SeatLockService.AcquireResult resultado =
                                seatLockService.acquireLock(FUNCION_ID, ASIENTO_ID, TTL_MS);
                        if (resultado instanceof SeatLockService.AcquireResult.Acquired acquired) {
                            adquiridos.incrementAndGet();
                            tokenGanador.set(acquired.token());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latchFinalizacion.countDown();
                    }
                });
            }

            latchArranque.countDown();
            assertThat(latchFinalizacion.await(30, TimeUnit.SECONDS)).isTrue();
            assertThat(adquiridos.get()).isEqualTo(1);

            // Liberar con el token del ganador
            String token = tokenGanador.get();
            assertThat(token).isNotNull();
            boolean liberado = seatLockService.releaseLock(FUNCION_ID, ASIENTO_ID, token);
            assertThat(liberado).isTrue();

            // Segunda ronda: nuevo latch para sincronizar
            CountDownLatch latchArranque2 = new CountDownLatch(1);
            CountDownLatch latchFinalizacion2 = new CountDownLatch(NUM_HILOS);
            AtomicInteger adquiridosRonda2 = new AtomicInteger(0);

            for (int i = 0; i < NUM_HILOS; i++) {
                executor.submit(() -> {
                    try {
                        latchArranque2.await();
                        SeatLockService.AcquireResult resultado =
                                seatLockService.acquireLock(FUNCION_ID, ASIENTO_ID, TTL_MS);
                        if (resultado instanceof SeatLockService.AcquireResult.Acquired) {
                            adquiridosRonda2.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latchFinalizacion2.countDown();
                    }
                });
            }

            latchArranque2.countDown();
            assertThat(latchFinalizacion2.await(30, TimeUnit.SECONDS)).isTrue();

            // Exactamente 1 debe adquirir en la segunda ronda también
            assertThat(adquiridosRonda2.get())
                    .as("tras liberar, exactamente un hilo debe poder adquirir en la segunda ronda")
                    .isEqualTo(1);

        } finally {
            executor.shutdownNow();
            redisTemplate.delete(RedisSeatLockService.keyFor(FUNCION_ID, ASIENTO_ID));
        }
    }
}