package com.ticketflow.reservation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del microservicio <strong>reservation-service</strong>
 * de TicketFlow.
 *
 * <p>Es el responsable del núcleo de reservas: selección de asientos,
 * locks distribuidos en Redis, procesamiento de pago y persistencia
 * transaccional (ACID) sobre PostgreSQL. Por convención de Boot, agrupa
 * el escaneo de componentes bajo
 * {@code com.ticketflow.reservation} y activa el auto-configurado de
 * Spring Boot.</p>
 */
@SpringBootApplication
public class TicketFlowReservationApplication {

    /**
     * Arranca el contexto de Spring Boot delegando en
     * {@link SpringApplication#run(Class, String...)}.
     *
     * @param args argumentos de línea de comandos estándar de una
     *              aplicación Spring Boot (perfiles, puerto del servidor,
     *              overrides de propiedades, etc.). No se esperan
     *              argumentos posicionales propios.
     */
    public static void main(String[] args) {
        SpringApplication.run(TicketFlowReservationApplication.class, args);
    }
}