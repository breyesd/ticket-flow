package com.ticketflow.reservation.lock;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketflow.reservation.AbstractIntegrationTest;
import com.ticketflow.reservation.domain.evento.Asiento;
import com.ticketflow.reservation.domain.evento.AsientoRepository;
import com.ticketflow.reservation.support.EmbeddedRedisExtension;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Test de integración del bloqueo pesimista
 * ({@code SELECT ... FOR UPDATE}) en {@link AsientoRepository}
 * (spec 0001, sección 3.2; plan Fase 3, F3.T2).
 *
 * <p>Arranca contra un PostgreSQL 16 real (vía
 * {@link AbstractIntegrationTest} con Testcontainers) porque el
 * bloqueo pesimista a nivel de fila con bloqueo de la segunda
 * transacción concurrente es específico de Postgres. Usa
 * {@link EmbeddedRedisExtension} para disponer de un Redis embebido
 * durante la carga del contexto.</p>
 *
 * <p>Verifica que, dentro de una transacción, {@link
 * AsientoRepository#findByIdForUpdate(Long)} adquiere el bloqueo
 * pesimista sobre la fila y que una segunda transacción concurrente
 * se bloquea hasta que la primera termina.</p>
 */
@ExtendWith(EmbeddedRedisExtension.class)
class PessimisticLockIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AsientoRepository asientoRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * Verifica que {@code findByIdForUpdate} adquiere el bloqueo
     * pesimista: una primera transacción que lee el asiento con
     * {@code FOR UPDATE} retiene el bloqueo, y una segunda transacción
     * concurrente que intenta el mismo bloqueo se bloquea hasta que la
     * primera hace commit.
     */
    @Test
    void segundaTransaccionSeBloqueaHastaCommitDeLaPrimera() throws Exception {
        Asiento asiento = asientoRepository.findByFuncionIdAndNumero(1L, 1).orElseThrow();
        Long asientoId = asiento.getId();

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        CountDownLatch primeraBloqueoAdquirido = new CountDownLatch(1);
        CountDownLatch permitirCommit = new CountDownLatch(1);
        AtomicLong duracionSegundaMs = new AtomicLong();
        AtomicReference<Throwable> error = new AtomicReference<>();

        // Pool de dos hilos (uno por transacción) para expresar la
        // concurrencia del escenario de bloqueo. Se cierra en el bloque
        // finally para no dejar hilos no-daemon colgados.
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Primera transacción: adquiere el lock pesimista y retiene la
            // transacción abierta hasta que la segunda haya intentado leer.
            Future<?> primera = executor.submit(() -> txTemplate.executeWithoutResult(status -> {
                asientoRepository.findByIdForUpdate(asientoId).orElseThrow();
                primeraBloqueoAdquirido.countDown();
                try {
                    permitirCommit.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));

            // Segunda transacción: espera a que la primera adquiera el lock
            // y mide cuánto bloquea al intentar el mismo FOR UPDATE.
            Future<?> segunda = executor.submit(() -> {
                try {
                    primeraBloqueoAdquirido.await(30, TimeUnit.SECONDS);
                    long inicio = System.nanoTime();
                    txTemplate.executeWithoutResult(status ->
                            asientoRepository.findByIdForUpdate(asientoId).orElseThrow());
                    duracionSegundaMs.set(TimeUnit.NANOSECONDS.toMillis(
                            System.nanoTime() - inicio));
                } catch (Throwable t) {
                    error.set(t);
                }
            });

            primeraBloqueoAdquirido.await(30, TimeUnit.SECONDS);

            // Espera a medir que la segunda está efectivamente bloqueada
            // (espera activa breve): la segunda no debe haber terminado
            // mientras la primera retiene el lock.
            Thread.sleep(1000);
            assertThat(segunda.isDone())
                    .as("la segunda transacción debe estar bloqueada "
                            + "mientras la primera retiene el lock")
                    .isFalse();

            // Libera la primera transacción: la segunda debe desbloquearse.
            permitirCommit.countDown();
            primera.get(30, TimeUnit.SECONDS);
            segunda.get(30, TimeUnit.SECONDS);

            assertThat(error.get()).as("la segunda transacción no debe fallar").isNull();
            assertThat(duracionSegundaMs.get())
                    .as("la segunda transacción debe haber esperado a que "
                            + "la primera hiciera commit")
                    .isGreaterThanOrEqualTo(500);
        } finally {
            executor.shutdownNow();
        }
    }
}