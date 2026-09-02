package com.ticketflow.reservation.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketflow.reservation.support.EmbeddedRedisExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test de integración del bean {@link RedisConfig#redisTemplate} que
 * verifica que la plantilla está configurada con
 * {@code StringRedisSerializer} para claves y
 * {@code GenericJackson2JsonRedisSerializer} con
 * {@code activateDefaultTyping} para valores.
 *
 * <p>Como el entorno no tiene Docker, este test arranca un servidor
 * Redis embebido (vía la dependencia
 * {@code com.github.codemonstur:embedded-redis}) mediante
 * {@link EmbeddedRedisExtension}, que publica
 * {@code spring.data.redis.host}/{@code spring.data.redis.port} en las
 * propiedades del sistema antes de cargar el contexto. El test escribe
 * una clave {@code "test:config"} con un POJO simple (record con un
 * único campo) y afirma que la lectura devuelve un valor equivalente
 * tipado a su clase original — comportamiento que solo es posible si
 * el serializador JSON está activo y la deserialización polimórfica
 * funciona (es decir, {@code activateDefaultTyping} está
 * activado).</p>
 *
 * <p>Usa el perfil estándar {@code test} (sin perfil dedicado): la
 * extensión provee el Redis embebido y el contexto se carga con la
 * configuración de producción más el wiring específico de tests, lo
 * que permite que {@link RedisConfig} no necesite filtros por
 * perfil.</p>
 */
@ExtendWith(EmbeddedRedisExtension.class)
@ActiveProfiles("test")
@SpringBootTest
class RedisConfigTest {

    /**
     * POJO de prueba usado por
     * {@link #writesAndReadsTypedJsonValue}. Es un record mínimo
     * deliberadamente {@code final} (todos los records lo son) para
     * forzar al serializador JSON a verificar que el tipado
     * polimórfico funciona con clases finales — escenario cubierto por
     * {@code DefaultTyping.EVERYTHING} en {@link RedisConfig}. No
     * implementa {@link java.io.Serializable} porque el serializador
     * usado es Jackson (JSON), que no requiere esa interfaz: basta con
     * que el tipo sea accesible al {@code ObjectMapper}.
     */
    public record SamplePojo(String value) {
    }

    /**
     * Bean inyectado por Spring construido por
     * {@link RedisConfig#redisTemplate}. El test escribe y lee a
     * través de él para verificar la configuración del serializador.
     */
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

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
