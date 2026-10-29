package com.pluscontable.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluscontable.auth.config.JwtProvider;
import com.pluscontable.auth.dto.LoginRequest;
import com.pluscontable.auth.dto.LoginResponse;
import com.pluscontable.auth.model.BitacoraSeguridad;
import com.pluscontable.auth.model.Rol;
import com.pluscontable.auth.model.Usuario;
import com.pluscontable.auth.repository.BitacoraRepository;
import com.pluscontable.auth.repository.UsuarioRepository;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

/** HU-36 / HT-03: login con credenciales correctas e incorrectas, bloqueo por intentos fallidos y JWT emitido. */
class AuthServiceTest {

    private static final int MAX_INTENTOS = 5;
    private static final int MINUTOS_BLOQUEO = 15;
    private static final String MENSAJE_GENERICO = "Usuario o contraseña incorrectos";

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final BitacoraRepository bitacora = mock(BitacoraRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final JwtProvider jwt = new JwtProvider("secreto-de-prueba-con-al-menos-32-bytes-ok", 8);
    private AuthService service;
    private Usuario ana;

    @BeforeEach
    void setUp() {
        service = new AuthService(usuarios, bitacora, encoder, jwt, MAX_INTENTOS, MINUTOS_BLOQUEO);
        Rol contador = new Rol();
        contador.setNombre("CONTADOR");
        ana = new Usuario();
        ana.setUsername("ana");
        ana.setPasswordHash(encoder.encode("Clave123*"));
        ana.setNombreCompleto("Ana Pérez");
        ana.setRoles(Set.of(contador));
        when(usuarios.findByUsername("ana")).thenReturn(Optional.of(ana));
    }

    private LoginResponse entrar(String clave) {
        return service.login(new LoginRequest("ana", clave), "10.0.0.1");
    }

    private ResponseStatusException falla(String clave) {
        return (ResponseStatusException) org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class,
                () -> entrar(clave));
    }

    private List<String> eventosRegistrados() {
        ArgumentCaptor<BitacoraSeguridad> c = ArgumentCaptor.forClass(BitacoraSeguridad.class);
        verify(bitacora, atLeastOnce()).save(c.capture());
        return c.getAllValues().stream().map(BitacoraSeguridad::getEvento).toList();
    }

    @Test
    void credencialesCorrectasEmitenUnTokenConElRolDelUsuario() {
        LoginResponse r = entrar("Clave123*");

        assertThat(r.tipo()).isEqualTo("Bearer");
        assertThat(r.expiraEnSegundos()).isEqualTo(Duration.ofHours(8).toSeconds());
        assertThat(r.usuario().username()).isEqualTo("ana");
        assertThat(r.usuario().roles()).containsExactly("CONTADOR");
        Claims claims = jwt.parse(r.token());
        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo("ana");
        assertThat(claims.get("roles", List.class)).containsExactly("CONTADOR");
        assertThat(eventosRegistrados()).containsExactly("LOGIN_EXITOSO");
    }

    @Test
    void contrasenaIncorrectaSeRechazaSinRevelarQueDatoFallo() {
        ResponseStatusException e = falla("mala");

        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(e.getReason()).isEqualTo(MENSAJE_GENERICO);
        assertThat(ana.getIntentosFallidos()).isEqualTo(1);
        assertThat(eventosRegistrados()).containsExactly("LOGIN_FALLIDO");
    }

    @Test
    void usuarioInexistenteDaElMismoMensajeQueContrasenaIncorrecta() {
        when(usuarios.findByUsername("nadie")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("nadie", "x"), "10.0.0.1"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(e.getReason()).isEqualTo(falla("mala").getReason());
                });
    }

    @Test
    void elQuintoIntentoFallidoBloqueaLaCuentaPorElTiempoConfigurado() {
        ana.setIntentosFallidos(MAX_INTENTOS - 1);

        ResponseStatusException e = falla("mala");

        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
        assertThat(ana.getBloqueadoHasta()).isAfter(LocalDateTime.now().plusMinutes(MINUTOS_BLOQUEO - 1))
                .isBefore(LocalDateTime.now().plusMinutes(MINUTOS_BLOQUEO + 1));
        assertThat(ana.getIntentosFallidos()).isZero();
        assertThat(eventosRegistrados()).containsExactly("CUENTA_BLOQUEADA");
    }

    @Test
    void losIntentosAnterioresAlLimiteNoBloquean() {
        for (int i = 1; i < MAX_INTENTOS; i++) {
            assertThat(falla("mala").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        assertThat(ana.getIntentosFallidos()).isEqualTo(MAX_INTENTOS - 1);
        assertThat(ana.getBloqueadoHasta()).isNull();
    }

    @Test
    void cuentaBloqueadaRechazaIncluidaLaContrasenaCorrecta() {
        ana.setBloqueadoHasta(LocalDateTime.now().plusMinutes(10));

        ResponseStatusException e = falla("Clave123*");

        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
        assertThat(e.getReason()).contains("bloqueada");
        assertThat(eventosRegistrados()).containsExactly("LOGIN_BLOQUEADO");
    }

    @Test
    void cuandoVenceElBloqueoSePuedeEntrarYSeReiniciaElContador() {
        ana.setBloqueadoHasta(LocalDateTime.now().minusMinutes(1));
        ana.setIntentosFallidos(3);

        LoginResponse r = entrar("Clave123*");

        assertThat(r.token()).isNotBlank();
        assertThat(ana.getBloqueadoHasta()).isNull();
        assertThat(ana.getIntentosFallidos()).isZero();
    }

    @Test
    void unLoginExitosoReiniciaLosIntentosFallidos() {
        ana.setIntentosFallidos(3);

        entrar("Clave123*");

        assertThat(ana.getIntentosFallidos()).isZero();
        verify(usuarios).save(any(Usuario.class));
    }

    @Test
    void usuarioInactivoNoEntra() {
        ana.setActivo(false);

        ResponseStatusException e = falla("Clave123*");

        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(eventosRegistrados()).containsExactly("LOGIN_RECHAZADO");
    }

    @Test
    void meDevuelveElUsuarioConSusRoles() {
        assertThat(service.me("ana").roles()).containsExactly("CONTADOR");
    }

    @Test
    void meDeUnUsuarioInexistenteDa404() {
        when(usuarios.findByUsername("fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.me("fantasma"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
