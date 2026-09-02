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

    protected Evento() {
    }

    public Evento(String nombre, String descripcion) {
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
