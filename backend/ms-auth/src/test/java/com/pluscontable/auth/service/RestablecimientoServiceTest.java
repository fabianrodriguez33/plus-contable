package com.pluscontable.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pluscontable.auth.model.BitacoraSeguridad;
import com.pluscontable.auth.model.IntentoRestablecimiento;
import com.pluscontable.auth.model.SolicitudRestablecimiento;
import com.pluscontable.auth.model.Usuario;
import com.pluscontable.auth.repository.BitacoraRepository;
import com.pluscontable.auth.repository.IntentoRestablecimientoRepository;
import com.pluscontable.auth.repository.SolicitudRestablecimientoRepository;
import com.pluscontable.auth.repository.UsuarioRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * HU-41 · Tarea 230 (unitarias): la solicitud de restablecimiento sin revelar si la cuenta existe (mismo mensaje y mismo trabajo
 * antes de responder), el límite por IP y por correo, la transacción de la solicitud con su bitácora y su atención.
 */
class RestablecimientoServiceTest {

    private static final String CORREO = "contacto@acme.pe";
    private static final String IP = "10.0.0.9";

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final SolicitudRestablecimientoRepository solicitudes = mock(SolicitudRestablecimientoRepository.class);
    private final IntentoRestablecimientoRepository intentos = mock(IntentoRestablecimientoRepository.class);
    private final BitacoraRepository bitacora = mock(BitacoraRepository.class);
    private final CuentaClienteServiceTest.TransaccionDePrueba tm = new CuentaClienteServiceTest.TransaccionDePrueba();
    /** Lo que se manda a segundo plano queda aquí, sin correr, hasta que la prueba lo corre. */
    private final List<Runnable> enSegundoPlano = new ArrayList<>();
    private final RestablecimientoService service = new RestablecimientoService(usuarios, solicitudes, intentos, bitacora,
            new TransactionTemplate(tm), enSegundoPlano::add, 5, 3, 60);

    private Usuario cliente;
    private boolean guardoDentroDeTransaccion;

    @BeforeEach
    void preparar() {
        cliente = new Usuario();
        ReflectionTestUtils.setField(cliente, "id", 11L);
        cliente.setUsername(CORREO);
        cliente.setClienteId(7L);
        when(usuarios.findByUsername(CORREO)).thenReturn(Optional.of(cliente));
        when(usuarios.findByUsername("nadie@acme.pe")).thenReturn(Optional.empty());
        when(solicitudes.findByUsuarioIdAndAtendidaEnIsNull(any())).thenReturn(Optional.empty());
        when(solicitudes.saveAndFlush(any(SolicitudRestablecimiento.class))).thenAnswer(a -> {
            guardoDentroDeTransaccion = tm.activa;
            return a.getArgument(0);
        });
    }

    private void correrSegundoPlano() {
        enSegundoPlano.forEach(Runnable::run);
        enSegundoPlano.clear();
    }

    @Test
    void criterio1_elCorreoDeUnClienteRegistraLaSolicitudYSuBitacoraEnUnaSolaTransaccion() {
        String mensaje = service.solicitar("  Contacto@ACME.pe ", IP);
        correrSegundoPlano();

        assertThat(mensaje).isEqualTo(RestablecimientoService.MENSAJE_SOLICITUD);
        ArgumentCaptor<SolicitudRestablecimiento> s = ArgumentCaptor.forClass(SolicitudRestablecimiento.class);
        verify(solicitudes).saveAndFlush(s.capture());
        assertThat(s.getValue().getUsuarioId()).isEqualTo(11L);
        assertThat(s.getValue().getIp()).isEqualTo(IP);
        ArgumentCaptor<BitacoraSeguridad> b = ArgumentCaptor.forClass(BitacoraSeguridad.class);
        verify(bitacora).save(b.capture());
        assertThat(b.getValue().getEvento()).isEqualTo("RESTABLECIMIENTO_SOLICITADO");
        assertThat(guardoDentroDeTransaccion).isTrue();
        assertThat(tm.confirmadas).isEqualTo(1);
    }

    @Test
    void siFallaLaBitacoraSeDeshaceTambienLaSolicitud() {
        when(bitacora.save(any())).thenThrow(new IllegalStateException("se cayó la base"));

        assertThat(service.solicitar(CORREO, IP)).isEqualTo(RestablecimientoService.MENSAJE_SOLICITUD);
        correrSegundoPlano();

        assertThat(tm.deshechas).isEqualTo(1);
        assertThat(tm.confirmadas).isZero();
    }

    @Test
    void criterio2_antesDeResponderHaceLoMismoExistaONoLaCuentaYNoLaBusca() {
        String conCuenta = service.solicitar(CORREO, IP);
        String sinCuenta = service.solicitar("nadie@acme.pe", IP);

        assertThat(sinCuenta).isEqualTo(conCuenta).isEqualTo(RestablecimientoService.MENSAJE_SOLICITUD);
        // hasta aquí solo se contó y se guardó un intento por pedido: ni la cuenta ni las solicitudes se tocaron
        verifyNoInteractions(usuarios, solicitudes, bitacora);
        verify(intentos, times(2)).save(any(IntentoRestablecimiento.class));
        assertThat(enSegundoPlano).hasSize(2);
    }

