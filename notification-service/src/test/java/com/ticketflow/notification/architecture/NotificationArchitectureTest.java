package com.ticketflow.notification.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Tests de arquitectura para notification-service (código de producción).
 *
 * <p>Reglas:</p>
 * <ul>
 *   <li>notification-service es un consumidor puro: solo consume eventos, no publica</li>
 *   <li>No debe tener acceso a base de datos (JPA)</li>
 *   <li>Solo usa common (eventos) y Spring Kafka</li>
 *   <li>La lógica de notificación está en service, el listener es adaptador de entrada</li>
 * </ul>
 *
 * <p>Se excluyen clases de test de las reglas.</p>
 */
@AnalyzeClasses(packages = "com.ticketflow.notification",
    importOptions = {
        ImportOption.DoNotIncludeTests.class,
        ImportOption.DoNotIncludeArchives.class,
        ImportOption.DoNotIncludeJars.class
    })
class NotificationArchitectureTest {

    @ArchTest
    static final ArchRule no_depende_de_jpa =
        noClasses()
            .that().resideInAPackage("com.ticketflow.notification..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "javax.persistence..",
                "jakarta.persistence..",
                "org.springframework.data.jpa..",
                "org.hibernate..")
            .because("notification-service es stateless y no debe persistir datos");

    @ArchTest
    static final ArchRule no_depende_de_reservation_service =
        noClasses()
            .that().resideInAPackage("com.ticketflow.notification..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.ticketflow.reservation..")
            .because("notification-service no debe depender del servicio de reservas");

    @ArchTest
    static final ArchRule solo_usa_common_kafka_y_spring_core =
        classes()
            .that().resideInAPackage("com.ticketflow.notification..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                "com.ticketflow.common..",
                "com.ticketflow.notification..",
                "org.springframework.kafka..",
                "org.springframework.stereotype..",
                "org.springframework.boot..",
                "org.springframework.beans.factory..",
                "org.slf4j..",
                "com.fasterxml.jackson..",
                "java..",
                "jakarta.validation..")
            .because("notification-service solo usa common (eventos), Spring Kafka, "
                + "core y SLF4J para logging");

    @ArchTest
    static final ArchRule listener_en_paquete_mensajeria =
        classes()
            .that().haveSimpleNameEndingWith("Listener")
            .should().resideInAPackage("com.ticketflow.notification.mensajeria..")
            .because("Los listeners de Kafka deben estar en el paquete mensajería");

    @ArchTest
    static final ArchRule servicios_en_paquete_notificacion =
        classes()
            .that().haveSimpleNameEndingWith("Service")
            .and().resideInAPackage("com.ticketflow.notification..")
            .should().resideInAPackage("com.ticketflow.notification.notificacion..")
            .because("Los servicios de notificación deben estar en el paquete notificacion");

    @ArchTest
    static final ArchRule no_controllers_web =
        noClasses()
            .that().resideInAPackage("com.ticketflow.notification..")
            .should().dependOnClassesThat()
            .resideInAPackage("org.springframework.web..")
            .because("notification-service no expone API REST, solo consume eventos");
}