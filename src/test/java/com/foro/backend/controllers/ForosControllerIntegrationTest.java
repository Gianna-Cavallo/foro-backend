package com.foro.backend.controllers;

import com.foro.backend.config.SecurityConfig;
import com.foro.backend.dto.ForoDTO;
import com.foro.backend.services.ForosService;
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
// @Import(SecurityConfig.class) es necesario: sin él, Spring Boot arma una
// configuración de seguridad por defecto para el slice (usuario in-memory
// generado, CSRF activo) que no refleja el comportamiento real de la app.
// Importando el SecurityConfig verdadero se usa el SecurityFilterChain real,
// donde "/api/foros" está en permitAll().
//
// Es "de integración" (y no unitario) porque el Controller no se prueba
// aislado: la request atraviesa de verdad el filtro de seguridad, el
// DispatcherServlet y el serializador JSON. Lo único simulado es el Service.
@WebMvcTest(ForosController.class)
@Import(SecurityConfig.class)
class ForosControllerIntegrationTest {

    // MockMvc simula requests HTTP contra el Controller sin abrir un puerto real.
    @Autowired
    private MockMvc mockMvc;

    // @MockitoBean reemplaza el bean ForosService del contexto por un mock.
    // (En Spring Boot 3.4+ sustituye al viejo @MockBean.) El Controller depende
    // de la interface ForosService, así que sin este mock el slice ni arrancaría.
    @MockitoBean
    private ForosService forosService;

    private static final LocalDateTime CREADO = LocalDateTime.of(2026, 1, 15, 10, 0);
    private static final LocalDateTime ACTUALIZADO = LocalDateTime.of(2026, 2, 20, 18, 30);

    @Test
    @DisplayName("GET /api/foros devuelve 200 y la lista de foros serializada a JSON")
    void getForos_devuelve200ConLaListaEnJson() throws Exception {
        // ARRANGE: el Service (mockeado) devuelve dos foros
        ForoDTO humanidades = new ForoDTO(1L, "Humanidades",
                "Carreras de letras, historia, filosofía y educación.",
                "humanidades", CREADO, ACTUALIZADO);
        ForoDTO teologia = new ForoDTO(2L, "Teología",
                "Estudios bíblicos, teológicos y pastorales.",
                "teologia", CREADO, ACTUALIZADO);

        when(forosService.listarTodos()).thenReturn(List.of(humanidades, teologia));

        // ACT + ASSERT
        mockMvc.perform(get("/api/foros"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                // El JSON respeta el orden que devolvió el Service
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

        // El Controller delega en el Service: no tiene lógica propia
        verify(forosService).listarTodos();
    }

    @Test
    @DisplayName("GET /api/foros es público: responde 200 sin autenticarse")
    void getForos_esPublico_noRequiereAutenticacion() throws Exception {
        // No usamos @WithMockUser: la request sale como usuario anónimo.
        // Como SecurityConfig tiene "/api/foros" en permitAll(), tiene que
        // pasar el filtro de seguridad y llegar al Controller (200, no 401/403).
        when(forosService.listarTodos()).thenReturn(List.of());

        mockMvc.perform(get("/api/foros"))
                .andExpect(status().isOk())
                // Sin foros la respuesta es un array vacío, no null
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
