package com.pluscontable.tributario.alertaplazo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluscontable.tributario.alertaplazo.AlertaPlazoRepositorio.Candidata;
import com.pluscontable.tributario.aviso.AvisoInternoDto;
import com.pluscontable.tributario.aviso.AvisoInternoService;
import com.pluscontable.tributario.model.TipoNotificacion;
import com.pluscontable.tributario.service.NotificacionAtendidaEvento;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** HU-19 · Tarea 93 (unitarias): las reglas de los 4 criterios (día exacto, escalones, ATENDIDA, cliente y días) y el «hoy» de Lima. */
class AlertaPlazoServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 17);
    private static final List<Integer> DIAS = List.of(5, 3, 1, 0);

    private final AlertaPlazoRepositorio repo = mock(AlertaPlazoRepositorio.class);
    private final AvisoInternoService avisos = mock(AvisoInternoService.class);
    // 17/11 15:00 en Lima
    private final AlertaPlazoService service = new AlertaPlazoService(repo, avisos, DIAS,
            Clock.fixed(Instant.parse("2026-11-17T20:00:00Z"), AlertaPlazoService.ZONA));

    @BeforeEach
    void preparar() {
        when(avisos.crear(any(), any(), any(), any())).thenAnswer(a ->
                new AvisoInternoDto(900L, a.getArgument(0), a.getArgument(1), a.getArgument(2), Instant.now(), null, a.getArgument(3)));
        when(repo.registrar(anyLong(), anyInt(), any(), anyLong())).thenReturn(true);
    }

    private static Candidata notificacion(long id, LocalDate plazo) {
        return new Candidata(id, 7L, "Acme SAC", TipoNotificacion.CARTA_INDUCTIVA, plazo);
    }

    private void pendientes(LocalDate hoy, Candidata... lista) {
        when(repo.pendientesConPlazoEntre(any(), any())).thenReturn(List.of(lista));
    }

    @Test
    void criterio1_elDiaDeAvisoUnaNotificacionPendienteGeneraSuAlertaConElClienteYLosDiasQueFaltan() {
        pendientes(HOY, notificacion(40, HOY.plusDays(3)));

        assertThat(service.generar(HOY)).isEqualTo(1);

        ArgumentCaptor<String> clave = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> mensaje = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked") ArgumentCaptor<List<Long>> clientes = ArgumentCaptor.forClass(List.class);
        verify(avisos).crear(eq("HU-19"), clave.capture(), mensaje.capture(), clientes.capture());
        assertThat(clave.getValue()).isEqualTo("notificacion:40:dias:3");
        assertThat(clientes.getValue()).containsExactly(7L);
        assertThat(mensaje.getValue()).contains("carta inductiva").contains("Acme SAC").contains("faltan 3 días").contains("20/11/2026");
        verify(repo).registrar(40L, 3, HOY, 900L);
    }

    @Test
    void criterio1_soloElDiaExactoDeCadaEscalonUnDiaAntesODespuesNoGeneraNada() {
        pendientes(HOY, notificacion(1, HOY.plusDays(6)), notificacion(2, HOY.plusDays(4)), notificacion(3, HOY.plusDays(2)),
                notificacion(4, HOY.minusDays(1)));

        assertThat(service.generar(HOY)).isZero();

        verify(avisos, never()).crear(any(), any(), any(), any());
        verify(repo, never()).registrar(anyLong(), anyInt(), any(), anyLong());
    }

    @Test
    void criterio2_sonEscalonadasUnaAlertaPorCadaDiaDeLaListaConSuPropiaClave() {
        Candidata n = notificacion(40, HOY.plusDays(5));
        pendientes(HOY, n);

        for (int atras : new int[] {0, 2, 4, 5}) service.generar(HOY.plusDays(atras)); // faltan 5, 3, 1 y 0

        ArgumentCaptor<String> clave = ArgumentCaptor.forClass(String.class);
        verify(avisos, times(4)).crear(eq("HU-19"), clave.capture(), any(), any());
        assertThat(clave.getAllValues()).containsExactly("notificacion:40:dias:5", "notificacion:40:dias:3", "notificacion:40:dias:1",
                "notificacion:40:dias:0");
        verify(repo).registrar(40L, 5, HOY, 900L);
        verify(repo).registrar(40L, 0, HOY.plusDays(5), 900L);
    }

    @Test
    void criterio2_repetirElProcesoElMismoDiaNoCuentaOtraAlerta() {
        pendientes(HOY, notificacion(40, HOY.plusDays(1)));
        when(repo.registrar(anyLong(), anyInt(), any(), anyLong())).thenReturn(true, false);

        assertThat(service.generar(HOY)).isEqualTo(1);
        assertThat(service.generar(HOY)).isZero(); // la alerta ya existía: el aviso es el mismo y no se cuenta
    }

    @Test
    void elDiaDelPlazoDiceQueVenceHoyYUnDiaAntesFaltaUnDia() {
        pendientes(HOY, notificacion(1, HOY));
        service.generar(HOY);
        pendientes(HOY, notificacion(2, HOY.plusDays(1)));
        service.generar(HOY);

        ArgumentCaptor<String> mensaje = ArgumentCaptor.forClass(String.class);
        verify(avisos, times(2)).crear(any(), any(), mensaje.capture(), any());
        assertThat(mensaje.getAllValues().get(0)).contains("vence hoy");
        assertThat(mensaje.getAllValues().get(1)).contains("falta 1 día").doesNotContain("faltan");
    }

    @Test
    void criterio4_pideAlRepositorioSoloLasPendientesDentroDeLaVentanaDelMayorEscalon() {
        pendientes(HOY);

        service.generar(HOY);

        // la consulta es de PENDIENTES (el filtro vive en el SQL: se prueba contra PostgreSQL); la ventana llega hasta el mayor día
        verify(repo).pendientesConPlazoEntre(HOY, HOY.plusDays(5));
    }

    @Test
    void sinDiasPorDefectoLaListaEsLaConfigurada() {
        AlertaPlazoService soloDos = new AlertaPlazoService(repo, avisos, List.of(2),
                Clock.fixed(Instant.parse("2026-11-17T20:00:00Z"), AlertaPlazoService.ZONA));
        pendientes(HOY, notificacion(1, HOY.plusDays(2)), notificacion(2, HOY.plusDays(3)));

        assertThat(soloDos.generar(HOY)).isEqualTo(1);
        verify(repo).pendientesConPlazoEntre(HOY, HOY.plusDays(2));
    }

    @Test
    void rechazaDiasVaciosRepetidosONegativos() {
        for (List<Integer> malos : List.of(List.<Integer>of(), List.of(3, 3), List.of(5, -1))) {
            assertThatThrownBy(() -> new AlertaPlazoService(repo, avisos, malos, Clock.systemUTC()))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("alertas-carta-dias");
        }
        assertThatThrownBy(() -> new AlertaPlazoService(repo, avisos, null, Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void hoyEsElDeLimaAunqueElServidorCorraEnUtc() {
        // 18/11 02:00 UTC = 17/11 21:00 en Lima: para Lima todavía es 17/11
        AlertaPlazoService enLima = new AlertaPlazoService(repo, avisos, DIAS,
                Clock.fixed(Instant.parse("2026-11-18T02:00:00Z"), AlertaPlazoService.ZONA));
        pendientes(HOY);

        enLima.generarDeHoy();
        enLima.listar();

        verify(repo).pendientesConPlazoEntre(HOY, HOY.plusDays(5));
        verify(repo).visibles(HOY);
    }

    @Test
    void elServicioQueCreaSpringUsaLaZonaDeLimaAunqueElServidorEsteEnOtraZona() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC")); // un servidor en UTC
            assertThat(new AlertaPlazoService(repo, avisos, DIAS).reloj().getZone()).isEqualTo(ZoneId.of("America/Lima"));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void elMensajeNombraElTipoDeNotificacion() {
        assertThat(AlertaPlazoService.mensaje(new Candidata(1L, 7L, "Acme SAC", TipoNotificacion.ESQUELA, HOY), 3)).contains("la esquela");
        assertThat(AlertaPlazoService.mensaje(new Candidata(1L, 7L, "Acme SAC", TipoNotificacion.COMUNICADO, HOY), 3)).contains("el comunicado");
    }

    @Test
    void alAtenderUnaNotificacionSusAvisosSeMarcanLeidosYLosDeOtraNoSeTocan() {
        when(repo.avisosDe(40L)).thenReturn(List.of(900L, 901L));

        service.alAtender(new NotificacionAtendidaEvento(40L));

        verify(avisos).marcarLeido(900L);
        verify(avisos).marcarLeido(901L);
        verify(repo, never()).avisosDe(41L);
        verify(avisos, times(2)).marcarLeido(anyLong());
    }

    @Test
    void alAtenderUnaNotificacionSinAlertasNoSeMarcaNada() {
        when(repo.avisosDe(40L)).thenReturn(List.of());

        service.alAtender(new NotificacionAtendidaEvento(40L));

        verify(avisos, never()).marcarLeido(anyLong());
    }
}
