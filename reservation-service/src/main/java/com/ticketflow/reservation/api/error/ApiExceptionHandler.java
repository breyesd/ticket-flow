package com.ticketflow.reservation.api.error;

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
}
