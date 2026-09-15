package com.ticketflow.reservation.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Tests de arquitectura para reservation-service (código de producción).
 *
 * <p>Reglas basadas en Clean Architecture / Arquitectura Hexagonal:</p>
 * <ul>
 *   <li>domain no depende de api, mensajería, lock, pago, config</li>
 *   <li>api (controllers, DTOs) solo usa domain y Spring Web</li>
 *   <li>mensajería (event publisher, listener) usa domain y Spring Kafka</li>
 *   <li>lock usa domain y Spring Data Redis</li>
 *   <li>pago usa domain y Spring para inyección</li>
 *   <li>config (configuración Spring) puede usar Spring Data Redis, Kafka, etc.</li>
 * </ul>
 *
 * <p>Se excluyen clases de test vía import options y se usan reglas de slices
 * para detectar ciclos.</p>
 */
@AnalyzeClasses(packages = "com.ticketflow.reservation",
    importOptions = {
        ImportOption.DoNotIncludeTests.class,
        ImportOption.DoNotIncludeArchives.class,
        ImportOption.DoNotIncludeJars.class
    })
class ReservationArchitectureTest {

    @ArchTest
    static final ArchRule domain_no_depende_de_capas_superiores =
        noClasses()
            .that().resideInAPackage("com.ticketflow.reservation.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "com.ticketflow.reservation.api..",
                "com.ticketflow.reservation.mensajeria..",
                "com.ticketflow.reservation.lock..",
                "com.ticketflow.reservation.pago..",
                "com.ticketflow.reservation.config..")
            .because("El dominio (domain) es el núcleo y no debe depender de capas externas");

    @ArchTest
    static final ArchRule api_solo_usa_domain_y_spring_web =
        classes()
            .that().resideInAPackage("com.ticketflow.reservation.api..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                "com.ticketflow.reservation.api..",
                "com.ticketflow.reservation.domain..",
                "com.ticketflow.reservation.lock..",
                "com.ticketflow.reservation.pago..",
                "com.ticketflow.common..",
                "java..",
                "jakarta.validation..",
                "org.springframework.web..",
                "org.springframework.http..",
                "org.springframework.validation..",
                "org.springframework.stereotype..",
                "org.springframework.beans.factory..",
                "org.springframework.transaction..",
                "org.springframework.context..",
                "io.swagger.v3.oas.annotations..",
                "com.fasterxml.jackson..")
            .because("La capa API (controllers, DTOs, services de aplicación) orquestra domain, lock, pago y usa Spring Web + OpenAPI/Swagger");

    @ArchTest
    static final ArchRule mensajeria_usa_domain_kafka_y_interno =
        classes()
            .that().resideInAPackage("com.ticketflow.reservation.mensajeria..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                "com.ticketflow.reservation.domain..",
                "com.ticketflow.reservation.mensajeria..",
                "com.ticketflow.common..",
                "org.springframework.kafka..",
                "org.springframework.messaging..",
                "org.springframework.stereotype..",
                "org.springframework.transaction..",
                "org.springframework.context.event..",
                "java..",
                "com.fasterxml.jackson..")
            .because("La capa de mensajería usa domain events, sus propias interfaces, Spring Kafka y transacciones");

    @ArchTest
    static final ArchRule lock_usa_domain_y_redis =
        classes()
            .that().resideInAPackage("com.ticketflow.reservation.lock..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                "com.ticketflow.reservation.domain..",
                "com.ticketflow.reservation.lock..",
                "com.ticketflow.common..",
                "org.springframework.data.redis..",
                "org.springframework.stereotype..",
                "java..")
            .because("El lock distribuido es infraestructura: usa Redis, domain y sus propios tipos internos");

    @ArchTest
    static final ArchRule pago_usa_domain_y_spring =
        classes()
            .that().resideInAPackage("com.ticketflow.reservation.pago..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                "com.ticketflow.reservation.domain..",
                "com.ticketflow.reservation.pago..",
                "com.ticketflow.common..",
                "org.springframework.stereotype..",
                "org.springframework.beans.factory..",
                "java..")
            .because("El gateway de pago usa domain, sus propios DTOs y Spring para inyección");

    @ArchTest
    static final ArchRule config_usa_spring_redis_kafka =
        classes()
            .that().resideInAPackage("com.ticketflow.reservation.config..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                "com.ticketflow.reservation.domain..",
                "com.ticketflow.common..",
                "org.springframework.data.redis..",
                "org.springframework.kafka..",
                "org.springframework.stereotype..",
                "org.springframework.beans.factory..",
                "org.springframework.context.annotation..",
                "org.springframework.boot.autoconfigure..",
                "java..",
                "com.fasterxml.jackson..")
            .because("La configuración Spring puede usar Redis, Kafka y beans de infraestructura");

    @ArchTest
    static final ArchRule repositorios_jpa_en_paquete_domain =
        classes()
            .that().haveSimpleNameEndingWith("Repository")
            .and().resideInAPackage("com.ticketflow.reservation.domain..")
            .should().resideInAnyPackage(
                "com.ticketflow.reservation.domain.evento..",
                "com.ticketflow.reservation.domain.reserva..")
            .because("Los repositorios JPA residen en domain junto a sus agregados");

    @ArchTest
    static final ArchRule entidades_en_paquete_domain_evento_o_reserva =
        classes()
            .that().areAnnotatedWith("jakarta.persistence.Entity")
            .should().resideInAnyPackage(
                "com.ticketflow.reservation.domain.evento..",
                "com.ticketflow.reservation.domain.reserva..")
            .because("Las entidades JPA deben residir en domain");

    @ArchTest
    static final ArchRule no_ciclos_api_mensajeria_produccion =
        noClasses()
            .that().resideInAPackage("com.ticketflow.reservation.api..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.ticketflow.reservation.mensajeria..")
            .because("API y mensajería son capas separadas, no deben depender mutuamente (código de producción)");

    @ArchTest
    static final ArchRule solo_capas_permitidas_usan_spring_web_kafka_redis =
        noClasses()
            .that().resideOutsideOfPackages(
                "com.ticketflow.reservation.api..",
                "com.ticketflow.reservation.mensajeria..",
                "com.ticketflow.reservation.lock..",
                "com.ticketflow.reservation.pago..",
                "com.ticketflow.reservation.config..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework.web..", "org.springframework.kafka..", "org.springframework.data.redis..")
            .because("Solo API (web), mensajería (Kafka), lock (Redis), pago y config pueden depender de Spring Web/Kafka/Redis");

    @ArchTest
    static final ArchRule no_ciclos_entre_modulos_principales =
        slices().matching("com.ticketflow.reservation.(*)..")
            .should().beFreeOfCycles()
            .because("No debe haber ciclos de dependencia entre los módulos principales (api, domain, mensajeria, lock, pago, config)");
}