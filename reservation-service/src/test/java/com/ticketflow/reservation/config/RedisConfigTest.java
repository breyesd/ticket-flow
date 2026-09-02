package com.ticketflow.reservation.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Serializable;
import java.net.ServerSocket;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import redis.embedded.RedisServer;

/**
 * Test de integración del bean {@link RedisConfig#redisTemplate} que
 * verifica que la plantilla está configurada con
 * {@code StringRedisSerializer} para claves y
 * {@code GenericJackson2JsonRedisSerializer} con
 * {@code activateDefaultTyping} para valores.
 *
 * <p>Como el entorno no tiene Docker, este test arranca un servidor
 * Redis embebido (vía la dependencia
 * {@code com.github.codemonstur:embedded-redis}) y lo expone a Spring
 * mediante {@link DynamicPropertySource}. El test escribe una clave
 * {@code "test:config"} con un POJO simple (record con un único
 * campo) y afirma que la lectura devuelve un valor equivalente tipado
 * a su clase original — comportamiento que solo es posible si el
 * serializador JSON está activo y la deserialización polimórfica
 * funciona (es decir, {@code activateDefaultTyping} está
 * activado).</p>
 *
 * <p>Usa un perfil dedicado {@code redis-test} (en lugar del
 * {@code test} general) para no contaminar el resto de tests con la
 * necesidad de un Redis embebido: el perfil {@code test} excluye
 * deliberadamente {@code RedisAutoConfiguration} (ver
 * {@code application-test.yml}).</p>
 */
@ActiveProfiles("redis-test")
@SpringBootTest
class RedisConfigTest {

    /**
     * POJO de prueba usado por
     * {@link #writesAndReadsTypedJsonValue}. Es un
     * {@link Serializable} para que Jackson pueda manejarlo sin
     * configuración adicional y se incluye el
     * {@code serialVersionUID} como buena práctica de serialización.
     */
    public record SamplePojo(String value) implements Serializable {

        private static final long serialVersionUID = 1L;
    }

    /**
     * Bean inyectado por Spring construido por
     * {@link RedisConfig#redisTemplate}. El test escribe y lee a
     * través de él para verificar la configuración del serializador.
     */
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Servidor Redis embebido compartido por los tests de esta clase.
     * Arranca una vez antes de todos los tests y se detiene al
     * finalizar, reusándose entre métodos para reducir el tiempo
     * total. Arranca en un puerto libre elegido mediante
     * {@link #findFreePort()} (la librería no soporta {@code port 0}).
     */
    private static RedisServer embeddedRedis;

    /**
     * Puerto concreto en el que escucha el servidor Redis embebido.
     * Se publica a Spring en {@link #configureRedisPort}.
     */
    private static int embeddedRedisPort;

    /**
     * Arranca el servidor Redis embebido en un puerto libre
     * descubierto con {@link #findFreePort()}.
     *
     * @throws IOException si no se puede descubrir un puerto libre
     *                     o si el binario de Redis no puede iniciarse
     *                     (típicamente por permisos o por falta del
     *                     ejecutable empaquetado para la plataforma).
     */
    @BeforeAll
    static void startEmbeddedRedis() throws IOException {
        embeddedRedisPort = findFreePort();
        embeddedRedis = new RedisServer(embeddedRedisPort);
        embeddedRedis.start();
    }

    /**
     * Detiene el servidor Redis embebido liberando el puerto y los
     * recursos asociados. Idempotente: si el servidor ya fue detenido
     * no falla.
     *
     * @throws IOException si la señal de parada no puede enviarse al
     *                     proceso Redis.
     */
    @AfterAll
    static void stopEmbeddedRedis() throws IOException {
        if (embeddedRedis != null) {
            embeddedRedis.stop();
            embeddedRedis = null;
        }
    }

    /**
     * Publica en el contexto de Spring el puerto del servidor Redis
     * embebido arrancado en {@link #startEmbeddedRedis}, de modo que
     * el cliente Lettuce/Jedis configurado por Spring Boot apunte al
     * puerto dinámico elegido (evita colisiones con otros tests).
     *
     * @param registry registro dinámico de propiedades de Spring al
     *                 que se añade {@code spring.data.redis.port}.
     */
    @DynamicPropertySource
    static void configureRedisPort(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.port", () -> embeddedRedisPort);
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

    /**
     * Verifica el criterio de aceptación del plan F2.T1: el
     * {@link RedisTemplate} configurado escribe un POJO bajo una
     * clave y lo recupera con su tipo original, lo que demuestra que
     * el serializador JSON está activo y que la deserialización
     * polimórfica ({@code activateDefaultTyping}) funciona.
     *
     * <p>Si {@link RedisConfig} no existiera, Spring sólo configuraría
     * un {@code StringRedisTemplate} (no un
     * {@code RedisTemplate<String,Object>}), por lo que la inyección
     * fallaría al cargar el contexto. Si existiera pero usara un
     * {@code StringRedisSerializer} para los valores, la lectura
     * devolvería una {@code String} (no un {@code SamplePojo}) y la
     * igualdad fallaría.</p>
     */
    @Test
    void writesAndReadsTypedJsonValue() {
        SamplePojo original = new SamplePojo("hola-redis");

        redisTemplate.opsForValue().set("test:config", original);

        Object roundTripped = redisTemplate.opsForValue().get("test:config");
        assertThat(roundTripped)
                .as("El valor recuperado debe ser un SamplePojo, no una cadena")
                .isInstanceOf(SamplePojo.class);
        assertThat(((SamplePojo) roundTripped).value())
                .as("El campo del POJO debe sobrevivir al ciclo de serialización")
                .isEqualTo("hola-redis");
    }
}
