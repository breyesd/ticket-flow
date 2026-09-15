package com.ticketflow.common.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Tests de arquitectura para el módulo common.
 *
 * <p>Reglas:</p>
 * <ul>
 *   <li>common no debe depender de reservation-service ni notification-service</li>
 *   <li>common debe ser independiente (sin dependencias a Spring, JPA, Kafka, etc.)</li>
 * </ul>
 */
@AnalyzeClasses(packages = "com.ticketflow.common")
class CommonArchitectureTest {

    @ArchTest
    static final ArchRule common_no_depende_de_reservation_service =
        noClasses()
            .that().resideInAPackage("com.ticketflow.common..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.ticketflow.reservation..")
            .because("common es un módulo compartido y no debe depender de servicios concretos");

    @ArchTest
    static final ArchRule common_no_depende_de_notification_service =
        noClasses()
            .that().resideInAPackage("com.ticketflow.common..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.ticketflow.notification..")
            .because("common es un módulo compartido y no debe depender de servicios concretos");

    @ArchTest
    static final ArchRule common_no_depende_de_spring_framework =
        noClasses()
            .that().resideInAPackage("com.ticketflow.common..")
            .should().dependOnClassesThat()
            .resideInAPackage("org.springframework..")
            .because("common debe ser independiente de frameworks (POJO puro)");

    @ArchTest
    static final ArchRule common_no_depende_de_jpa =
        noClasses()
            .that().resideInAPackage("com.ticketflow.common..")
            .should().dependOnClassesThat()
            .resideInAPackage("javax.persistence..")
            .orShould().dependOnClassesThat()
            .resideInAPackage("jakarta.persistence..")
            .because("common no debe tener dependencias a JPA");

    @ArchTest
    static final ArchRule common_no_depende_de_kafka =
        noClasses()
            .that().resideInAPackage("com.ticketflow.common..")
            .should().dependOnClassesThat()
            .resideInAPackage("org.apache.kafka..")
            .because("common no debe tener dependencias a Kafka");
}