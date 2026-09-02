package com.ticketflow.reservation.api.asiento;

import com.ticketflow.reservation.domain.evento.Asiento;
import com.ticketflow.reservation.domain.evento.AsientoRepository;
import com.ticketflow.reservation.domain.evento.EventoNotFoundException;
import com.ticketflow.reservation.domain.evento.EventoRepository;
import com.ticketflow.reservation.domain.evento.FuncionNotFoundException;
import com.ticketflow.reservation.domain.evento.FuncionRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de aplicación para la consulta de disponibilidad de
 * asientos de una {@code Funcion}.
 *
 * <p>Se anota con {@link Transactional @Transactional(readOnly = true)}
 * para que Hibernate opere en modo de solo lectura durante toda la
 * navegación del grafo (evento → función → asientos), evitando
 * flushes innecesarios y aprovechando optimizaciones del datasource.</p>
 */
@Service
public class AsientoService {

    private final EventoRepository eventoRepository;
    private final FuncionRepository funcionRepository;
    private final AsientoRepository asientoRepository;

    /**
     * Constructor con inyección por constructor (estilo recomendado
     * sobre {@code @Autowired} en campo).
     *
     * @param eventoRepository   repositorio de eventos; no puede ser
     *                           {@code null}.
     * @param funcionRepository  repositorio de funciones; no puede ser
     *                           {@code null}.
     * @param asientoRepository  repositorio de asientos; no puede ser
     *                           {@code null}.
     */
    public AsientoService(EventoRepository eventoRepository,
                          FuncionRepository funcionRepository,
                          AsientoRepository asientoRepository) {
        this.eventoRepository = eventoRepository;
        this.funcionRepository = funcionRepository;
        this.asientoRepository = asientoRepository;
    }

    /**
     * Devuelve la lista de asientos (enumerados como DTOs de
     * disponibilidad) de la función indicada, validando que tanto el
     * evento como la función existan.
     *
     * @param eventoId  identificador del evento al que pertenece la
     *                  función; debe existir.
     * @param funcionId identificador de la función cuyos asientos se
     *                  quieren listar; debe existir y pertenecer al
     *                  evento indicado.
     * @return lista de DTOs {@link AsientoDisponibilidadDto}
     *         ordenados por número ascendente.
     * @throws EventoNotFoundException  si {@code eventoId} no existe
     *                                  en la base de datos.
     * @throws FuncionNotFoundException si {@code funcionId} no existe
     *                                  o no pertenece a
     *                                  {@code eventoId}.
     */
    @Transactional(readOnly = true)
    public List<AsientoDisponibilidadDto> listarAsientos(Long eventoId, Long funcionId) {
        if (!eventoRepository.existsById(eventoId)) {
            throw new EventoNotFoundException(eventoId);
        }
        var funcion = funcionRepository.findById(funcionId)
                .filter(f -> f.getEvento().getId().equals(eventoId))
                .orElseThrow(() -> new FuncionNotFoundException(eventoId, funcionId));
        return asientoRepository.findByFuncionIdOrderByNumeroAsc(funcion.getId())
                .stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Proyecta una entidad {@link Asiento} a su DTO público.
     *
     * @param asiento entidad a proyectar; no puede ser {@code null}.
     * @return DTO de disponibilidad con {@code numero} y
     *         {@code estado}.
     */
    private AsientoDisponibilidadDto toDto(Asiento asiento) {
        return new AsientoDisponibilidadDto(asiento.getNumero(), asiento.getEstado());
    }
}
