package com.pluscontable.tributario.ruc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pluscontable.tributario.aviso.AvisoInternoDto;
import com.pluscontable.tributario.aviso.AvisoInternoService;
import com.pluscontable.tributario.model.Cliente;
import com.pluscontable.tributario.model.Cliente.CondicionHabido;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** HU-22 · T80: solo la transición HABIDO → NO_HABIDO avisa, con clave y mensaje deterministas por día de Lima. */
class AlertaCondicionRucServiceTest {

    /** 18/11/2026 03:30 UTC = 17/11/2026 22:30 en Lima: la fecha de la clave debe ser la de Lima. */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-18T03:30:00Z"), ZoneOffset.UTC);
    private static final String CLAVE = "NO_HABIDO:cliente:7:2026-11-17";
    private static final String MENSAJE = "El cliente Empresa Prueba SAC (RUC 20123456786) pasó a condición NO HABIDO.";

    private final AvisoInternoService avisos = mock(AvisoInternoService.class);
    private final AlertaCondicionRucService servicio =
            new AlertaCondicionRucService(avisos, RELOJ.withZone(AlertaCondicionRucService.ZONA));

    private static Cliente cliente(CondicionHabido condicion) {
        Cliente c = new Cliente();
        c.setId(7L);
        c.setRuc("20123456786");
        c.setRazonSocial("Empresa Prueba SAC");
        c.setCondicionHabido(condicion);
        return c;
    }

    @Test
    void soloHabidoANoHabidoEsTransicionQueAvisa() {
        assertThat(AlertaCondicionRucService.pasaANoHabido(CondicionHabido.HABIDO, CondicionHabido.NO_HABIDO)).isTrue();
        assertThat(AlertaCondicionRucService.pasaANoHabido(CondicionHabido.NO_HABIDO, CondicionHabido.HABIDO)).isFalse();
        assertThat(AlertaCondicionRucService.pasaANoHabido(CondicionHabido.HABIDO, CondicionHabido.HABIDO)).isFalse();
        assertThat(AlertaCondicionRucService.pasaANoHabido(CondicionHabido.NO_HABIDO, CondicionHabido.NO_HABIDO)).isFalse();
        assertThat(AlertaCondicionRucService.pasaANoHabido(null, CondicionHabido.NO_HABIDO)).isFalse();
    }

    @Test
    void transicionCreaAvisoConOrigenClaveDeLimaMensajeYCliente() {
        var creado = new AvisoInternoDto(1L, "HU22_RUC", CLAVE, MENSAJE, Instant.now(), null, List.of(7L));
        when(avisos.buscarPorEvento("HU22_RUC", CLAVE)).thenReturn(Optional.empty());
        when(avisos.crear("HU22_RUC", CLAVE, MENSAJE, List.of(7L))).thenReturn(creado);

        assertThat(servicio.registrarCambio(cliente(CondicionHabido.NO_HABIDO), CondicionHabido.HABIDO)).contains(creado);
        verify(avisos).crear("HU22_RUC", CLAVE, MENSAJE, List.of(7L));
    }

    @Test
    void siYaSeAvisoHoyDevuelveElExistenteSinCrearOtro() {
        var previo = new AvisoInternoDto(1L, "HU22_RUC", CLAVE, "mensaje anterior", Instant.now(), null, List.of(7L));
        when(avisos.buscarPorEvento("HU22_RUC", CLAVE)).thenReturn(Optional.of(previo));

        assertThat(servicio.registrarCambio(cliente(CondicionHabido.NO_HABIDO), CondicionHabido.HABIDO)).contains(previo);
        verify(avisos, never()).crear(anyString(), anyString(), anyString(), anyList());
    }

    @Test
    void sinCambioRecuperacionOSinValorAnteriorNoAvisa() {
        assertThat(servicio.registrarCambio(cliente(CondicionHabido.NO_HABIDO), CondicionHabido.NO_HABIDO)).isEmpty();
        assertThat(servicio.registrarCambio(cliente(CondicionHabido.HABIDO), CondicionHabido.HABIDO)).isEmpty();
        assertThat(servicio.registrarCambio(cliente(CondicionHabido.HABIDO), CondicionHabido.NO_HABIDO)).isEmpty();
        assertThat(servicio.registrarCambio(cliente(CondicionHabido.NO_HABIDO), null)).isEmpty();
        verifyNoInteractions(avisos);
    }

    @Test
    void elDetectorDeT79CoincideConLaRegla() {
        // El detector del PR #49 (que usará la verificación diaria, T83) marca NO_HABIDO en el mismo caso.
        var noHabido = new ResultadoConsulta.Encontrado(new DatosRuc("20123456786", "Empresa Prueba SAC",
                "ACTIVO", "NO HABIDO", null, null, "prueba", null));
        assertThat(DetectorCambioCondicionRuc.evaluar(CondicionHabido.HABIDO, DetectorCambioCondicionRuc.EstadoSunat.ACTIVO,
                noHabido).motivos()).containsExactly(DetectorCambioCondicionRuc.MotivoAviso.NO_HABIDO);
        assertThat(DetectorCambioCondicionRuc.evaluar(CondicionHabido.NO_HABIDO, DetectorCambioCondicionRuc.EstadoSunat.ACTIVO,
                noHabido).motivos()).isEmpty();
        verify(avisos, never()).crear(any(), any(), any(), any());
    }
}
