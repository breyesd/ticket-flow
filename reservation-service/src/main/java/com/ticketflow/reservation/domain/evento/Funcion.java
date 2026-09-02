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

    protected Funcion() {
    }

    public Funcion(Evento evento, OffsetDateTime fechaHora) {
        this.evento = evento;
        this.fechaHora = fechaHora;
    }

    public Long getId() {
        return id;
    }

    public Evento getEvento() {
        return evento;
    }

    public OffsetDateTime getFechaHora() {
        return fechaHora;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
