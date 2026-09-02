package com.ticketflow.reservation.support;

import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import redis.embedded.RedisServer;

/**
 * Extensión de JUnit 5 que arranca una instancia única de Redis
 * embebido (vía {@code com.github.codemonstur:embedded-redis}) para
 * toda la JVM de tests y la detiene cuando la JVM finaliza.
 *
 * <p>El puerto se elige dinámicamente con {@link #findFreePort()} (la
 * librería no soporta {@code port 0}) y se publica a Spring como
 * propiedad del sistema {@code spring.data.redis.port} antes de que
 * se cargue el contexto, de modo que el cliente Lettuce configurado
 * por Spring Boot apunte al puerto dinámico elegido y no a un puerto
 * fijo que pueda colisionar con otros procesos.</p>
 *
 * <p>El servidor Redis se mantiene vivo entre clases de test dentro
 * de la misma JVM de Surefire: arrancarlo y detenerlo por clase
 * rompe el caché de contextos de Spring, que reutiliza el contexto
 * entre clases con la misma configuración activa pero apuntaría a un
 * puerto cuyo Redis ya fue detenido. La parada se hace en un
 * shutdown hook registrado en {@link #beforeAll}.</p>
 *
 * <p>Esta extensión sustituye al perfil dedicado {@code redis-test}
 * que existía en F2.T1 (revisión 0): permite que el bean
 * {@code RedisTemplate<String,Object>} declarado por
 * {@code RedisConfig} esté disponible bajo el perfil estándar
 * {@code test}, sin filtros por perfil en la clase de producción,
 * cumpliendo el principio de que el wiring de producción sea válido
 * también en tests.</p>
 *
 * <p>Uso típico:</p>
 * <pre>{@code
 * @ExtendWith(EmbeddedRedisExtension.class)
 * @ActiveProfiles("test")
 * @SpringBootTest
 * class MiTest { ... }
 * }</pre>
 */
public class EmbeddedRedisExtension implements BeforeAllCallback, AfterAllCallback {

    /**
     * Nombre de la propiedad del sistema bajo la que se publica el
     * puerto dinámico del servidor Redis embebido. Spring Boot la
     * lee al construir el {@code RedisConnectionFactory}.
     */
    public static final String REDIS_PORT_PROPERTY = "spring.data.redis.port";

    /**
     * Host al que apunta el cliente Lettuce/Jedis. Fijo a
     * {@code localhost} porque la extensión arranca el Redis en el
     * mismo proceso.
     */
    public static final String REDIS_HOST_PROPERTY = "spring.data.redis.host";

    /**
     * Instancia compartida por toda la JVM de tests; se inicializa
     * perezosamente en {@link #beforeAll} y se libera cuando la JVM
     * finaliza (shutdown hook). Estática para sobrevivir entre
     * clases de test y mantener estable el puerto que Spring cachea
     * en su contexto.
     */
    private static RedisServer embeddedRedis;

    /**
     * Puerto concreto en el que escucha el servidor Redis embebido.
     */
    private static int embeddedRedisPort;

    /**
     * Shutdown hook registrado para detener el Redis embebido cuando
     * la JVM finaliza. Se registra una sola vez por JVM.
     */
    private static Thread shutdownHook;

    /**
     * Arranca el servidor Redis embebido en un puerto TCP libre
     * descubierto con {@link #findFreePort()} y publica
     * {@code spring.data.redis.host}/{@code spring.data.redis.port}
     * en las propiedades del sistema para que Spring Boot las lea al
     * construir el {@code RedisConnectionFactory}.
     *
     * <p>Si ya hay un Redis embebido activo en esta JVM (caso normal
     * cuando varias clases de test comparten la JVM de Surefire), se
     * reutiliza sin reiniciar; las propiedades del sistema se
     * reescriben siempre con el puerto actual (idempotente).</p>
     *
     * @param context contexto de la extensión; no se utiliza
     *                directamente pero lo requiere la API de JUnit 5.
     * @throws IOException si no se puede descubrir un puerto libre
     *                     o si el binario de Redis no puede iniciarse
     *                     (típicamente por permisos o por falta del
     *                     ejecutable empaquetado para la plataforma).
     */
    @Override
    public void beforeAll(ExtensionContext context) throws IOException {
        synchronized (EmbeddedRedisExtension.class) {
            if (embeddedRedis == null) {
                embeddedRedisPort = findFreePort();
                embeddedRedis = new RedisServer(embeddedRedisPort);
                embeddedRedis.start();
                if (shutdownHook == null) {
                    shutdownHook = new Thread(EmbeddedRedisExtension::stopEmbeddedRedis,
                            "embedded-redis-shutdown");
                    Runtime.getRuntime().addShutdownHook(shutdownHook);
                }
            }
        }
        System.setProperty(REDIS_HOST_PROPERTY, "localhost");
        System.setProperty(REDIS_PORT_PROPERTY, Integer.toString(embeddedRedisPort));
    }

    /**
     * No realiza ninguna acción: el servidor Redis embebido se
     * mantiene vivo entre clases de test dentro de la JVM de
     * Surefire para no invalidar el caché de contextos de Spring (el
     * puerto se cachea en el contexto y debe permanecer estable
     * durante toda la JVM). La parada efectiva ocurre en el shutdown
     * hook registrado en {@link #beforeAll}.
     *
     * @param context contexto de la extensión; no se utiliza
     *                directamente pero lo requiere la API de JUnit 5.
     */
    @Override
    public void afterAll(ExtensionContext context) {
    }

    /**
     * Detiene el servidor Redis embebido como parte del shutdown
     * hook de la JVM. Idempotente: si ya está nulo no falla.
     */
    private static void stopEmbeddedRedis() {
        if (embeddedRedis != null) {
            try {
                embeddedRedis.stop();
            } catch (IOException e) {
                // El shutdown hook no puede propagar excepciones;
                // se ignoran porque la JVM está terminando.
            }
            embeddedRedis = null;
        }
    }

    /**
     * Pide al sistema operativo un puerto TCP libre, lo cierra y lo
     * devuelve. Existe una pequeña ventana de carrera entre el cierre
     * y el bind del servidor Redis, pero es aceptable en tests
     * (los reintentos naturales del SO la cubren) y evita fijar un
     * puerto que choque con otros procesos.
     *
     * @return un puerto TCP libre en el momento de la invocación.
     * @throws IOException si no se puede abrir un socket para
     *                     preguntar al SO.
     */
    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
