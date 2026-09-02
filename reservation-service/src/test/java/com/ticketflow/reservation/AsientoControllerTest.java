package com.ticketflow.reservation;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketflow.reservation.support.EmbeddedRedisExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Test de integración del endpoint
 * {@code GET /api/v1/eventos/{eventoId}/funciones/{funcionId}/asientos}
 * y del formato de error RFC 7807 aplicado por el
 * {@code @RestControllerAdvice} central.
 *
 * <p>Arranca contra el perfil {@code test} (H2 + Flyway con V1+V2
 * aplicadas), de modo que el seed con 1 evento, 1 función y 100
 * asientos en estado {@code DISPONIBLE} ya está disponible.</p>
 *
 * <p>Usa {@link EmbeddedRedisExtension} para disponer de un Redis
 * embebido durante la carga del contexto (necesario porque el perfil
 * {@code test} ya no excluye {@code RedisAutoConfiguration}).</p>
 */
@ExtendWith(EmbeddedRedisExtension.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AsientoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Verifica la respuesta 200 para la combinación evento/función del
     * seed: devuelve exactamente 100 elementos con los campos
     * {@code numero} y {@code estado} del DTO.
     */
    @Test
    void getAsientosReturnsSeededAsientos() throws Exception {
        mockMvc.perform(get("/api/v1/eventos/{eventoId}/funciones/{funcionId}/asientos", 1L, 1L))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$", hasSize(100)))
                .andExpect(jsonPath("$[0].numero").value(1))
                .andExpect(jsonPath("$[0].estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$[99].numero").value(100));
    }

    /**
     * Verifica que un evento inexistente devuelve 404 con cuerpo
     * Problem Details (RFC 7807) y el título "Not Found".
     */
    @Test
    void getAsientosReturns404WhenEventoDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/eventos/{eventoId}/funciones/{funcionId}/asientos", 9999L, 1L))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"));
    }

    /**
     * Verifica que una función inexistente dentro de un evento
     * existente devuelve 404 con cuerpo Problem Details.
     */
    @Test
    void getAsientosReturns404WhenFuncionDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/eventos/{eventoId}/funciones/{funcionId}/asientos", 1L, 9999L))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"));
    }
}
