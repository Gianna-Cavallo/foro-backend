package com.foro.backend.controllers;

import com.foro.backend.config.SecurityConfig;
import com.foro.backend.models.StudentUser;
import com.foro.backend.repositories.UserRepository;
import com.foro.backend.services.AuthServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Test de INTEGRACIÓN de la capa web para POST /api/auth/login.
//
// @WebMvcTest levanta solo la parte web (Controller, Security, JSON).
// A diferencia del test de foros, acá el Service NO se mockea: se usa el
// AuthServiceImpl real, así la request recorre Controller → Service → Repository.
// Lo único simulado es el UserRepository (la base de datos).
//
// @Import trae lo que @WebMvcTest no carga solo:
// - SecurityConfig: las reglas de seguridad reales (login público, CSRF apagado)
//   y el PasswordEncoder (BCrypt) que usa el Service.
// - AuthServiceImpl: la implementación que necesita el Controller.
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, AuthServiceImpl.class})
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // El encoder real de SecurityConfig: lo usamos para generar un hash
    // válido de la password, igual que si estuviera guardado en la BD.
    @Autowired
    private PasswordEncoder passwordEncoder;

    // Reemplaza la base de datos: devuelve lo que le indiquemos con when(...)
    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("POST /api/auth/login con credenciales válidas devuelve 200 y los datos del usuario sin passwordHash")
    void login_credencialesValidas_devuelve200SinPasswordHash() throws Exception {
        // ARRANGE: en la "BD" existe un usuario con la password "password123" hasheada
        StudentUser user = new StudentUser();
        user.setId(1L);
        user.setEmail("malena@uap.edu.ar");
        user.setUsername("malena");
        user.setRole("user");
        user.setPasswordHash(passwordEncoder.encode("password123"));
        when(userRepository.findByEmail("malena@uap.edu.ar")).thenReturn(Optional.of(user));

        // ACT + ASSERT: el POST va sin token CSRF, como lo mandaría el BFF
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"malena@uap.edu.ar\", \"password\": \"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("malena@uap.edu.ar"))
                .andExpect(jsonPath("$.username").value("malena"))
                .andExpect(jsonPath("$.role").value("user"))
                // Lo más importante: el hash de la password nunca sale en la respuesta
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/auth/login con password incorrecta devuelve 401 'Credenciales inválidas'")
    void login_passwordIncorrecta_devuelve401() throws Exception {
        // ARRANGE: el usuario existe, pero su password real es otra
        StudentUser user = new StudentUser();
        user.setEmail("malena@uap.edu.ar");
        user.setPasswordHash(passwordEncoder.encode("password123"));
        when(userRepository.findByEmail("malena@uap.edu.ar")).thenReturn(Optional.of(user));

        // ACT + ASSERT
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"malena@uap.edu.ar\", \"password\": \"incorrecta\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Credenciales inválidas"));
    }
}
