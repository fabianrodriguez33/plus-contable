package com.pluscontable.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluscontable.auth.config.JwtProvider;
import com.pluscontable.auth.config.SesionToken;
import com.pluscontable.auth.dto.LoginRequest;
import com.pluscontable.auth.dto.LoginResponse;
import com.pluscontable.auth.model.BitacoraSeguridad;
import com.pluscontable.auth.model.Rol;
import com.pluscontable.auth.model.TokenRevocado;
import com.pluscontable.auth.model.Usuario;
import com.pluscontable.auth.repository.BitacoraRepository;
import com.pluscontable.auth.repository.TokenRevocadoRepository;
import com.pluscontable.auth.repository.UsuarioRepository;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

/**
 * HU-37 (tareas 15 y 16): primer ingreso de un cliente con la contraseña temporal (un solo uso, token restringido) y cambio
 * obligatorio de la contraseña.
 */
class PrimerIngresoServiceTest {

    private static final String CORREO = "contacto@acme.pe";
    private static final String TEMPORAL = "Temporal2345";
    private static final String NUEVA = "MiClaveNueva9";

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final BitacoraRepository bitacora = mock(BitacoraRepository.class);
    private final TokenRevocadoRepository revocados = mock(TokenRevocadoRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final JwtProvider jwt = new JwtProvider("secreto-de-prueba-con-al-menos-32-bytes-ok", 8);
    private AuthService service;
    private Usuario cliente;

    @BeforeEach
    void preparar() {
        service = new AuthService(usuarios, bitacora, revocados, encoder, jwt, 5, 15);
        Rol rol = new Rol();
        rol.setNombre("CLIENTE_MYPE");
        cliente = new Usuario();
        cliente.setUsername(CORREO);
        cliente.setPasswordHash(encoder.encode(TEMPORAL));
        cliente.setNombreCompleto("Acme SAC");
        cliente.setClienteId(7L);
        cliente.setRoles(Set.of(rol));
        cliente.setDebeCambiarPassword(true);
        when(usuarios.findByUsername(CORREO)).thenReturn(Optional.of(cliente));
    }

    private LoginResponse entrar(String clave) {
        return service.login(new LoginRequest(CORREO, clave), "10.0.0.2");
    }

    private ResponseStatusException falla(Runnable accion) {
        return (ResponseStatusException) org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class, accion::run);
    }

    private SesionToken restringida() {
        return new SesionToken("jti-restringido", Instant.parse("2026-11-10T20:00:00Z"), true);
    }

    private List<BitacoraSeguridad> bitacoraGuardada() {
        ArgumentCaptor<BitacoraSeguridad> c = ArgumentCaptor.forClass(BitacoraSeguridad.class);
        verify(bitacora, org.mockito.Mockito.atLeast(0)).save(c.capture());
        return c.getAllValues();
    }

    // ---- primer ingreso con la temporal

    @Test
    void elPrimerIngresoConLaTemporalEntregaUnTokenRestringidoSinRolesYAvisaQueDebeCambiarla() {
        LoginResponse r = entrar(TEMPORAL);

        assertThat(r.debeCambiarPassword()).isTrue();
        Claims c = jwt.parse(r.token());
        assertThat(c.getSubject()).isEqualTo(CORREO);
        assertThat(c.get("roles", List.class)).isEmpty();
        assertThat(c.get("cambiarPassword", Boolean.class)).isTrue();
        assertThat(c.get("clienteId")).isNull();
        assertThat(c.getId()).isNotBlank();
        assertThat(r.usuario().roles()).containsExactly("CLIENTE_MYPE");
    }

    @Test
    void elPrimerIngresoMarcaLaTemporalComoUsadaYLoAnota() {
        entrar(TEMPORAL);

        assertThat(cliente.getTemporalUsadaEn()).isNotNull();
        assertThat(cliente.isDebeCambiarPassword()).isTrue(); // sigue pendiente hasta que la cambie
        verify(usuarios).save(cliente);
        assertThat(bitacoraGuardada()).anySatisfy(b -> {
            assertThat(b.getEvento()).isEqualTo("LOGIN_EXITOSO");
            assertThat(b.getDetalle()).contains("debe cambiarla");
        });
    }

