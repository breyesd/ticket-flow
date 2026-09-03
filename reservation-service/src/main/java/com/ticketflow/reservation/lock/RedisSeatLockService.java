package com.ticketflow.reservation.lock;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * Implementación basada en Redis de {@link SeatLockService} (spec 0001
 * §3.1, plan fase 2).
 *
 * <p>Estrategia de atomicidad:</p>
 * <ul>
 *   <li>{@link #acquireLock} delega en
 *       {@link RedisTemplate#opsForValue()#setIfAbsent(Object, Object,
 *       Duration)}, que Spring Data Redis traduce a
 *       {@code SET key value NX PX <ttlMs>} — un solo comando
 *       atómico que nunca sobrescribe un lock existente.</li>
 *   <li>{@link #releaseLock} ejecuta un script Lua
 *       ({@code GET} + comparación de token + {@code DEL}) que Redis
 *       procesa de forma atómica. Un {@code DEL} directo sería
 *       vulnerable a una carrera: si nuestro lock expiró y otro
 *       cliente lo adquirió, un {@code DEL} tardío borraría el lock
 *       ajeno.</li>
 *   <li>{@link #isLocked} usa {@code EXISTS} internamente (vía
 *       {@link RedisTemplate#hasKey(Object)}).</li>
 * </ul>
 *
 * <p>El formato de la clave sigue la spec 0001 §3.1:
 * {@code lock:funcion:{funcionId}:asiento:{asientoId}}. Las constantes
 * {@link #KEY_PREFIX} y {@link #KEY_SUFFIX} son detalles de
 * implementación privados: el contrato público no expone el esquema
 * de claves.</p>
 *
 * <p>El token de propietario es un UUID v4 generado en cada
 * adquisición, lo que hace prácticamente imposible que dos clientes
 * generen el mismo token por casualidad y libera a la operación
 * {@code releaseLock} de tener que verificar contra una lista de
 * tokens activos.</p>
 */
@Service
public class RedisSeatLockService implements SeatLockService {

    /**
     * Prefijo común de las claves de lock en Redis. Junto con
     * {@link #KEY_SUFFIX} y los identificadores de función y asiento
     * forma la clave completa. Es privado: el contrato público no
     * depende del esquema exacto.
     */
    private static final String KEY_PREFIX = "lock:funcion:";

    /**
     * Segmento intermedio entre el identificador de función y el de
     * asiento. Se mantiene como constante separada para evitar errores
     * de concatenación y dejar claro el formato en el código.
     */
    private static final String KEY_SUFFIX = ":asiento:";

    /**
     * Script Lua que libera un lock solo si el token facilitado
     * coincide con el almacenado. La comparación y el borrado ocurren
     * dentro del mismo script, de modo que Redis los ejecuta como
     * una operación atómica (no hay ventana de carrera entre el
     * {@code GET} y el {@code DEL}). Devuelve {@code 1} si se borró,
     * {@code 0} en caso contrario.
     */
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] "
                    + "then return redis.call('DEL', KEYS[1]) "
                    + "else return 0 end",
            Long.class);

    /**
     * Script Lua que verifica la propiedad del lock sin liberarlo.
     * Compara el token facilitado con el almacenado y devuelve {@code 1}
     * si coinciden, {@code 0} en caso contrario. Al no incluir
     * {@code DEL}, es estrictamente no destructivo y se usa en
     * {@link #isOwner} para decidir si el llamante sigue siendo dueño
     * del lock antes de confirmar una compra.
     */
    private static final DefaultRedisScript<Long> IS_OWNER_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] "
                    + "then return 1 else return 0 end",
            Long.class);

    /**
     * Plantilla de Redis inyectada por Spring, configurada en
     * {@code RedisConfig} con
     * {@code StringRedisSerializer}/{@code GenericJackson2JsonRedisSerializer}.
     * Se usa directamente para el script Lua porque el serializador
     * aplicado a {@code ARGV[1]} (el token) debe ser consistente con
     * el que se usó al hacer {@code SET} en la adquisición — ver
     * nota sobre tokens en {@link #acquireLock}.
     */
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Constructor con inyección por constructor (estilo recomendado
     * sobre {@code @Autowired} en campo).
     *
     * @param redisTemplate plantilla de Redis compartida con el resto
     *                      del servicio; no puede ser {@code null}.
     */
    public RedisSeatLockService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Detalles de implementación:</p>
     * <ul>
     *   <li>El token es un {@link UUID#randomUUID()} (v4), generado
     *       por el servidor de aplicación, no por Redis.</li>
     *   <li>{@code setIfAbsent} con {@link Duration} se traduce a
     *       {@code SET key value NX PX ttlMs}; si la clave ya
     *       existía, Redis devuelve {@code nil} y
     *       {@code setIfAbsent} retorna {@code Boolean.FALSE} (o
     *       {@code null} en algunos casos, que se trata igual).</li>
     * </ul>
     */
    @Override
    public AcquireResult acquireLock(Long funcionId, Long asientoId, long ttlMs) {
        String key = keyFor(funcionId, asientoId);
        String token = UUID.randomUUID().toString();
        Boolean ok = redisTemplate.opsForValue()
                .setIfAbsent(key, token, Duration.ofMillis(ttlMs));
        if (Boolean.TRUE.equals(ok)) {
            return new AcquireResult.Acquired(token);
        }
        return new AcquireResult.AlreadyLocked();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Detalles de implementación:</p>
     * <ul>
     *   <li>Si el token es {@code null} o está en blanco, se
     *       devuelve {@code false} sin tocar Redis. Esto evita
     *       tanto un {@code NPE} al pasar el token al script como
     *       borrar accidentalmente un lock por una entrada de API
     *       inválida.</li>
     *   <li>El script Lua
     *       {@code if GET(KEYS[1]) == ARGV[1] then DEL(KEYS[1]) else 0}
     *       garantiza que un lock cuyo TTL expiró (y fue tomado por
     *       otro cliente) no pueda ser borrado por el dueño
     *       original: el {@code GET} devolverá el nuevo token y la
     *       comparación fallará.</li>
     * </ul>
     */
    @Override
    public boolean releaseLock(Long funcionId, Long asientoId, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String key = keyFor(funcionId, asientoId);
        Long result = redisTemplate.execute(RELEASE_SCRIPT, List.of(key), token);
        return result != null && result == 1L;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isLocked(Long funcionId, Long asientoId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(keyFor(funcionId, asientoId)));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Detalles de implementación:</p>
     * <ul>
     *   <li>Si el token es {@code null} o está en blanco, devuelve
     *       {@code false} sin tocar Redis (defensa frente a NPE y a
     *       considerar a un llamante sin token como propietario).</li>
     *   <li>La comparación se ejecuta en un script Lua
     *       ({@code GET} + comparación), atómico en Redis, de modo que
     *       no existe ventana de carrera entre la lectura del token y
     *       la decisión de propiedad.</li>
     * </ul>
     */
    @Override
    public boolean isOwner(Long funcionId, Long asientoId, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String key = keyFor(funcionId, asientoId);
        Long result = redisTemplate.execute(IS_OWNER_SCRIPT, List.of(key), token);
        return result != null && result == 1L;
    }

    /**
     * Construye la clave Redis que representa el lock del asiento
     * {@code asientoId} dentro de la función {@code funcionId}.
     *
     * <p>Es de paquete (no privada) para que los tests de
     * integración puedan utilizarla como fuente única de verdad del
     * formato de clave; así, un cambio futuro en el esquema (p. ej.
     * un hash tag para Redis Cluster) se aplica en un solo lugar y
     * los helpers de limpieza en los tests lo reflejan
     * automáticamente.</p>
     *
     * @param funcionId identificador de la función; se incorpora tal
     *                  cual al sufijo de la clave.
     * @param asientoId identificador del asiento; se incorpora tal
     *                  cual al sufijo de la clave.
     * @return clave completa del lock, con el formato definido por la
     *         spec 0001 §3.1.
     */
    static String keyFor(Long funcionId, Long asientoId) {
        return KEY_PREFIX + funcionId + KEY_SUFFIX + asientoId;
    }
}
