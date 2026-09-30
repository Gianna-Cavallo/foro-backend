package com.foro.backend.services;

import com.foro.backend.dto.CrearForoDTO;
import com.foro.backend.dto.ForoDTO;
import com.foro.backend.models.Foro;
import com.foro.backend.repositories.ForoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Tests UNITARIOS de ForosServiceImpl.
//
// @ExtendWith(MockitoExtension.class) activa Mockito en JUnit 5. NO se levanta
// el contexto de Spring: no hay base de datos, ni H2, ni DataSeeder. Solo se
// prueba la lógica de la clase, con el repositorio reemplazado por un mock.
//
// Esto es lo que hace que estos tests sean "unitarios": la única pieza real
// bajo test es ForosServiceImpl; todo lo que la rodea está simulado.
@ExtendWith(MockitoExtension.class)
class ForosServiceImplTest {

    // @Mock crea un doble de prueba del repositorio. No toca la BD:
    // devuelve lo que nosotros le indiquemos con when(...).
    @Mock
    private ForoRepository foroRepository;

    // @InjectMocks instancia ForosServiceImpl y le pasa el mock de arriba
    // por el constructor (inyección de dependencias, pero a mano en el test).
    @InjectMocks
    private ForosServiceImpl forosService;

    // Timestamps fijos para que las aserciones sean deterministas.
    private static final LocalDateTime CREADO = LocalDateTime.of(2026, 1, 15, 10, 0);
    private static final LocalDateTime ACTUALIZADO = LocalDateTime.of(2026, 2, 20, 18, 30);

    // ── listarTodos ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("listarTodos: mapea cada entidad Foro a su ForoDTO conservando todos los campos")
    void listarTodos_mapeaLasEntidadesADTO() {
        // ARRANGE: le decimos al mock qué devolver cuando el service llame findAll()
        Foro humanidades = construirForo(1L, "Humanidades",
                "Carreras de letras, historia, filosofía y educación.", "humanidades");
        Foro teologia = construirForo(2L, "Teología",
                "Estudios bíblicos, teológicos y pastorales.", "teologia");

        when(foroRepository.findAll()).thenReturn(List.of(humanidades, teologia));

        // ACT
        List<ForoDTO> resultado = forosService.listarTodos();

        // ASSERT: mismo tamaño, mismo orden y todos los campos mapeados
        assertThat(resultado).hasSize(2);

        ForoDTO primero = resultado.get(0);
        assertThat(primero.getId()).isEqualTo(1L);
        assertThat(primero.getNombre()).isEqualTo("Humanidades");
        assertThat(primero.getDescripcion())
                .isEqualTo("Carreras de letras, historia, filosofía y educación.");
        assertThat(primero.getFacultad()).isEqualTo("humanidades");
        assertThat(primero.getCreatedAt()).isEqualTo(CREADO);
        assertThat(primero.getUpdatedAt()).isEqualTo(ACTUALIZADO);

        ForoDTO segundo = resultado.get(1);
        assertThat(segundo.getId()).isEqualTo(2L);
        assertThat(segundo.getNombre()).isEqualTo("Teología");
        assertThat(segundo.getFacultad()).isEqualTo("teologia");

        // El service sólo debe leer: no puede escribir nada al listar.
        verify(foroRepository).findAll();
        verify(foroRepository, never()).save(any());
    }

    @Test
    @DisplayName("listarTodos: devuelve lista vacía cuando el repositorio no tiene foros")
    void listarTodos_sinForos_devuelveListaVacia() {
        when(foroRepository.findAll()).thenReturn(List.of());

        List<ForoDTO> resultado = forosService.listarTodos();

        // Vacía, pero NUNCA null — el Controller la serializa como []
        assertThat(resultado).isNotNull().isEmpty();
    }

    // ── crear ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("crear: persiste los datos del DTO de entrada y devuelve el DTO con el id generado")
    void crear_guardaElForoYDevuelveElDTO() {
        // ARRANGE: DTO que llegaría desde el Controller
        CrearForoDTO entrada = new CrearForoDTO();
        entrada.setNombre("Ciencias Económicas");
        entrada.setDescripcion("Administración, contabilidad, comercio y economía.");
        entrada.setFacultad("economicas");

        // save() en la vida real devuelve la entidad YA persistida: con id y
        // timestamps asignados por la BD / Hibernate. Simulamos eso.
        Foro guardado = construirForo(99L, "Ciencias Económicas",
                "Administración, contabilidad, comercio y economía.", "economicas");
        when(foroRepository.save(any(Foro.class))).thenReturn(guardado);

        // ACT
        ForoDTO resultado = forosService.crear(entrada);

        // ASSERT 1: el DTO devuelto refleja lo que quedó persistido, con el id
        assertThat(resultado.getId()).isEqualTo(99L);
        assertThat(resultado.getNombre()).isEqualTo("Ciencias Económicas");
        assertThat(resultado.getDescripcion())
                .isEqualTo("Administración, contabilidad, comercio y economía.");
        assertThat(resultado.getFacultad()).isEqualTo("economicas");
        assertThat(resultado.getCreatedAt()).isEqualTo(CREADO);
        assertThat(resultado.getUpdatedAt()).isEqualTo(ACTUALIZADO);

        // ASSERT 2: además de mirar lo que salió, verificamos lo que ENTRÓ a save().
        // El ArgumentCaptor "captura" la entidad que el service le pasó al mock,
        // así comprobamos que el mapeo DTO → entidad es correcto.
        ArgumentCaptor<Foro> capturado = ArgumentCaptor.forClass(Foro.class);
        verify(foroRepository).save(capturado.capture());

        Foro enviadoARepositorio = capturado.getValue();
        assertThat(enviadoARepositorio.getNombre()).isEqualTo("Ciencias Económicas");
        assertThat(enviadoARepositorio.getDescripcion())
                .isEqualTo("Administración, contabilidad, comercio y economía.");
        assertThat(enviadoARepositorio.getFacultad()).isEqualTo("economicas");
        // El id lo genera la BD: el service nunca lo setea a mano
        assertThat(enviadoARepositorio.getId()).isNull();
    }

    @Test
    @DisplayName("crear: la descripción es opcional y viaja como null sin romper el mapeo")
    void crear_sinDescripcion_guardaNull() {
        CrearForoDTO entrada = new CrearForoDTO();
        entrada.setNombre("Comunidad UAP");
        entrada.setFacultad("general");
        // descripcion queda en null a propósito: CrearForoDTO no la marca @NotBlank

        Foro guardado = construirForo(7L, "Comunidad UAP", null, "general");
        when(foroRepository.save(any(Foro.class))).thenReturn(guardado);

        ForoDTO resultado = forosService.crear(entrada);

        assertThat(resultado.getId()).isEqualTo(7L);
        assertThat(resultado.getDescripcion()).isNull();

        ArgumentCaptor<Foro> capturado = ArgumentCaptor.forClass(Foro.class);
        verify(foroRepository).save(capturado.capture());
        assertThat(capturado.getValue().getDescripcion()).isNull();
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