    @Test
    void criterio2_unCorreoSinCuentaNoGuardaSolicitudNiBitacora() {
        service.solicitar("nadie@acme.pe", IP);
        correrSegundoPlano();

        verify(solicitudes, never()).saveAndFlush(any(SolicitudRestablecimiento.class));
        verify(bitacora, never()).save(any());
    }

    @Test
    void elIntentoGuardaLaHuellaDelCorreoNormalizadoNoElCorreo() {
        service.solicitar("  Contacto@ACME.pe ", null);

        ArgumentCaptor<IntentoRestablecimiento> i = ArgumentCaptor.forClass(IntentoRestablecimiento.class);
        verify(intentos).save(i.capture());
        assertThat(i.getValue().getCorreoHuella()).isEqualTo(RestablecimientoService.huella(CORREO)).hasSize(64).doesNotContain("acme");
        assertThat(i.getValue().getIp()).isEqualTo("desconocida");
    }

    @Test
    void conElMaximoDeLaIpOElDelCorreoDa429SinGuardarNiBuscarNada() {
        when(intentos.countByIpAndFechaAfter(eq(IP), any())).thenReturn(5L);
        assertThatThrownBy(() -> service.solicitar(CORREO, IP))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(e.getReason()).isEqualTo(RestablecimientoService.MENSAJE_DEMASIADOS);
                });

        when(intentos.countByIpAndFechaAfter(eq(IP), any())).thenReturn(4L);
        when(intentos.countByCorreoHuellaAndFechaAfter(eq(RestablecimientoService.huella(CORREO)), any())).thenReturn(3L);
        assertThatThrownBy(() -> service.solicitar(CORREO, IP)).isInstanceOf(ResponseStatusException.class);

        verify(intentos, never()).save(any());
        assertThat(enSegundoPlano).isEmpty();
    }

    @Test
    void justoDebajoDelMaximoTodaviaSeAtiende() {
        when(intentos.countByIpAndFechaAfter(eq(IP), any())).thenReturn(4L);
        when(intentos.countByCorreoHuellaAndFechaAfter(anyString(), any())).thenReturn(2L);

        assertThat(service.solicitar(CORREO, IP)).isEqualTo(RestablecimientoService.MENSAJE_SOLICITUD);
        verify(intentos).save(any(IntentoRestablecimiento.class));
    }

    @Test
    void cadaPedidoBorraLosIntentosDeMasDeUnDia() {
        service.solicitar(CORREO, IP);

        verify(intentos).borrarAnterioresA(any());
    }

    @Test
    void lasCuentasDelEstudioOInactivasTampocoGeneranSolicitud() {
        cliente.setClienteId(null);
        service.solicitar(CORREO, IP);
        correrSegundoPlano();

        cliente.setClienteId(7L);
        cliente.setActivo(false);
        service.solicitar(CORREO, IP);
        correrSegundoPlano();

        verify(solicitudes, never()).saveAndFlush(any(SolicitudRestablecimiento.class));
        verify(bitacora, never()).save(any());
    }

    @Test
    void pedirOtraVezConUnaPendienteNoDuplicaYUnPedidoSimultaneoNoFalla() {
        when(solicitudes.findByUsuarioIdAndAtendidaEnIsNull(11L)).thenReturn(Optional.of(new SolicitudRestablecimiento(11L, null)));
        service.solicitar(CORREO, IP);
        correrSegundoPlano();
        verify(solicitudes, never()).saveAndFlush(any(SolicitudRestablecimiento.class));

        when(solicitudes.findByUsuarioIdAndAtendidaEnIsNull(11L)).thenReturn(Optional.empty());
        when(solicitudes.saveAndFlush(any(SolicitudRestablecimiento.class))).thenThrow(new DataIntegrityViolationException("ux"));
        service.solicitar(CORREO, IP);
        correrSegundoPlano();
        verify(bitacora, never()).save(any());
        assertThat(tm.deshechas).isEqualTo(1);
    }

    @Test
    void unCorreoVacioODemasiadoLargoDa400SinContarIntentos() {
        assertThatThrownBy(() -> service.solicitar(null, IP)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.solicitar("   ", IP)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.solicitar("a".repeat(56) + "@x.pe", IP)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(intentos);
    }

    @Test
    void atenderMarcaLaPendienteConQuienYCuandoYSinPendienteNoHaceNada() {
        SolicitudRestablecimiento pendiente = new SolicitudRestablecimiento(11L, null);
        when(solicitudes.findByUsuarioIdAndAtendidaEnIsNull(11L)).thenReturn(Optional.of(pendiente));

        service.atender(11L, "ana");
        assertThat(pendiente.getAtendidaPor()).isEqualTo("ana");
        assertThat(pendiente.getAtendidaEn()).isNotNull();
        verify(solicitudes).save(pendiente);

        service.atender(12L, "ana");
        verify(solicitudes, times(1)).save(any());
    }
}
