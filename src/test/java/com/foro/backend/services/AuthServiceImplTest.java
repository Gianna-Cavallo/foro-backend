package com.foro.backend.services;

import com.foro.backend.dto.UserDTO;
import com.foro.backend.models.StudentUser;
import com.foro.backend.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Tests UNITARIOS de AuthServiceImpl.
//
// @ExtendWith(MockitoExtension.class) activa Mockito en JUnit 5. NO se levanta
// el contexto de Spring: no hay base de datos, ni H2. Solo se prueba la lógica
// de la clase, con el repositorio y el encoder reemplazados por mocks.
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    // @Mock crea un doble de prueba del repositorio. No toca la BD:
    // devuelve lo que nosotros le indiquemos con when(...).
    @Mock
    private UserRepository userRepository;

    // @Mock crea un doble de prueba del encoder de passwords.
    @Mock
    private PasswordEncoder passwordEncoder;

    // @InjectMocks instancia AuthServiceImpl y le pasa los mocks de arriba
    // por el constructor (inyección de dependencias, pero a mano en el test).
    @InjectMocks
    private AuthServiceImpl authService;

    private StudentUser testUser;

    @BeforeEach
    void setUp() {
        // User es abstracta: usamos StudentUser para tener una instancia concreta.
        testUser = new StudentUser();
        testUser.setId(1L);
        testUser.setEmail("estudiante@uap.edu.ar");
        testUser.setPasswordHash("hashGuardadoEnLaBD");
        testUser.setUsername("estudiante1");
        testUser.setRole("user");
    }

    // ── login ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login: con un email que no existe lanza RuntimeException con 'Credenciales inválidas'")
    void login_emailInexistente_lanzaExcepcion() {
        // ARRANGE: el repositorio no encuentra ningún usuario con ese email
        when(userRepository.findByEmail("noexiste@uap.edu.ar")).thenReturn(Optional.empty());

        // ACT & ASSERT: debe lanzar la excepción con el mensaje esperado
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> authService.login("noexiste@uap.edu.ar", "cualquierPassword"));
        assertEquals("Credenciales inválidas", exception.getMessage());

        // No tiene sentido comparar la password si el usuario ni siquiera existe
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("login: con la password incorrecta lanza RuntimeException con 'Credenciales inválidas'")
    void login_passwordIncorrecta_lanzaExcepcion() {
        // ARRANGE: el usuario existe, pero la password no coincide con el hash
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("passwordIncorrecta", testUser.getPasswordHash())).thenReturn(false);

        // ACT & ASSERT: debe lanzar la misma excepción, sin dar pistas de qué falló
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> authService.login(testUser.getEmail(), "passwordIncorrecta"));
        assertEquals("Credenciales inválidas", exception.getMessage());
    }

    @Test
    @DisplayName("login: con credenciales válidas devuelve el UserDTO con los datos del usuario")
    void login_credencialesValidas_devuelveUserDTO() {
        // ARRANGE: el usuario existe y la password coincide con el hash
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("passwordCorrecta", testUser.getPasswordHash())).thenReturn(true);

        // ACT
        UserDTO resultado = authService.login(testUser.getEmail(), "passwordCorrecta");

        // ASSERT: el DTO refleja los datos del usuario, sin exponer el passwordHash
        assertNotNull(resultado);
        assertEquals(testUser.getId(), resultado.getId());
        assertEquals(testUser.getEmail(), resultado.getEmail());
        assertEquals(testUser.getUsername(), resultado.getUsername());
        assertEquals(testUser.getRole(), resultado.getRole());

        verify(userRepository, times(1)).findByEmail(testUser.getEmail());
        verify(passwordEncoder, times(1)).matches("passwordCorrecta", testUser.getPasswordHash());
    }
}
