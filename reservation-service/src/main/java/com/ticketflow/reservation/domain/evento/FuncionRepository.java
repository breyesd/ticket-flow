package com.ticketflow.reservation.domain.evento;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data JPA para la entidad {@link Funcion}.
 *
 * <p>Expone las operaciones CRUD estándar heredadas de
 * {@link JpaRepository} más las consultas derivadas necesarias para
 * soportar la navegación del catálogo y el endpoint de disponibilidad
 * de la fase 1.</p>
 */
public interface FuncionRepository extends JpaRepository<Funcion, Long> {

    /**
     * Lista todas las funciones asociadas al evento indicado, en el
     * orden por defecto de la base de datos.
     *
     * @param eventoId identificador del evento cuyas funciones se
     *                 quieren obtener; no puede ser {@code null}.
     * @return lista de funciones del evento (posiblemente vacía si el
     *         evento no tiene funciones o no existe).
     */
    List<Funcion> findByEventoId(Long eventoId);
}
