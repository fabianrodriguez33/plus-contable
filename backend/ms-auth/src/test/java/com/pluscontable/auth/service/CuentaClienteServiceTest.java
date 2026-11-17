package com.pluscontable.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluscontable.auth.dto.CuentaClienteResponse;
import com.pluscontable.auth.model.BitacoraSeguridad;
import com.pluscontable.auth.model.Rol;
import com.pluscontable.auth.model.Usuario;
import com.pluscontable.auth.repository.BitacoraRepository;
import com.pluscontable.auth.repository.RolRepository;
import com.pluscontable.auth.repository.UsuarioRepository;
import com.pluscontable.auth.service.ClienteTributarioClient.ClienteResumen;
import java.time.LocalDateTime;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** HU-37 (tareas 14, 15 y 16): crear la cuenta de un cliente MYPE (cuenta nueva, repetida y cliente no registrado). */
class CuentaClienteServiceTest {

    private static final long CLIENTE = 7L;
    private static final String TEMPORAL = "Temporal2345";
    private static final String TOKEN = "Bearer token-del-contador";

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final RolRepository roles = mock(RolRepository.class);
    private final BitacoraRepository bitacora = mock(BitacoraRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final ClienteTributarioClient tributario = mock(ClienteTributarioClient.class);
    private final GeneradorPassword generador = mock(GeneradorPassword.class);
    private final RestablecimientoService restablecimientos = mock(RestablecimientoService.class);
    private final TransaccionDePrueba tm = new TransaccionDePrueba();
    private final CuentaClienteService service =
            new CuentaClienteService(usuarios, roles, bitacora, encoder, tributario, generador, 48, new TransactionTemplate(tm), restablecimientos);
    private boolean consultaDentroDeTransaccion = true;
    private boolean guardoDentroDeTransaccion = false;

    /** Administrador de transacciones que solo registra si hay una abierta y si se confirmó o se deshizo. */
    static class TransaccionDePrueba implements PlatformTransactionManager {
        boolean activa;
        int confirmadas;
        int deshechas;

        @Override public TransactionStatus getTransaction(TransactionDefinition d) { activa = true; return new SimpleTransactionStatus(); }
        @Override public void commit(TransactionStatus s) { activa = false; confirmadas++; }
        @Override public void rollback(TransactionStatus s) { activa = false; deshechas++; }
    }

    @BeforeEach
    void preparar() {
        Rol rol = new Rol();
        rol.setNombre("CLIENTE_MYPE");
        when(roles.findByNombre("CLIENTE_MYPE")).thenReturn(Optional.of(rol));
        when(tributario.buscar(CLIENTE, TOKEN)).thenAnswer(inv -> {
            consultaDentroDeTransaccion = tm.activa;
            return Optional.of(new ClienteResumen(CLIENTE, "Acme SAC"));
        });
        when(generador.generar()).thenReturn(TEMPORAL);
        when(usuarios.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            guardoDentroDeTransaccion = tm.activa;
            ReflectionTestUtils.setField(u, "id", 9L);
            return u;
        });
    }

    private CuentaClienteResponse crear(String correo) {
        return service.crear(CLIENTE, correo, TOKEN, "ana", "10.0.0.1");
    }

    private Usuario guardado() {
        ArgumentCaptor<Usuario> c = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(c.capture());
        return c.getValue();
    }

