package com.ticketflow.reservation.domain.evento;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data JPA para la entidad {@link Asiento}.
 *
 * <p>Expone las operaciones CRUD estándar heredadas de
 * {@link JpaRepository} y las consultas derivadas mínimas que
 * necesita el flujo de selección y confirmación de compra:</p>
 * <ul>
 *   <li>{@link #findByFuncionIdOrderByNumeroAsc(Long)} para listar la
 *       disponibilidad de una función.</li>
 *   <li>{@link #findByFuncionIdAndNumero(Long, Integer)} para localizar
 *       un asiento concreto antes de aplicar el lock pesimista
 *       ({@code SELECT ... FOR UPDATE}) en fases posteriores.</li>
 * </ul>
 */
public interface AsientoRepository extends JpaRepository<Asiento, Long> {

    /**
     * Lista todos los asientos de la función indicada, ordenados
     * ascendentemente por {@code numero}.
     *
     * @param funcionId identificador de la función cuyos asientos se
     *                  quieren obtener; no puede ser {@code null}.
     * @return lista de asientos ordenada por número (posiblemente
     *         vacía).
     */
    List<Asiento> findByFuncionIdOrderByNumeroAsc(Long funcionId);

    /**
     * Busca el asiento concreto de la función con el número
     * indicado. La unicidad por {@code (funcion_id, numero)} la
     * garantiza la restricción {@code uk_asiento_funcion_numero}
     * declarada en V1, por lo que a lo sumo hay un resultado.
     *
     * @param funcionId identificador de la función en la que se busca
     *                  el asiento; no puede ser {@code null}.
     * @param numero    número del asiento dentro de la función; no
     *                  puede ser {@code null}.
     * @return un {@link Optional} con el asiento si existe, o vacío si
     *         la combinación {@code (funcionId, numero)} no se
     *         encuentra.
     */
    Optional<Asiento> findByFuncionIdAndNumero(Long funcionId, Integer numero);
}
