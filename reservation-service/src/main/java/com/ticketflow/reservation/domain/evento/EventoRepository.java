package com.ticketflow.reservation.domain.evento;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data JPA para la entidad {@link Evento}.
 *
 * <p>Expone las operaciones CRUD estándar heredadas de
 * {@link JpaRepository} y queda como punto de extensión para futuras
 * consultas específicas del dominio de eventos.</p>
 */
public interface EventoRepository extends JpaRepository<Evento, Long> {
}
