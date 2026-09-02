package com.ticketflow.reservation.domain.evento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Evento o espectáculo raíz del modelo de dominio. Agrupa una o más
 * {@link Funcion funciones} y, a través de ellas, sus {@link Asiento
 * asientos}.
 *
 * <p>Raíz de la jerarquía
 * {@code Evento (1) ─< Funcion (n) ─< Asiento (n)} definida en la spec
 * 0001, sección 2.3.</p>
 */
@Entity
@Table(name = "evento")
public class Evento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(length = 1000)
    private String descripcion;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    /**
     * Constructor protegido requerido por JPA (no debe usarse desde
     * código de aplicación: la instancia se materializa mediante
     * reflexión al cargarse desde la base de datos).
     */
    protected Evento() {
    }

    /**
     * Crea un nuevo evento en memoria listo para ser persistido.
     *
     * @param nombre      nombre público del evento mostrado en la UI;
     *                    obligatorio y con un máximo de 200 caracteres.
     * @param descripcion descripción opcional y libre del evento;
     *                    máximo 1000 caracteres.
     */
    public Evento(String nombre, String descripcion) {
        this.nombre = nombre;
        this.descripcion = descripcion;
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
     * @return nombre del evento tal como se expone a usuarios finales.
     */
    public String getNombre() {
        return nombre;
    }

    /**
     * @return descripción opcional del evento, o {@code null} si no se
     *         proporcionó al crear la entidad.
     */
    public String getDescripcion() {
        return descripcion;
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
