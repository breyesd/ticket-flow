package com.ticketflow.reservation.domain.evento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Función o fecha concreta de un {@link Evento}. Un evento puede tener
 * varias funciones; cada función contiene sus propios
 * {@link Asiento asientos}.
 *
 * <p>Nivel intermedio de la jerarquía
 * {@code Evento (1) ─< Funcion (n) ─< Asiento (n)} (spec 0001, sección
 * 2.3).</p>
 */
@Entity
@Table(name = "funcion")
public class Funcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    @Column(name = "fecha_hora", nullable = false)
    private OffsetDateTime fechaHora;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    /**
     * Constructor protegido requerido por JPA (instanciación por
     * reflexión al cargar desde base de datos; no debe usarse desde
     * código de aplicación).
     */
    protected Funcion() {
    }

    /**
     * Crea una nueva función ligada a un evento existente y lista para
     * persistir.
     *
     * @param evento   evento al que pertenece esta función; no puede
     *                 ser {@code null} y debe estar persistido (o
     *                 persistirse en la misma transacción) por la
     *                 restricción de FK {@code fk_funcion_evento}.
     * @param fechaHora fecha y hora concretas (con zona horaria) en que
     *                 se celebra la función; no puede ser {@code null}.
     */
    public Funcion(Evento evento, OffsetDateTime fechaHora) {
        this.evento = evento;
        this.fechaHora = fechaHora;
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
     * @return evento al que pertenece esta función. El acceso se delega
     *         al proxy lazy de JPA: la consulta a la base de datos se
     *         difiere hasta que se accede a algún atributo del evento.
     */
    public Evento getEvento() {
        return evento;
    }

    /**
     * @return fecha y hora concretas (con zona horaria) en que se
     *         celebra la función.
     */
    public OffsetDateTime getFechaHora() {
        return fechaHora;
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
