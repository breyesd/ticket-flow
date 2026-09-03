package com.ticketflow.reservation.domain.reserva;

import com.ticketflow.reservation.domain.evento.Asiento;
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
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Reserva resultante de la confirmación de una compra (spec 0001,
 * secciones 2.4 y 3.2; plan Fase 3, F3.T1).
 *
 * <p>Representa el resultado persistido del flujo de pago y
 * persistencia: un comprador ({@code usuarioId}) confirma un asiento
 * concreto y la {@link Reserva} queda registrada con un {@link
 * ReservaEstado} terminal ({@link ReservaEstado#PAGADA} o
 * {@link ReservaEstado#FALLIDA}) y el importe cobrado.</p>
 *
 * <p>La consistencia frente al double-booking se refuerza en la base de
 * datos con el índice único parcial {@code uk_reserva_asiento_pagada}
 * (migración V3): a lo sumo una reserva con estado {@code PAGADA} por
 * asiento.</p>
 */
@Entity
@Table(name = "reserva")
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asiento_id", nullable = false)
    private Asiento asiento;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservaEstado estado;

    // Columna marcadora (NULL cuando FALLIDA) que materializa la unicidad
    // de la reserva PAGADA por asiento sin índice único parcial (no
    // portable a H2). Ver migración V3 y {@link #reservaPagada}.
    @Column(name = "asiento_pagada_id")
    private Long asientoPagadaId;

    @Column(name = "fecha_creacion", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime fechaCreacion;

    /**
     * Constructor protegido requerido por JPA (instanciación por
     * reflexión al cargar desde base de datos; no debe usarse desde
     * código de aplicación).
     */
    protected Reserva() {
    }

    /**
     * Crea una nueva reserva en memoria lista para persistir, ligada a
     * un asiento y a un comprador, con un importe y un estado terminal.
     *
     * @param usuarioId identificador del comprador que confirmó la
     *                  reserva; debe ser positivo y no puede ser
     *                  {@code null}.
     * @param asiento   asiento sobre el que recae la reserva; no puede
     *                  ser {@code null} y debe estar persistido (o
     *                  persistirse en la misma transacción) por la
     *                  restricción de FK {@code fk_reserva_asiento}.
     * @param monto     importe cobrado por la reserva; debe ser mayor
     *                  que cero (restricción
     *                  {@code ck_reserva_monto_positivo} en V3).
     * @param estado    estado terminal de la reserva; no puede ser
     *                  {@code null}.
     */
    public Reserva(Long usuarioId, Asiento asiento, BigDecimal monto, ReservaEstado estado) {
        this.usuarioId = usuarioId;
        this.asiento = asiento;
        this.monto = monto;
        this.estado = estado;
        this.asientoPagadaId = reservaPagada(estado, asiento);
    }

    /**
     * Calcula el valor de la columna marcadora {@code asiento_pagada_id}
     * usada para reforzar la unicidad de la reserva PAGADA (migración V3).
     * Devuelve el identificador del asiento solo cuando el estado es
     * {@link ReservaEstado#PAGADA}; en caso contrario devuelve {@code null}
     * para permitir múltiples reservas {@link ReservaEstado#FALLIDA} sobre
     * el mismo asiento.
     *
     * @param estado  estado terminal de la reserva.
     * @param asiento asiento asociado a la reserva.
     * @return el identificador del asiento si {@code estado} es
     *         {@link ReservaEstado#PAGADA}, o {@code null} en caso
     *         contrario.
     */
    private static Long reservaPagada(ReservaEstado estado, Asiento asiento) {
        return estado == ReservaEstado.PAGADA ? asiento.getId() : null;
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
     * @return identificador del comprador que confirmó la reserva.
     */
    public Long getUsuarioId() {
        return usuarioId;
    }

    /**
     * @return asiento sobre el que recae la reserva. El acceso se delega
     *         al proxy lazy de JPA: la consulta se difiere hasta que se
     *         accede a algún atributo del asiento.
     */
    public Asiento getAsiento() {
        return asiento;
    }

    /**
     * @return importe cobrado por la reserva, siempre mayor que cero.
     */
    public BigDecimal getMonto() {
        return monto;
    }

    /**
     * @return estado terminal de la reserva
     *         ({@link ReservaEstado#PAGADA} o
     *         {@link ReservaEstado#FALLIDA}).
     */
    public ReservaEstado getEstado() {
        return estado;
    }

    /**
     * @return marca temporal de creación del registro tal y como la
     *         asigna la base de datos por defecto de la columna
     *         {@code fecha_creacion}.
     */
    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}