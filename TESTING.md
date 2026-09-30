# Testing

## Requisitos

- JDK 17 o superior.
- **No se necesita MySQL corriendo** para los tests: el perfil `test` usa H2
  en memoria (`src/test/resources/application-test.properties`).
- Maven wrapper incluido (`./mvnw` / `mvnw.cmd` en Windows), no hace falta
  instalar Maven.

## Comandos

```bash
./mvnw test                          # corre toda la suite
./mvnw -Dtest=NombreClase test        # corre una sola clase de test
```

## Dónde ubicar cada tipo de test

- **Unitarios** (services, mapeos, lógica pura): en el mismo package que la
  clase bajo test dentro de `src/test/java/...`, usando Mockito
  (`@ExtendWith(MockitoExtension.class)` o `@MockitoBean`/`@Mock`) para
  mockear repositorios y dependencias. No necesitan levantar el contexto de
  Spring.
- **Integración de capa web** (controllers + Security + validación):
  `@WebMvcTest(TalController.class)`. Ver la sección "Notas" más abajo — hay
  que importar explícitamente lo que el controller necesita.
- **End-to-end** (contexto completo: JPA + Security + web):
  `@SpringBootTest` con `@ActiveProfiles("test")`, para que use H2 en vez de
  MySQL. `BackendApplicationTests` ya lo hace así.

## Notas para quien escriba tests (relevado al armar esta infraestructura)

### `DataSeeder` corre en todos los `@SpringBootTest`

`DataSeeder` (`src/main/java/com/foro/backend/config/DataSeeder.java`) es un
`CommandLineRunner` **sin `@Profile`**, así que se ejecuta siempre que se
levanta el contexto completo — incluyendo cualquier `@SpringBootTest` con
`@ActiveProfiles("test")`. Cada vez que se crea un contexto nuevo (H2 en
memoria arranca vacía), el seeder inserta:

- 5 usuarios: `admin@uap.edu.ar` (rol `admin`) + `gianna`, `malena`,
  `milena`, `jperez` `@uap.edu.ar` (rol `user`), todos con password
  `password123`.
- 6 foros: Humanidades, Ciencias Económicas, Teología, Ciencias de la Salud,
  Instituto Superior, Comunidad UAP (facultades `humanidades`,
  `economicas`, `teologia`, `salud`, `instituto`, `general`).

Implicancias para los tests `@SpringBootTest`:
- La base **no arranca vacía** — ya hay 5 usuarios y 6 foros antes de que
  corra el `@Test`. No asumir `count() == 0`.
- Si un test intenta insertar un usuario con alguno de los emails de arriba,
  va a chocar con la constraint `unique` de `email`/`username`.
- Si Spring reutiliza el mismo `ApplicationContext` entre clases de test
  (context caching), el seed corre una sola vez para ese contexto, no por
  cada `@Test`.

### `@WebMvcTest` de `AuthController` necesita imports explícitos

`@WebMvcTest(AuthController.class)` solo levanta el controller y la capa
web — no trae automáticamente los beans `@Service` ni `@Configuration`. Para
que el contexto levante y el login funcione de punta a punta hace falta:

```java
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, AuthServiceImpl.class})
class AlgunTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean
    UserRepository userRepository; // AuthServiceImpl lo necesita
}
```

- `@Import(SecurityConfig.class)`: sin esto, Spring Boot activa su propia
  configuración de seguridad default para el slice (usuario in-memory
  generado, CSRF habilitado), que no refleja el comportamiento real. Al
  importar el `SecurityConfig` real se usa el `SecurityFilterChain`
  verdadero (con `/api/auth/login` en `permitAll()` y CSRF deshabilitado).
- `@Import(AuthServiceImpl.class)`: `AuthController` depende de la interfaz
  `AuthService`; sin un bean concreto que la implemente, el contexto falla
  al arrancar (`UnsatisfiedDependencyException`).
- `@MockitoBean UserRepository`: mockea el repositorio real para no
  depender de una base de datos en un test de slice.
- **CSRF no bloquea el POST** — no hace falta `.with(csrf())`. Como se
  importa el `SecurityConfig` real (que deshabilita CSRF para toda la API),
  el filtro de CSRF de Spring Security nunca actúa. Esto se verificó
  corriendo el POST sin token y no dio 403.
- Con un body JSON válido pero sin stubear `userRepository.findByEmail(...)`
  (Mockito devuelve `Optional.empty()` por defecto), `AuthServiceImpl` lanza
  la excepción de credenciales inválidas y `AuthController` la traduce a
  **401 Unauthorized** con body `"Credenciales inválidas"`. Para probar el
  camino exitoso hay que stubear `userRepository.findByEmail(...)` con un
  `User` cuyo `passwordHash` coincida (vía el `PasswordEncoder` real que
  aporta `SecurityConfig`).
