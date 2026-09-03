package com.ticketflow.reservation.domain.evento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Asiento concreto dentro de una {@link Funcion}. Cada asiento pertenece a
 * una única función y se identifica por su {@code numero}, que debe ser
 * único dentro de la función (restricción {@code uk_asiento_funcion_numero}
 * en V1).
 *
 * <p>Nivel hoja de la jerarquía
 * {@code Evento (1) ─< Funcion (n) ─< Asiento (n)} (spec 0001, sección
 * 2.3). El {@link AsientoEstado estado} por defecto al crear un asiento es
 * {@link AsientoEstado#DISPONIBLE}; el bloqueo durante la selección
 * <strong>no</strong> se persiste, solo vive en Redis (spec 0001, sección
 * 3.1).</p>
 */
@Entity
@Table(name = "asiento")
public class Asiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "funcion_id", nullable = false)
    private Funcion funcion;

    @Column(nullable = false)
    private Integer numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AsientoEstado estado;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    /**
     * Constructor protegido requerido por JPA (instanciación por
     * reflexión al cargar desde base de datos; no debe usarse desde
     * código de aplicación).
     */
    protected Asiento() {
    }

    /**
     * Crea un nuevo asiento ligado a una función existente y listo para
     * persistir. El estado se inicializa a
     * {@link AsientoEstado#DISPONIBLE} como semántica de creación (ver
     * spec 0001, sección 2.4): un asiento recién dado de alta siempre
     * está libre.
     *
     * @param funcion función a la que pertenece este asiento; no puede
     *                ser {@code null} y debe estar persistida (o
     *                persistirse en la misma transacción) por la
     *                restricción de FK {@code fk_asiento_funcion}.
     * @param numero  número del asiento dentro de la función; debe ser
     *                único por función (restricción
     *                {@code uk_asiento_funcion_numero} en V1).
     */
    public Asiento(Funcion funcion, Integer numero) {
        this.funcion = funcion;
        this.numero = numero;
        this.estado = AsientoEstado.DISPONIBLE;
    }

    /**
     * @return identificador surrogate generado por la base de datos
     *         (clave primaria). Será {@code null} hasta que la entidad
     *         haya sido persistida.
     */
    public Long getId() {
        return id;
    }

    /**
     * @return función a la que pertenece este asiento. El acceso se
     *         delega al proxy lazy de JPA: la consulta a la base de
     *         datos se difiere hasta que se accede a algún atributo de
     *         la función.
     */
    public Funcion getFuncion() {
        return funcion;
    }

    /**
     * @return número del asiento dentro de la función; único por
     *         función.
     */
    public Integer getNumero() {
        return numero;
    }

    /**
     * @return estado actual del asiento en la base de datos. El
     *         bloqueo durante la selección <strong>no</strong> se
     *         persiste: solo viven en Redis como claves con TTL (ver
     *         spec 0001, sección 3.1).
     */
    public AsientoEstado getEstado() {
        return estado;
    }

    /**
     * Transiciona el asiento a {@link AsientoEstado#VENDIDO} como
     * parte de una confirmación de compra exitosa (spec 0001, sección
     * 3.2).
     *
     * <p>La transición es unidireccional: una vez vendido, el asiento
     * no vuelve a {@link AsientoEstado#DISPONIBLE}. Se invoca
     * únicamente desde el flujo de confirmación, dentro de una
     * transacción que ha validado previamente el estado
     * {@link AsientoEstado#DISPONIBLE} bajo bloqueo pesimista.</p>
     */
    public void vender() {
        this.estado = AsientoEstado.VENDIDO;
    }

    /**
     * @return marca temporal de creación del registro tal y como la
     *         asigna la base de datos por defecto de la columna
     *         {@code created_at}.
     */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