    private void noCrea(String correo, HttpStatus estado, String mensaje) {
        assertThatThrownBy(() -> crear(correo))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(estado);
                    assertThat(e.getReason()).contains(mensaje);
                });
        verify(usuarios, never()).save(any());
        verify(bitacora, never()).save(any());
    }

    @Test
    void creaLaCuentaDelClienteConRolCorreoEmpresaYCambioPendiente() {
        CuentaClienteResponse r = crear("contacto@acme.pe");

        Usuario u = guardado();
        assertThat(u.getUsername()).isEqualTo("contacto@acme.pe");
        assertThat(u.getEmail()).isEqualTo("contacto@acme.pe");
        assertThat(u.getClienteId()).isEqualTo(CLIENTE);
        assertThat(u.getRoles()).extracting(Rol::getNombre).containsExactly("CLIENTE_MYPE");
        assertThat(u.isDebeCambiarPassword()).isTrue();
        assertThat(u.getTemporalUsadaEn()).isNull();
        assertThat(u.isActivo()).isTrue();
        assertThat(u.getNombreCompleto()).isEqualTo("Acme SAC");
        assertThat(r.usuarioId()).isEqualTo(9L);
        assertThat(r.correo()).isEqualTo("contacto@acme.pe");
        assertThat(r.clienteId()).isEqualTo(CLIENTE);
        assertThat(r.passwordTemporal()).isEqualTo(TEMPORAL);
    }

    // ---- restablecer el acceso (decisión de Beto del 11/11)

    private static final String ANTERIOR = "Anterior-prueba-1";

    /** La cuenta que ya existe: el cliente ya cambió su contraseña, la temporal anterior se gastó y venció. */
    private Usuario cuentaExistente() {
        Rol rol = new Rol();
        rol.setNombre("CLIENTE_MYPE");
        Usuario u = new Usuario();
        ReflectionTestUtils.setField(u, "id", 9L);
        u.setUsername("contacto@acme.pe");
        u.setEmail("contacto@acme.pe");
        u.setNombreCompleto("Acme SAC");
        u.setClienteId(CLIENTE);
        u.setPasswordHash(encoder.encode(ANTERIOR));
        u.setDebeCambiarPassword(false);
        u.setTemporalUsadaEn(LocalDateTime.now().minusDays(3));
        u.setTemporalVenceEn(LocalDateTime.now().minusDays(1));
        u.setSesionVersion(2);
        u.setIntentosFallidos(3);
        u.setRoles(new java.util.HashSet<>(java.util.Set.of(rol)));
        when(usuarios.findByClienteId(CLIENTE)).thenReturn(Optional.of(u));
        return u;
    }

    private CuentaClienteResponse restablecer() {
        return service.restablecer(CLIENTE, "ana", "10.0.0.1");
    }

    @Test
    void restablecerEntregaOtraTemporalSoloEnLaRespuesta() {
        cuentaExistente();

        CuentaClienteResponse r = restablecer();

        assertThat(r.passwordTemporal()).isEqualTo(TEMPORAL);
        assertThat(r.usuarioId()).isEqualTo(9L);
        assertThat(r.correo()).isEqualTo("contacto@acme.pe");
        assertThat(r.clienteId()).isEqualTo(CLIENTE);
        assertThat(r.venceEn()).isNotNull();
    }

    @Test
    void restablecerDejaAtendidaLaSolicitudDelClienteHu41() {
        cuentaExistente();

        restablecer();

        verify(restablecimientos).atender(9L, "ana");
    }

    @Test
    void restablecerGuardaSoloElHashDeLaNuevaYLaAnteriorDejaDeServir() {
        Usuario u = cuentaExistente();

        restablecer();

        assertThat(u.getPasswordHash()).isNotEqualTo(TEMPORAL).doesNotContain(TEMPORAL).startsWith("$2");
        assertThat(encoder.matches(TEMPORAL, u.getPasswordHash())).isTrue();
        assertThat(encoder.matches(ANTERIOR, u.getPasswordHash())).isFalse();
    }

    @Test
    void restablecerReiniciaLas48HorasYVuelveAExigirElCambio() {
        Usuario u = cuentaExistente();
        LocalDateTime antes = LocalDateTime.now();

        CuentaClienteResponse r = restablecer();

        assertThat(u.getTemporalVenceEn()).isBetween(antes.plusHours(48), LocalDateTime.now().plusHours(48));
        assertThat(r.venceEn()).isEqualTo(u.getTemporalVenceEn());
        assertThat(u.isDebeCambiarPassword()).isTrue();
        assertThat(u.getTemporalUsadaEn()).isNull();
    }

    @Test
    void restablecerCierraLasSesionesActivasSubiendoLaVersion() {
        Usuario u = cuentaExistente();

        restablecer();
        assertThat(u.getSesionVersion()).isEqualTo(3);
        restablecer();
        assertThat(u.getSesionVersion()).isEqualTo(4);
    }

    @Test
    void restablecerGuardaLaCuentaYNoTocaSusOtrosDatos() {
        Usuario u = cuentaExistente();

        restablecer();

        verify(usuarios).save(u);
        assertThat(u.getUsername()).isEqualTo("contacto@acme.pe");
        assertThat(u.getEmail()).isEqualTo("contacto@acme.pe");
        assertThat(u.getClienteId()).isEqualTo(CLIENTE);
        assertThat(u.getNombreCompleto()).isEqualTo("Acme SAC");
        assertThat(u.isActivo()).isTrue();
        assertThat(u.getRoles()).extracting(Rol::getNombre).containsExactly("CLIENTE_MYPE");
        assertThat(u.getIntentosFallidos()).isEqualTo(3);
    }

    @Test
    void restablecerFuncionaTambienConLaTemporalAunSinUsarYAunVigente() {
        Usuario u = cuentaExistente();
        u.setDebeCambiarPassword(true);
        u.setTemporalUsadaEn(null);
        u.setTemporalVenceEn(LocalDateTime.now().plusHours(1));

        restablecer();

        assertThat(encoder.matches(TEMPORAL, u.getPasswordHash())).isTrue();
        assertThat(u.getTemporalVenceEn()).isAfter(LocalDateTime.now().plusHours(47));
    }

    @Test
    void restablecerLoAnotaEnLaBitacoraSinLaContrasenia() {
        Usuario u = cuentaExistente();
        when(usuarios.findByUsername("ana")).thenReturn(Optional.of(quien(5L)));

        restablecer();

        ArgumentCaptor<BitacoraSeguridad> c = ArgumentCaptor.forClass(BitacoraSeguridad.class);
        verify(bitacora).save(c.capture());
        BitacoraSeguridad b = c.getValue();
        assertThat(b.getEvento()).isEqualTo("CUENTA_CLIENTE_RESTABLECIDA");
        assertThat(b.getUsername()).isEqualTo("ana");
        assertThat(b.getUsuarioId()).isEqualTo(5L);
        assertThat(b.getIp()).isEqualTo("10.0.0.1");
        assertThat(b.getDetalle()).contains("contacto@acme.pe").contains("cliente " + CLIENTE)
                .doesNotContain(TEMPORAL).doesNotContain(ANTERIOR).doesNotContain(u.getPasswordHash());
    }

    private Usuario quien(long id) {
        Usuario q = new Usuario();
        ReflectionTestUtils.setField(q, "id", id);
        q.setUsername("ana");
        return q;
    }

    @Test
    void restablecerSinCuentaDa404YNoGeneraNiGuardaNada() {
        when(usuarios.findByClienteId(CLIENTE)).thenReturn(Optional.empty());

        assertThatThrownBy(this::restablecer).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(e.getReason()).isEqualTo("El cliente no tiene una cuenta de acceso");
        });
        verify(generador, never()).generar();
        verify(usuarios, never()).save(any());
        verify(bitacora, never()).save(any());
    }

    @Test
    void laVigenciaDelRestablecimientoSeConfigura() {
        Usuario u = cuentaExistente();
        CuentaClienteService de72 = new CuentaClienteService(usuarios, roles, bitacora, encoder, tributario, generador, 72, new TransactionTemplate(tm), restablecimientos);
        LocalDateTime antes = LocalDateTime.now();

        de72.restablecer(CLIENTE, "ana", "10.0.0.1");

        assertThat(u.getTemporalVenceEn()).isBetween(antes.plusHours(72), LocalDateTime.now().plusHours(72));
    }

    @Test
    void restablecerGuardaDentroDeUnaTransaccionYSiFallaSeDeshace() {
        cuentaExistente();
        restablecer();
        assertThat(guardoDentroDeTransaccion).isTrue();
        assertThat(tm.confirmadas).isEqualTo(1);

        cuentaExistente();
        when(bitacora.save(any())).thenThrow(new IllegalStateException("la base se cayó"));
        assertThatThrownBy(this::restablecer).isInstanceOf(IllegalStateException.class);
        assertThat(tm.deshechas).isEqualTo(1);
    }

    @Test
    void laConsultaAMsTributarioSeHaceFueraDeLaTransaccionYElGuardadoDentro() {
        crear("contacto@acme.pe");

        assertThat(consultaDentroDeTransaccion).isFalse();
        assertThat(guardoDentroDeTransaccion).isTrue();
        assertThat(tm.confirmadas).isEqualTo(1);
        assertThat(tm.deshechas).isZero();
    }

    @Test
    void siFallaElGuardadoLaTransaccionSeDeshace() {
        when(bitacora.save(any())).thenThrow(new IllegalStateException("la base se cayó"));

        assertThatThrownBy(() -> crear("contacto@acme.pe")).isInstanceOf(IllegalStateException.class);
        assertThat(tm.deshechas).isEqualTo(1);
        assertThat(tm.confirmadas).isZero();
    }

    @Test
    void siElClienteNoExisteNoSeAbreNingunaTransaccion() {
        when(tributario.buscar(CLIENTE, TOKEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crear("contacto@acme.pe")).isInstanceOf(ResponseStatusException.class);
        assertThat(tm.confirmadas + tm.deshechas).isZero();
    }

    @Test
    void laTemporalVenceALas48HorasPorDefecto() {
        LocalDateTime antes = LocalDateTime.now();
        crear("contacto@acme.pe");

        LocalDateTime vence = guardado().getTemporalVenceEn();
        assertThat(vence).isNotNull().isBetween(antes.plusHours(48), LocalDateTime.now().plusHours(48));
    }

    @Test
    void laVigenciaDeLaTemporalSeConfigura() {
        CuentaClienteService de72 = new CuentaClienteService(usuarios, roles, bitacora, encoder, tributario, generador, 72, new TransactionTemplate(tm), restablecimientos);
        LocalDateTime antes = LocalDateTime.now();
        de72.crear(CLIENTE, "contacto@acme.pe", TOKEN, "ana", "10.0.0.1");

        assertThat(guardado().getTemporalVenceEn()).isBetween(antes.plusHours(72), LocalDateTime.now().plusHours(72));
    }

    @Test
    void laContraseniaTemporalSeGuardaSoloComoHashNuncaEnClaro() {
        crear("contacto@acme.pe");

        Usuario u = guardado();
        assertThat(u.getPasswordHash()).isNotEqualTo(TEMPORAL).doesNotContain(TEMPORAL).startsWith("$2");
        assertThat(encoder.matches(TEMPORAL, u.getPasswordHash())).isTrue();
        assertThat(u.getUsername()).doesNotContain(TEMPORAL);
        assertThat(u.getEmail()).doesNotContain(TEMPORAL);
        assertThat(u.getNombreCompleto()).doesNotContain(TEMPORAL);
    }

    @Test
    void laBitacoraAnotaLaCreacionConQuienLaHizoYNuncaLaContrasenia() {
        crear("contacto@acme.pe");

        ArgumentCaptor<BitacoraSeguridad> c = ArgumentCaptor.forClass(BitacoraSeguridad.class);
        verify(bitacora).save(c.capture());
        BitacoraSeguridad b = c.getValue();
        assertThat(b.getEvento()).isEqualTo("CUENTA_CLIENTE_CREADA");
        assertThat(b.getUsername()).isEqualTo("ana");
        assertThat(b.getIp()).isEqualTo("10.0.0.1");
        assertThat(b.getDetalle()).contains("contacto@acme.pe").contains("cliente 7").doesNotContain(TEMPORAL);
        assertThat(String.join("|", b.getEvento(), b.getDetalle(), b.getUsername(), b.getIp())).doesNotContain(TEMPORAL);
    }

    @Test
    void elCorreoSeNormalizaEnMinusculasYSinEspacios() {
        CuentaClienteResponse r = crear("  Ana.Perez@Empresa.PE ");

        assertThat(guardado().getUsername()).isEqualTo("ana.perez@empresa.pe");
        assertThat(r.correo()).isEqualTo("ana.perez@empresa.pe");
    }

    @Test
    void reenviaAMsTributarioElTokenDelContadorParaComprobarLaEmpresa() {
        crear("contacto@acme.pe");

        verify(tributario).buscar(CLIENTE, TOKEN);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "sin-arroba", "a@b", "a b@acme.pe", "@acme.pe", "x@acme", "x@acme.p", "x@@acme.pe", "x@acme..pe"})
    void uncorreoInvalidoDa400SinConsultarNiGuardar(String correo) {
        noCrea(correo, HttpStatus.BAD_REQUEST, "Faltan o son inválidos: correo");
        verify(tributario, never()).buscar(anyLong(), anyString());
        verify(generador, never()).generar();
    }

    @Test
    void elCorreoAdmiteHastaSesentaCaracteresPorqueTambienEsElUsuario() {
        String de60 = "a".repeat(52) + "@acme.pe"; // 60
        assertThat(de60).hasSize(60);
        crear(de60);
        assertThat(guardado().getUsername()).isEqualTo(de60);
    }

    @Test
    void conSesentaYUnCaracteresSeRechaza() {
        noCrea("a".repeat(53) + "@acme.pe", HttpStatus.BAD_REQUEST, "hasta 60 caracteres");
    }

    @Test
    void siElClienteNoEstaRegistradoDa404NoGeneraContraseniaNiGuarda() {
        when(tributario.buscar(CLIENTE, TOKEN)).thenReturn(Optional.empty());

        noCrea("contacto@acme.pe", HttpStatus.NOT_FOUND, "Cliente no encontrado: 7");
        verify(generador, never()).generar();
    }

    @Test
    void siLaEmpresaYaTieneCuentaDa409YNoCreaUnaSegunda() {
        when(usuarios.existsByClienteId(CLIENTE)).thenReturn(true);

        noCrea("otro@acme.pe", HttpStatus.CONFLICT, "La empresa ya tiene una cuenta de acceso");
        verify(generador, never()).generar();
    }

    @Test
    void siElCorreoYaLoUsaOtraCuentaDa409() {
        when(usuarios.existsByUsername("contacto@acme.pe")).thenReturn(true);

        noCrea("contacto@acme.pe", HttpStatus.CONFLICT, "Ese correo ya está registrado");
    }

    @Test
    void siMsTributarioNoResponde502YNoSeCreaNada() {
        when(tributario.buscar(CLIENTE, TOKEN)).thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo comprobar"));

        noCrea("contacto@acme.pe", HttpStatus.BAD_GATEWAY, "No se pudo comprobar");
    }

    @Test
    void siDosContadoresCreanAlaVezElSegundoRecibe409PorElIndiceUnicoDeLaEmpresa() {
        when(usuarios.save(any(Usuario.class))).thenThrow(
                new DataIntegrityViolationException("duplicate key value violates unique constraint \"ux_usuarios_cliente_id\""));

        falla409("La empresa ya tiene una cuenta de acceso");
    }

    @Test
    void siLaCarreraEsPorElMismoCorreoElMensajeEsDelCorreo() {
        when(usuarios.save(any(Usuario.class))).thenThrow(
                new DataIntegrityViolationException("duplicate key value violates unique constraint \"usuarios_username_key\""));

        falla409("Ese correo ya está registrado");
    }

    private void falla409(String mensaje) {
        assertThatThrownBy(() -> crear("contacto@acme.pe"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).isEqualTo(mensaje);
                });
        verify(bitacora, never()).save(any()); // sin cuenta no hay nada que anotar
    }

    @Test
    void sinElRolClienteMypeSembradoFallaSinCrearNada() {
        when(roles.findByNombre("CLIENTE_MYPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crear("contacto@acme.pe")).isInstanceOf(IllegalStateException.class);
        verify(usuarios, never()).save(any());
    }

    @Test
    void laRazonSocialMuyLargaSeRecortaYUnaVaciaUsaElNumeroDelCliente() {
        when(tributario.buscar(CLIENTE, TOKEN)).thenReturn(Optional.of(new ClienteResumen(CLIENTE, "X".repeat(200))));
        crear("contacto@acme.pe");
        assertThat(guardado().getNombreCompleto()).hasSize(150);
    }

    @Test
    void sinRazonSocialElNombreEsClienteYSuNumero() {
        when(tributario.buscar(CLIENTE, TOKEN)).thenReturn(Optional.of(new ClienteResumen(CLIENTE, " ")));
        crear("contacto@acme.pe");
        assertThat(guardado().getNombreCompleto()).isEqualTo("Cliente 7");
    }

    @Test
    void laContraseniaDeCadaCuentaSaleDelGenerador() {
        when(generador.generar()).thenReturn("OtraClave2468");

        CuentaClienteResponse r = crear("contacto@acme.pe");

        assertThat(r.passwordTemporal()).isEqualTo("OtraClave2468");
        assertThat(encoder.matches("OtraClave2468", guardado().getPasswordHash())).isTrue();
        verify(generador).generar();
    }
}
