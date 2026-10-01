package com.foro.backend.controllers;

import com.foro.backend.config.SecurityConfig;
import com.foro.backend.models.Foro;
import com.foro.backend.repositories.ForoRepository;
import com.foro.backend.services.ForosServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Test de INTEGRACIÓN de la capa web para GET /api/foros.
//
// @WebMvcTest levanta solo la porción web del contexto (el Controller, el
// mapeo de rutas, la serialización a JSON y los filtros de Spring Security),
// NO la capa de persistencia. Por eso acá no hay H2, ni JPA, ni DataSeeder.
//
// Los dos @Import son necesarios, cada uno por su motivo:
//
//  · SecurityConfig.class — sin él, Spring Boot arma una configuración de
//    seguridad por defecto para el slice (usuario in-memory generado, CSRF
//    activo) que no refleja el comportamiento real de la app. Importando el
//    SecurityConfig verdadero se usa el SecurityFilterChain real, donde
//    "/api/foros" está en permitAll().
//
//  · ForosServiceImpl.class — @WebMvcTest no incluye los beans @Service.
//    El Controller depende de la interface ForosService, así que sin un bean
//    concreto que la implemente el contexto no arranca
//    (UnsatisfiedDependencyException).
//
// El corte se hace en el REPOSITORIO: el único doble es ForoRepository.
// Así el test ejercita de verdad el Controller Y el Service, y solo simula
// el acceso a datos. Ése es el mismo criterio que documenta TESTING.md para
// el @WebMvcTest de AuthController.
@WebMvcTest(ForosController.class)
@Import({SecurityConfig.class, ForosServiceImpl.class})
class ForosControllerIntegrationTest {

    // MockMvc simula requests HTTP contra el Controller sin abrir un puerto real.
    @Autowired
    private MockMvc mockMvc;

    // @MockitoBean reemplaza el bean ForoRepository del contexto por un mock.
    // (En Spring Boot 3.4+ sustituye al viejo @MockBean.) Mockeamos el
    // repositorio y no el Service para que la lógica de ForosServiceImpl
    // —incluido el mapeo entidad → DTO— corra de verdad dentro de este test.
    @MockitoBean
    private ForoRepository foroRepository;

    private static final LocalDateTime CREADO = LocalDateTime.of(2026, 1, 15, 10, 0);
    private static final LocalDateTime ACTUALIZADO = LocalDateTime.of(2026, 2, 20, 18, 30);

    @Test
    @DisplayName("GET /api/foros devuelve 200 y la lista de foros serializada a JSON")
    void getForos_devuelve200ConLaListaEnJson() throws Exception {
        // ARRANGE: el repositorio (mockeado) devuelve ENTIDADES Foro.
        // El mapeo a ForoDTO lo hace ForosServiceImpl, que acá es real.
        Foro humanidades = construirForo(1L, "Humanidades",
                "Carreras de letras, historia, filosofía y educación.", "humanidades");
        Foro teologia = construirForo(2L, "Teología",
                "Estudios bíblicos, teológicos y pastorales.", "teologia");

        when(foroRepository.findAll()).thenReturn(List.of(humanidades, teologia));

        // ACT + ASSERT
        mockMvc.perform(get("/api/foros"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                // El JSON respeta el orden en que vinieron del repositorio
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Humanidades"))
                .andExpect(jsonPath("$[0].descripcion")
                        .value("Carreras de letras, historia, filosofía y educación."))
                .andExpect(jsonPath("$[0].facultad").value("humanidades"))
                // Los timestamps también se serializan (ISO-8601)
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[0].updatedAt").exists())
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].nombre").value("Teología"))
                .andExpect(jsonPath("$[1].facultad").value("teologia"));

        // La cadena Controller → Service → Repository se recorrió entera,
        // y un GET no puede escribir en la base.
        verify(foroRepository).findAll();
        verify(foroRepository, never()).save(any());
    }

    @Test
    @DisplayName("GET /api/foros es público: responde 200 sin autenticarse")
    void getForos_esPublico_noRequiereAutenticacion() throws Exception {
        // No usamos @WithMockUser: la request sale como usuario anónimo.
        // Como SecurityConfig tiene "/api/foros" en permitAll(), tiene que
        // pasar el filtro de seguridad y llegar al Controller (200, no 401/403).
        when(foroRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/foros"))
                .andExpect(status().isOk())
                // Sin foros la respuesta es un array vacío, no null
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ── helper ──────────────────────────────────────────────────────────────

    // Arma una entidad Foro como la que devolvería la BD: con id y timestamps.
    // setId / setCreatedAt / setUpdatedAt los hereda de BaseEntity (Lombok @Setter).
    private Foro construirForo(Long id, String nombre, String descripcion, String facultad) {
        Foro foro = new Foro();
        foro.setId(id);
        foro.setNombre(nombre);
        foro.setDescripcion(descripcion);
        foro.setFacultad(facultad);
        foro.setCreatedAt(CREADO);
        foro.setUpdatedAt(ACTUALIZADO);
        return foro;
    }
}