    @Test
    void laTemporalNoSirveUnaSegundaVezAunqueSeaLaCorrecta() {
        entrar(TEMPORAL);

        ResponseStatusException e = falla(() -> entrar(TEMPORAL));

        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(e.getReason()).contains("ya fue usada");
        assertThat(bitacoraGuardada()).anySatisfy(b -> {
            assertThat(b.getEvento()).isEqualTo("LOGIN_RECHAZADO");
            assertThat(b.getDetalle()).contains("temporal ya usada");
        });
    }

    @Test
    void unaContraseniaIncorrectaNoGastaLaTemporal() {
        falla(() -> entrar("OtraCosa123"));

        assertThat(cliente.getTemporalUsadaEn()).isNull();
        assertThat(entrar(TEMPORAL).debeCambiarPassword()).isTrue();
    }

    @Test
    void unaCuentaSinCambioPendienteEntraConSuTokenNormalConRolYEmpresa() {
        cliente.setDebeCambiarPassword(false);
        cliente.setPasswordHash(encoder.encode(NUEVA));

        LoginResponse r = entrar(NUEVA);

        assertThat(r.debeCambiarPassword()).isFalse();
        Claims c = jwt.parse(r.token());
        assertThat(c.get("roles", List.class)).containsExactly("CLIENTE_MYPE");
        assertThat(c.get("clienteId", Long.class)).isEqualTo(7L);
        assertThat(c.get("cambiarPassword")).isNull();
    }

    @Test
    void elCorreoEscritoConMayusculasTambienEntra() {
        when(usuarios.findByUsername("Contacto@Acme.PE")).thenReturn(Optional.empty());

        LoginResponse r = service.login(new LoginRequest("  Contacto@Acme.PE ", TEMPORAL), "10.0.0.2");

        assertThat(r.usuario().username()).isEqualTo(CORREO);
    }

    @Test
    void siElNombreYaEstaEnMinusculasNoSeConsultaDosVeces() {
        when(usuarios.findByUsername("nadie")).thenReturn(Optional.empty());

        falla(() -> service.login(new LoginRequest("nadie", "x"), "10.0.0.2"));

        verify(usuarios, times(1)).findByUsername("nadie");
    }

    // ---- cambio obligatorio

    @Test
    void cambiarLaContraseniaGuardaSoloElHashDeLaNuevaYLaTemporalDejaDeServir() {
        entrar(TEMPORAL);
        String hashAnterior = cliente.getPasswordHash();

        service.cambiarPassword(CORREO, restringida(), NUEVA, "10.0.0.2");

        assertThat(cliente.getPasswordHash()).isNotEqualTo(hashAnterior).isNotEqualTo(NUEVA).doesNotContain(NUEVA).startsWith("$2");
        assertThat(encoder.matches(NUEVA, cliente.getPasswordHash())).isTrue();
        assertThat(encoder.matches(TEMPORAL, cliente.getPasswordHash())).isFalse();
        assertThat(cliente.isDebeCambiarPassword()).isFalse();
        ResponseStatusException e = falla(() -> entrar(TEMPORAL));
        assertThat(e.getReason()).isEqualTo("Usuario o contraseña incorrectos");
    }

    @Test
    void cambiarLaContraseniaEntregaElTokenNormalConRolYEmpresaYRevocaElRestringido() {
        entrar(TEMPORAL);

        LoginResponse r = service.cambiarPassword(CORREO, restringida(), NUEVA, "10.0.0.2");

        assertThat(r.debeCambiarPassword()).isFalse();
        Claims c = jwt.parse(r.token());
        assertThat(c.get("roles", List.class)).containsExactly("CLIENTE_MYPE");
        assertThat(c.get("clienteId", Long.class)).isEqualTo(7L);
        assertThat(c.get("cambiarPassword")).isNull();
        ArgumentCaptor<TokenRevocado> t = ArgumentCaptor.forClass(TokenRevocado.class);
        verify(revocados).save(t.capture());
        assertThat(t.getValue().getJti()).isEqualTo("jti-restringido");
    }

