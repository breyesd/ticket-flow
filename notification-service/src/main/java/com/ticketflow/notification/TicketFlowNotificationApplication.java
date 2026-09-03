package com.ticketflow.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del microservicio <strong>notification-service</strong>
 * de TicketFlow (spec 0001, sección 2.2; plan Fase 4, F4.T4).
 *
 * <p>Es un consumidor puro de eventos vía Kafka: no expone API REST ni
 * accede a bases de datos. Escucha el topic {@code tickets.orders} y,
 * al recibir un {@code ReservaConfirmadaEvent}, delega en el servicio de
 * notificación para simular el envío del boleto. Por convención de
 * Spring Boot, agrupa el escaneo de componentes bajo
 * {@code com.ticketflow.notification} y activa el auto-configurado.</p>
 */
@SpringBootApplication
public class TicketFlowNotificationApplication {

    /**
     * Arranca el contexto de Spring Boot delegando en
     * {@link SpringApplication#run(Class, String...)}.
     *
     * @param args argumentos de línea de comandos estándar de una
     *             aplicación Spring Boot (perfiles, overrides de
     *             propiedades de Kafka, etc.). No se esperan argumentos
     *             posicionales propios.
     */
    public static void main(String[] args) {
        SpringApplication.run(TicketFlowNotificationApplication.class, args);
    }
}