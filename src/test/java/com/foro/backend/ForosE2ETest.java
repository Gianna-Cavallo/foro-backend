package com.foro.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foro.backend.dto.CrearForoDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Test END-TO-END de foros: POST /api/foros y después GET /api/foros.
//
// @SpringBootTest levanta el contexto COMPLETO (web + Security + JPA), a
// diferencia del @WebMvcTest de ForosControllerIntegrationTest, que solo
// levanta la capa web. Acá el foro se escribe y se lee de verdad en la base.
//
// @ActiveProfiles("test") hace que se use application-test.properties, o sea
// H2 en memoria en vez de MySQL. Sin esto el test intentaría conectarse a la
// base real.
//
// @AutoConfigureMockMvc agrega MockMvc al contexto completo, para poder pegarle
// a los endpoints sin levantar un puerto HTTP real.
//
// ⚠️ IMPORTANTE — la base NUNCA arranca vacía:
// DataSeeder es un CommandLineRunner sin @Profile, así que se ejecuta en
// cualquier @SpringBootTest e inserta 6 foros y 5 usuarios antes de que corra
// el @Test. Por eso este test NO afirma cuántos foros hay (nada de hasSize(1)
// ni count() == 0): busca puntualmente el foro que creó, por id y por nombre.
//
// Tampoco se autentica: "/api/foros" está en permitAll() dentro de
// SecurityConfig, así que tanto el POST como el GET son públicos.
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ForosE2ETest {

    @Autowired
    private MockMvc mockMvc;

    // ObjectMapper de Spring Boot: ya viene configurado para LocalDateTime
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("E2E: POST /api/foros crea el foro y después aparece en GET /api/foros")
    void crearForo_yDespuesAparecerEnElListado() throws Exception {
        // ARRANGE
        // Nombre único por corrida: así el foro no choca con los 6 del
        // DataSeeder ni con lo que haya dejado otro test en el mismo contexto.
        String nombreUnico = "Foro E2E " + UUID.randomUUID();

        CrearForoDTO nuevoForo = new CrearForoDTO();
        nuevoForo.setNombre(nombreUnico);
        nuevoForo.setDescripcion("Foro creado por el test end-to-end.");
        nuevoForo.setFacultad("general");

        // ── PASO 1: POST /api/foros ─────────────────────────────────────────
        String respuestaPost = mockMvc.perform(post("/api/foros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nuevoForo)))
                // El Controller responde 201 CREATED, no 200
                .andExpect(status().isCreated())
                // El id lo generó la BD de verdad (H2), no un mock
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nombre").value(nombreUnico))
                .andExpect(jsonPath("$.descripcion").value("Foro creado por el test end-to-end."))
                .andExpect(jsonPath("$.facultad").value("general"))
                // @CreationTimestamp de BaseEntity completó la fecha al persistir
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long idCreado = objectMapper.readTree(respuestaPost).get("id").asLong();
        assertThat(idCreado).isNotNull().isPositive();

        // ── PASO 2: GET /api/foros ──────────────────────────────────────────
        // No afirmamos el tamaño de la lista: el DataSeeder ya dejó 6 foros y
        // el total depende de qué otros tests hayan corrido en este contexto.
        // Filtramos por el id que devolvió el POST.
        //
        // El jsonPath "$[?(@.id == N)]" es un filtro: recorre el array y se
        // queda con los elementos cuyo campo id valga N. Si encuentra
        // exactamente uno, Spring desenvuelve la lista y compara el valor;
        // si encuentra cero o más de uno, el test falla.
        mockMvc.perform(get("/api/foros"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + idCreado + ")].nombre").value(nombreUnico))
                .andExpect(jsonPath("$[?(@.id == " + idCreado + ")].facultad").value("general"))
                .andExpect(jsonPath("$[?(@.id == " + idCreado + ")].descripcion")
                        .value("Foro creado por el test end-to-end."));

        // Y el mismo foro también se encuentra buscándolo por nombre,
        // que es la otra forma estable de identificarlo sin contar registros.
        mockMvc.perform(get("/api/foros"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == '" + nombreUnico + "')].id")
                        .value(idCreado.intValue()));
    }
}
