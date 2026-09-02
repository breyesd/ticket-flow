package com.ticketflow.reservation.lock;

/**
 * Contrato del servicio de bloqueo distribuido por asiento de
 * TicketFlow (fase 2, spec 0001 §3.1).
 *
 * <p>El servicio implementa una fase de selección de asientos en la
 * que dos clientes que intenten comprar el mismo asiento no pueden
 * adquirirlo a la vez: el segundo intento recibe un resultado de
 * "ya bloqueado" y debe ser rechazado por la capa superior con
 * HTTP 409 Conflict.</p>
 *
 * <p>El bloqueo tiene tres propiedades:</p>
 * <ul>
 *   <li><strong>Exclusión mutua:</strong> una segunda adquisición
 *       sobre la misma clave falla inmediatamente.</li>
 *   <li><strong>Expiración:</strong> el lock tiene un TTL
 *       configurable; si el dueño no lo libera a tiempo, Redis lo
 *       expira y el siguiente intento tendrá éxito.</li>
 *   <li><strong>Liberación por propietario:</strong> solo el cliente
 *       que posee el token recibido en la adquisición puede liberar
 *       el lock. Se implementa con un script Lua atómico
 *       ({@code GET} + comparación + {@code DEL}) para evitar que un
 *       cliente cuyo lock ya expiró pueda borrar un lock recién
 *       tomado por otro cliente.</li>
 * </ul>
 *
 * <p>La clave Redis asociada al lock de un asiento sigue el formato
 * {@code lock:funcion:{funcionId}:asiento:{asientoId}} definido por la
 * spec 0001 §3.1 y el plan de fase 2.</p>
 */
public interface SeatLockService {

    /**
     * Resultado de intentar adquirir un lock sobre un asiento.
     *
     * <p>Se modela como jerarquía sellada (Java 21) para que el
     * compilador fuerce el manejo de ambos casos y para hacer
     * explícito el contrato a los consumidores (F2.T3 y F2.T4).</p>
     */
    sealed interface AcquireResult {

        /**
         * Adquisición exitosa: el lock fue creado y el llamante es
         * su propietario durante el TTL indicado. El {@code token}
         * es el valor opaco que debe pasarse a
         * {@link SeatLockService#releaseLock} para liberar.
         */
        record Acquired(String token) implements AcquireResult {
        }

        /**
         * Adquisición fallida: ya existe un lock activo sobre el
         * asiento solicitado. El llamante NO debe intentar liberar
         * la clave ni tratarla como propia; corresponde a otro
         * cliente o a un lock huérfano a punto de expirar.
         */
        record AlreadyLocked() implements AcquireResult {
        }
    }

    /**
     * Intenta adquirir el lock distribuido sobre el asiento
     * {@code asientoId} de la función {@code funcionId} durante
     * {@code ttlMs} milisegundos.
     *
     * <p>La operación es atómica: equivale a
     * {@code SET lock:funcion:{funcionId}:asiento:{asientoId} <token>
     * NX PX <ttlMs>} en Redis, de modo que dos adquisiciones
     * concurrentes nunca pueden crear dos locks sobre la misma
     * clave. Si la clave ya existe (otro cliente la retuvo o aún no
     * expiró), se devuelve {@link AcquireResult.AlreadyLocked} sin
     * sobrescribir el valor existente.</p>
     *
     * @param funcionId identificador de la función a la que pertenece
     *                  el asiento; no puede ser {@code null}.
     * @param asientoId identificador del asiento a bloquear dentro de
     *                  la función; no puede ser {@code null}.
     * @param ttlMs     tiempo de vida del lock en milisegundos; debe
     *                  ser positivo. La spec 0001 §3.1 fija un TTL
     *                  por defecto de 5 minutos (300000 ms) para la
     *                  operación de selección.
     * @return {@link AcquireResult.Acquired} con un token UUID v4
     *         opaco si la adquisición tuvo éxito, o
     *         {@link AcquireResult.AlreadyLocked} si ya existía un
     *         lock activo.
     */
    AcquireResult acquireLock(Long funcionId, Long asientoId, long ttlMs);

    /**
     * Intenta liberar el lock distribuido sobre el asiento
     * {@code asientoId} de la función {@code funcionId}.
     *
     * <p>La liberación solo tiene éxito si el {@code token}
     * facilitado coincide con el almacenado en Redis: cualquier
     * cliente cuyo lock haya expirado y haya sido sustituido por
     * otro no podrá borrar el lock ajeno. La operación es atómica
     * (script Lua) y devuelve {@code false} si no había lock o si
     * el token no coincidía.</p>
     *
     * <p>Si {@code token} es {@code null} o está en blanco, el
     * método devuelve {@code false} sin tocar Redis (defensa frente
     * a NPE silencioso y a borrar un lock por error).</p>
     *
     * @param funcionId identificador de la función; no puede ser
     *                  {@code null}.
     * @param asientoId identificador del asiento; no puede ser
     *                  {@code null}.
     * @param token     token de propietario devuelto por
     *                  {@link #acquireLock}; puede ser {@code null}
     *                  o vacío, en cuyo caso la operación es un no-op
     *                  que devuelve {@code false}.
     * @return {@code true} si se liberó el lock (token coincidente);
     *         {@code false} si no había lock, si el token no
     *         coincidía, o si {@code token} era {@code null}/vacío.
     */
    boolean releaseLock(Long funcionId, Long asientoId, String token);

    /**
     * Indica si el asiento indicado está actualmente bloqueado en
     * Redis (con cualquier token y TTL restante, incluido un TTL
     * cercano a expirar).
     *
     * <p>Útil para mostrar el estado "lockeado por otro cliente" en
     * listados de disponibilidad. No es atómico respecto a
     * {@link #acquireLock} por construcción: entre la consulta y una
     * adquisición pueden pasar cosas. La capa de adquisición sigue
     * siendo la fuente de verdad.</p>
     *
     * @param funcionId identificador de la función; no puede ser
     *                  {@code null}.
     * @param asientoId identificador del asiento; no puede ser
     *                  {@code null}.
     * @return {@code true} si existe la clave de lock en Redis,
     *         {@code false} en caso contrario.
     */
    boolean isLocked(Long funcionId, Long asientoId);
}
