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

    protected Asiento() {
    }

    public Asiento(Funcion funcion, Integer numero) {
        this.funcion = funcion;
        this.numero = numero;
        this.estado = AsientoEstado.DISPONIBLE;
    }

    public Long getId() {
        return id;
    }

    public Funcion getFuncion() {
        return funcion;
    }

    public Integer getNumero() {
        return numero;
    }

    public AsientoEstado getEstado() {
        return estado;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
