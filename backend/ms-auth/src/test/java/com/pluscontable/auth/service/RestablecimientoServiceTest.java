package com.pluscontable.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluscontable.auth.model.BitacoraSeguridad;
import com.pluscontable.auth.model.SolicitudRestablecimiento;
import com.pluscontable.auth.model.Usuario;
import com.pluscontable.auth.repository.BitacoraRepository;
import com.pluscontable.auth.repository.SolicitudRestablecimientoRepository;
import com.pluscontable.auth.repository.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** HU-41 · Tarea 230 (unitarias): la solicitud de restablecimiento, sin revelar si la cuenta existe, y su atención. */
class RestablecimientoServiceTest {

    private static final String CORREO = "contacto@acme.pe";

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final SolicitudRestablecimientoRepository solicitudes = mock(SolicitudRestablecimientoRepository.class);
    private final BitacoraRepository bitacora = mock(BitacoraRepository.class);
    private final RestablecimientoService service = new RestablecimientoService(usuarios, solicitudes, bitacora);

    private Usuario cliente;

    @BeforeEach
    void preparar() {
        cliente = new Usuario();
        ReflectionTestUtils.setField(cliente, "id", 11L);
        cliente.setUsername(CORREO);
        cliente.setClienteId(7L);
        when(usuarios.findByUsername(CORREO)).thenReturn(Optional.of(cliente));
        when(usuarios.findByUsername("nadie@acme.pe")).thenReturn(Optional.empty());
        when(solicitudes.findByUsuarioIdAndAtendidaEnIsNull(any())).thenReturn(Optional.empty());
    }

    @Test
    void criterio1_elCorreoDeUnClienteRegistraLaSolicitudParaElEstudioYLaAnotaEnLaBitacora() {
        String mensaje = service.solicitar("  Contacto@ACME.pe ", "10.0.0.9");

        assertThat(mensaje).isEqualTo(RestablecimientoService.MENSAJE_SOLICITUD);
        ArgumentCaptor<SolicitudRestablecimiento> s = ArgumentCaptor.forClass(SolicitudRestablecimiento.class);
        verify(solicitudes).saveAndFlush(s.capture());
        assertThat(s.getValue().getUsuarioId()).isEqualTo(11L);
        assertThat(s.getValue().getIp()).isEqualTo("10.0.0.9");
        ArgumentCaptor<BitacoraSeguridad> b = ArgumentCaptor.forClass(BitacoraSeguridad.class);
        verify(bitacora).save(b.capture());
        assertThat(b.getValue().getEvento()).isEqualTo("RESTABLECIMIENTO_SOLICITADO");
    }

    @Test
    void criterio2_unCorreoSinCuentaRecibeElMismoMensajeYNoSeGuardaNada() {
        assertThat(service.solicitar("nadie@acme.pe", "10.0.0.9")).isEqualTo(RestablecimientoService.MENSAJE_SOLICITUD);

        verify(solicitudes, never()).saveAndFlush(any(SolicitudRestablecimiento.class));
        verify(bitacora, never()).save(any());
    }

    @Test
    void criterio2_lasCuentasDelEstudioOInactivasTampocoGeneranSolicitudNiCambianElMensaje() {
        cliente.setClienteId(null);
        assertThat(service.solicitar(CORREO, null)).isEqualTo(RestablecimientoService.MENSAJE_SOLICITUD);

        cliente.setClienteId(7L);
        cliente.setActivo(false);
        assertThat(service.solicitar(CORREO, null)).isEqualTo(RestablecimientoService.MENSAJE_SOLICITUD);

        verify(solicitudes, never()).saveAndFlush(any(SolicitudRestablecimiento.class));
        verify(bitacora, never()).save(any());
    }

    @Test
    void pedirOtraVezConUnaPendienteNoDuplicaYUnPedidoSimultaneoNoFalla() {
        when(solicitudes.findByUsuarioIdAndAtendidaEnIsNull(11L)).thenReturn(Optional.of(new SolicitudRestablecimiento(11L, null)));
        assertThat(service.solicitar(CORREO, null)).isEqualTo(RestablecimientoService.MENSAJE_SOLICITUD);
        verify(solicitudes, never()).saveAndFlush(any(SolicitudRestablecimiento.class));

        when(solicitudes.findByUsuarioIdAndAtendidaEnIsNull(11L)).thenReturn(Optional.empty());
        when(solicitudes.saveAndFlush(any(SolicitudRestablecimiento.class))).thenThrow(new DataIntegrityViolationException("ux"));
        assertThat(service.solicitar(CORREO, null)).isEqualTo(RestablecimientoService.MENSAJE_SOLICITUD);
        verify(bitacora, never()).save(any());
    }

    @Test
    void unCorreoVacioODemasiadoLargoDa400() {
        assertThatThrownBy(() -> service.solicitar(null, null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.solicitar("   ", null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.solicitar("a".repeat(56) + "@x.pe", null)).isInstanceOf(ResponseStatusException.class);
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
