package com.ticketflow.reservation.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuración de Spring Data Redis para {@code reservation-service}.
 *
 * <p>Expone un bean {@link RedisTemplate} parametrizado como
 * {@code <String, Object>} con:</p>
 * <ul>
 *   <li>{@link StringRedisSerializer} para claves y claves de hash:
 *       formato legible y compatible con herramientas externas
 *       ({@code redis-cli}).</li>
 *   <li>{@link GenericJackson2JsonRedisSerializer} con
 *       {@link ObjectMapper#activateDefaultTyping} para valores y
 *       valores de hash: permite serializar/deserializar POJOs
 *       arbitrarios preservando el tipo concreto en el JSON
 *       embebido, de modo que la lectura devuelve la instancia
 *       correcta (no un {@code LinkedHashMap}).</li>
 * </ul>
 *
 * <p>Este bean es la base del bloqueo distribuido de Fase 2 (ver
 * spec 0001, sección 3.1 y plan F2.T2): {@code SeatLockService}
 * construirá claves {@code lock:funcion:{funcionId}:asiento:{asientoId}}
 * y almacenará valores tipados (token de propietario del lock).</p>
 *
 * <p>La activación de tipado por defecto se restringe con un
 * {@link BasicPolymorphicTypeValidator} que limita la deserialización
 * polimórfica a clases del classpath propio y de
 * {@code java.util}/{@code java.lang}, mitigando riesgos de
 * deserialización insegura (ver AGENTS.md §6 — controles de seguridad
 * con SpotBugs + Find Security Bugs).</p>
 */
@Configuration
@Profile("!test")
public class RedisConfig {

    /**
     * Construye un {@link RedisTemplate} parametrizado
     * {@code <String, Object>} listo para inyección por constructor.
     *
     * <p>El {@link RedisConnectionFactory} lo autoconfigura Spring
     * Boot a partir de las propiedades {@code spring.data.redis.host}
     * y {@code spring.data.redis.port} (con defaults
     * {@code localhost:6379} definidos en {@code application.yml}).</p>
     *
     * @param connectionFactory fábrica de conexiones a Redis
     *                          autoconfigurada por Spring Boot; no
     *                          puede ser {@code null}.
     * @return plantilla de Redis lista para usar; nunca {@code null}.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.EVERYTHING);
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
