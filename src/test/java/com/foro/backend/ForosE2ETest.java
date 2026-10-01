package com.foro.backend;

import com.foro.backend.dto.CrearForoDTO;
import com.foro.backend.dto.ForoDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

// Test END-TO-END de foros: POST /api/foros y después GET /api/foros.
//
// webEnvironment = RANDOM_PORT levanta un servidor Tomcat DE VERDAD en un
// puerto libre, y TestRestTemplate le pega por HTTP real: socket, serialización
// de ida y de vuelta, filtros de seguridad, todo. Es un paso más realista que
// MockMvc, que simula el ciclo de request en memoria sin abrir ningún puerto.
//
// @ActiveProfiles("test") hace que se use application-test.properties, o sea
// H2 en memoria en vez de MySQL. Sin esto el test intentaría conectarse a la
// base real.
//
// ⚠️ IMPORTANTE — la base NUNCA arranca vacía:
// DataSeeder es un CommandLineRunner sin @Profile, así que se ejecuta en
// cualquier @SpringBootTest e inserta 6 foros y 5 usuarios antes de que corra
// el @Test. Por eso este test NO afirma cuántos foros hay (nada de hasSize(1)
// ni count() == 0): busca puntualmente el foro que creó, por id y por nombre.
//
// Tampoco manda credenciales: "/api/foros" está en permitAll() dentro de
// SecurityConfig, así que tanto el POST como el GET son públicos.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ForosE2ETest {

    // TestRestTemplate ya viene apuntado al host y al puerto que eligió el
    // test, así que las URLs se escriben relativas ("/api/foros").
    // A diferencia de RestTemplate, no lanza excepción ante un 4xx/5xx:
    // devuelve el status dentro de la respuesta para poder afirmarlo.
    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("E2E: POST /api/foros crea el foro y después aparece en GET /api/foros")
    void crearForo_yDespuesAparecerEnElListado() {
        // ARRANGE
        // Nombre único por corrida: así el foro no choca con los 6 del
        // DataSeeder ni con lo que haya dejado otra corrida del test.
        String nombreUnico = "Foro E2E " + UUID.randomUUID();

        CrearForoDTO nuevoForo = new CrearForoDTO();
        nuevoForo.setNombre(nombreUnico);
        nuevoForo.setDescripcion("Foro creado por el test end-to-end.");
        nuevoForo.setFacultad("general");

        // ── PASO 1: POST /api/foros ─────────────────────────────────────────
        // postForEntity serializa el DTO a JSON, lo manda por HTTP real y
        // deserializa la respuesta al tipo que le pedimos.
        ResponseEntity<ForoDTO> respuestaPost =
                restTemplate.postForEntity("/api/foros", nuevoForo, ForoDTO.class);

        // El Controller responde 201 CREATED, no 200
        assertThat(respuestaPost.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ForoDTO creado = respuestaPost.getBody();
        assertThat(creado).isNotNull();
        // El id lo generó la BD de verdad (H2), no un mock
        assertThat(creado.getId()).isNotNull().isPositive();
        assertThat(creado.getNombre()).isEqualTo(nombreUnico);
        assertThat(creado.getDescripcion()).isEqualTo("Foro creado por el test end-to-end.");
        assertThat(creado.getFacultad()).isEqualTo("general");
        // @CreationTimestamp de BaseEntity completó la fecha al persistir
        assertThat(creado.getCreatedAt()).isNotNull();

        Long idCreado = creado.getId();

        // ── PASO 2: GET /api/foros ──────────────────────────────────────────
        // Pedimos un array porque el endpoint devuelve una lista JSON.
        ResponseEntity<ForoDTO[]> respuestaGet =
                restTemplate.getForEntity("/api/foros", ForoDTO[].class);

        assertThat(respuestaGet.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuestaGet.getBody()).isNotNull();

        // No afirmamos el tamaño de la lista: el DataSeeder ya dejó 6 foros y
        // el total depende de qué otros tests hayan corrido en este contexto.
        // Buscamos por el id que devolvió el POST, exigiendo un único match.
        ForoDTO encontradoPorId = buscarUnico(respuestaGet.getBody(),
                foro -> idCreado.equals(foro.getId()),
                "id " + idCreado);

        assertThat(encontradoPorId.getNombre()).isEqualTo(nombreUnico);
        assertThat(encontradoPorId.getFacultad()).isEqualTo("general");
        assertThat(encontradoPorId.getDescripcion())
                .isEqualTo("Foro creado por el test end-to-end.");

        // Y el mismo foro también se encuentra buscándolo por nombre,
        // que es la otra forma estable de identificarlo sin contar registros.
        ForoDTO encontradoPorNombre = buscarUnico(respuestaGet.getBody(),
                foro -> nombreUnico.equals(foro.getNombre()),
                "nombre " + nombreUnico);

        assertThat(encontradoPorNombre.getId()).isEqualTo(idCreado);
    }

    // ── helper ──────────────────────────────────────────────────────────────

    // Devuelve el único foro del array que cumpla el filtro.
    // Falla con un mensaje claro si no hay ninguno o si hay más de uno: es el
    // equivalente a lo que hacía el filtro jsonPath "$[?(@.id == N)]".
    private ForoDTO buscarUnico(ForoDTO[] foros,
                                Predicate<ForoDTO> filtro,
                                String descripcionBusqueda) {
        List<ForoDTO> coincidencias = Arrays.stream(foros).filter(filtro).toList();

        assertThat(coincidencias)
                .withFailMessage("Se esperaba exactamente 1 foro con %s, pero hubo %d",
                        descripcionBusqueda, coincidencias.size())
                .hasSize(1);

        return coincidencias.get(0);
    }
}
