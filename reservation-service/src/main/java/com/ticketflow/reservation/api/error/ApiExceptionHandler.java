package com.ticketflow.reservation.api.error;

import com.ticketflow.reservation.domain.evento.AsientoBloqueadoException;
import com.ticketflow.reservation.domain.evento.AsientoNoPropietarioException;
import com.ticketflow.reservation.domain.evento.AsientoVendidoException;
import com.ticketflow.reservation.domain.evento.EventoNotFoundException;
import com.ticketflow.reservation.domain.evento.FuncionNotFoundException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Manejador central de excepciones del API REST de
 * {@code reservation-service}.
 *
 * <p>Devuelve respuestas conformes al estándar
 * <a href="https://datatracker.ietf.org/doc/html/rfc7807">RFC 7807
 * (Problem Details for HTTP APIs)</a> usando {@link ProblemDetail} de
 * Spring 6. El {@code type} apunta a un URI de la documentación del
 * proyecto y los campos {@code title}, {@code status} y {@code detail}
 * se completan a partir de la excepción.</p>
 *
 * <p>Esta clase es el único punto donde se mapean excepciones de
 * dominio a respuestas HTTP: los controladores no capturan
 * excepciones de dominio, las dejan propagar hasta aquí.</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * Mapea {@link EventoNotFoundException} a una respuesta HTTP 404
     * con cuerpo Problem Details.
     *
     * @param ex excepción lanzada cuando el evento solicitado no
     *           existe.
     * @return respuesta 404 con cuerpo RFC 7807.
     */
    @ExceptionHandler(EventoNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleEventoNotFound(EventoNotFoundException ex) {
        ProblemDetail body = problemDetail(ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    /**
     * Mapea {@link FuncionNotFoundException} a una respuesta HTTP 404
     * con cuerpo Problem Details.
     *
     * @param ex excepción lanzada cuando la función solicitada no
     *           existe bajo el evento indicado.
     * @return respuesta 404 con cuerpo RFC 7807.
     */
    @ExceptionHandler(FuncionNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleFuncionNotFound(FuncionNotFoundException ex) {
        ProblemDetail body = problemDetail(ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    /**
     * Construye un {@link ProblemDetail} 404 con el formato común
     * para todas las excepciones de "no encontrado" del API.
     *
     * @param detail mensaje de detalle específico de la excepción.
     * @return cuerpo Problem Details listo para devolver.
     */
    private ProblemDetail problemDetail(String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, detail);
        body.setTitle("Not Found");
        body.setType(URI.create("https://ticketflow.dev/errors/not-found"));
        return body;
    }

    /**
     * Mapea {@link AsientoBloqueadoException} a una respuesta HTTP 409
     * Conflict con cuerpo Problem Details.
     *
     * @param ex excepción lanzada cuando el asiento ya tiene un lock
     *           activo.
     * @return respuesta 409 con cuerpo RFC 7807.
     */
    @ExceptionHandler(AsientoBloqueadoException.class)
    public ResponseEntity<ProblemDetail> handleAsientoBloqueado(AsientoBloqueadoException ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        body.setTitle("Conflict");
        body.setType(URI.create("https://ticketflow.dev/errors/conflict"));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    /**
     * Mapea {@link AsientoNoPropietarioException} a una respuesta HTTP 409
     * Conflict con cuerpo Problem Details.
     *
     * @param ex excepción lanzada cuando se intenta liberar un lock que
     *           pertenece a otro propietario.
     * @return respuesta 409 con cuerpo RFC 7807.
     */
    @ExceptionHandler(AsientoNoPropietarioException.class)
    public ResponseEntity<ProblemDetail> handleAsientoNoPropietario(
            AsientoNoPropietarioException ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        body.setTitle("Conflict");
        body.setType(URI.create("https://ticketflow.dev/errors/conflict"));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    /**
     * Mapea {@link AsientoVendidoException} a una respuesta HTTP 422
     * Unprocessable Entity con cuerpo Problem Details.
     *
     * @param ex excepción lanzada cuando el asiento ya está vendido.
     * @return respuesta 422 con cuerpo RFC 7807.
     */
    @ExceptionHandler(AsientoVendidoException.class)
    public ResponseEntity<ProblemDetail> handleAsientoVendido(AsientoVendidoException ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        body.setTitle("Unprocessable Entity");
        body.setType(URI.create("https://ticketflow.dev/errors/unprocessable-entity"));
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