    @Test
    void elCambioSeAnotaEnLaBitacoraSinGuardarNingunaContrasenia() {
        entrar(TEMPORAL);

        service.cambiarPassword(CORREO, restringida(), NUEVA, "10.0.0.2");

        List<BitacoraSeguridad> filas = bitacoraGuardada();
        assertThat(filas).anySatisfy(b -> {
            assertThat(b.getEvento()).isEqualTo("CAMBIO_PASSWORD");
            assertThat(b.getUsername()).isEqualTo(CORREO);
            assertThat(b.getIp()).isEqualTo("10.0.0.2");
        });
        for (BitacoraSeguridad b : filas) {
            String todo = String.join("|", String.valueOf(b.getEvento()), String.valueOf(b.getDetalle()),
                    String.valueOf(b.getUsername()), String.valueOf(b.getIp()));
            assertThat(todo).doesNotContain(NUEVA).doesNotContain(TEMPORAL);
        }
    }

    @Test
    void conUnTokenNormalNoSePuedeCambiarPorEsteCaminoNiConSesionNula() {
        ResponseStatusException normal = falla(() -> service.cambiarPassword(CORREO, new SesionToken("j", null, false), NUEVA, "ip"));
        ResponseStatusException nula = falla(() -> service.cambiarPassword(CORREO, null, NUEVA, "ip"));

        assertThat(normal.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(nula.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(cliente.isDebeCambiarPassword()).isTrue();
        verify(usuarios, never()).save(any());
    }

    @Test
    void siLaCuentaYaNoTieneCambioPendienteNoSePuedeUsarUnTokenRestringidoViejo() {
        cliente.setDebeCambiarPassword(false);
        String hash = cliente.getPasswordHash();

        ResponseStatusException e = falla(() -> service.cambiarPassword(CORREO, restringida(), NUEVA, "ip"));

        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(cliente.getPasswordHash()).isEqualTo(hash);
        verify(revocados, never()).save(any());
    }

    @Test
    void unUsuarioQueYaNoExisteDa401() {
        when(usuarios.findByUsername("fantasma@acme.pe")).thenReturn(Optional.empty());

        ResponseStatusException e = falla(() -> service.cambiarPassword("fantasma@acme.pe", restringida(), NUEVA, "ip"));

        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "    "})
    void sinContraseniaNuevaDa400(String nueva) {
        rechaza(nueva, "Faltan o son inválidos: passwordNueva");
    }

    @Test
    void conSieteCaracteresSeRechazaYConOchoSeAcepta() {
        rechaza("Abcdef1", "de 8 a 72 caracteres");
        service.cambiarPassword(CORREO, restringida(), "Abcdefg1", "ip");
        assertThat(cliente.isDebeCambiarPassword()).isFalse();
    }

    @Test
    void conSetentaYDosCaracteresSeAceptaYConSetentaYTresSeRechaza() {
        rechaza("A1".repeat(36) + "x", "de 8 a 72 caracteres");
        service.cambiarPassword(CORREO, restringida(), "A1".repeat(36), "ip");
        assertThat(encoder.matches("A1".repeat(36), cliente.getPasswordHash())).isTrue();
    }

    @Test
    void bcryptSoloUsaSetentaYDosBytesAsiQueUnTextoConAcentosMasLargoSeRechazaEnVezDeTruncarse() {
        String conAcentos = "ñ".repeat(37); // 37 caracteres pero 74 bytes
        rechaza(conAcentos, "de 8 a 72 caracteres");
    }

    @Test
    void noPuedeSerIgualAlCorreoNiAlaTemporal() {
        rechaza("CONTACTO@acme.pe", "no puede ser igual al correo");
        rechaza(TEMPORAL, "no puede ser la temporal");
    }

    private void rechaza(String nueva, String mensaje) {
        String hash = cliente.getPasswordHash();
        ResponseStatusException e = falla(() -> service.cambiarPassword(CORREO, restringida(), nueva, "ip"));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getReason()).contains(mensaje);
        assertThat(cliente.getPasswordHash()).isEqualTo(hash);
        assertThat(cliente.isDebeCambiarPassword()).isTrue();
        verify(revocados, never()).save(any());
    }
}
