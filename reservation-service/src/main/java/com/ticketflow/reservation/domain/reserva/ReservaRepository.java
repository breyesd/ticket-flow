package com.ticketflow.reservation.domain.reserva;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data JPA para la entidad {@link Reserva}.
 *
 * <p>Expone las operaciones CRUD estándar heredadas de
 * {@link JpaRepository} y la consulta mínima que necesita el flujo de
 * confirmación y las verificaciones de consistencia:</p>
 * <ul>
 *   <li>{@link #findByAsientoIdOrderByFechaCreacionAsc(Long)} para
 *       localizar las reservas asociadas a un asiento concreto.</li>
 * </ul>
 */
public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    /**
     * Lista todas las reservas asociadas a un asiento, ordenadas
     * ascendentemente por fecha de creación.
     *
     * @param asientoId identificador del asiento cuyas reservas se
     *                  quieren obtener; no puede ser {@code null}.
     * @return lista de reservas del asiento, ordenada por fecha de
     *         creación (posiblemente vacía).
     */
    List<Reserva> findByAsientoIdOrderByFechaCreacionAsc(Long asientoId);
}