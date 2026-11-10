package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluscontable.tributario.dto.IncidenciaDto;
import com.pluscontable.tributario.dto.IncidenciaEntradaDto;
import com.pluscontable.tributario.model.Cliente;
import com.pluscontable.tributario.model.Incidencia;
import com.pluscontable.tributario.model.IncidenciaCliente;
import com.pluscontable.tributario.model.TipoIncidencia;
import com.pluscontable.tributario.repository.ClienteRepository;
import com.pluscontable.tributario.repository.IncidenciaClienteRepository;
import com.pluscontable.tributario.repository.IncidenciaRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

/** HU-23 · Tarea 163 (unitarias): registrar la incidencia, su historial por cliente, la general y el usuario del token. */
class IncidenciaServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 9);
    private static final long CLIENTE = 7L;

    private final IncidenciaRepository incidencias = mock(IncidenciaRepository.class);
    private final IncidenciaClienteRepository asociaciones = mock(IncidenciaClienteRepository.class);
    private final ClienteRepository clientesRepo = mock(ClienteRepository.class);
    private final ClienteService clientes = mock(ClienteService.class);
    private final Clock reloj = Clock.fixed(Instant.parse("2026-11-09T15:00:00Z"), ZoneOffset.UTC);
    private IncidenciaService service;

    @BeforeEach
    void preparar() {
        service = new IncidenciaService(incidencias, asociaciones, clientesRepo, clientes, reloj);
        when(incidencias.save(any(Incidencia.class))).thenAnswer(a -> {
            Incidencia i = a.getArgument(0);
            i.setId(50L);
            return i;
        });
        when(clientes.buscar(CLIENTE)).thenReturn(cliente(CLIENTE));
    }

    private static Cliente cliente(long id) {
        Cliente c = new Cliente();
        c.setId(id);
        return c;
    }

    private static IncidenciaEntradaDto propia(TipoIncidencia tipo, Long cliente, LocalDate fecha, String descripcion) {
        return new IncidenciaEntradaDto(tipo, cliente, fecha, descripcion);
    }

    @SuppressWarnings("unchecked")
    private List<IncidenciaCliente> asociacionesGuardadas() {
        ArgumentCaptor<Iterable<IncidenciaCliente>> c = ArgumentCaptor.forClass(Iterable.class);
        verify(asociaciones).saveAll(c.capture());
        List<IncidenciaCliente> lista = new ArrayList<>();
        c.getValue().forEach(lista::add);
        return lista;
    }

    // ---- criterio 1: se registra con tipo, cliente, fecha y descripción

    @Test
    void registraConTipoClienteFechaYDescripcion() {
        IncidenciaDto r = service.registrar(propia(TipoIncidencia.CLIENTE_NO_ENVIA_DOCUMENTOS, CLIENTE, HOY, "No envió las facturas"), "contador1");

        ArgumentCaptor<Incidencia> c = ArgumentCaptor.forClass(Incidencia.class);
        verify(incidencias).save(c.capture());
        assertThat(c.getValue().getTipo()).isEqualTo(TipoIncidencia.CLIENTE_NO_ENVIA_DOCUMENTOS);
        assertThat(c.getValue().getFecha()).isEqualTo(HOY);
        assertThat(c.getValue().getDescripcion()).isEqualTo("No envió las facturas");
        assertThat(r.id()).isEqualTo(50L);
        assertThat(r.tipo()).isEqualTo(TipoIncidencia.CLIENTE_NO_ENVIA_DOCUMENTOS);
        assertThat(r.clientesAsociados()).isEqualTo(1);
    }

    @Test
    void laDescripcionSeGuardaSinEspaciosSobrantes() {
        service.registrar(propia(TipoIncidencia.OTRO, CLIENTE, HOY, "  Cambio de local  "), "contador1");

        ArgumentCaptor<Incidencia> c = ArgumentCaptor.forClass(Incidencia.class);
        verify(incidencias).save(c.capture());
        assertThat(c.getValue().getDescripcion()).isEqualTo("Cambio de local");
    }

    @Test
    void laFechaDeHoyEsValidaYLaDeMananaNo() {
        service.registrar(propia(TipoIncidencia.OTRO, CLIENTE, HOY, "hoy"), "contador1");

        assertThatThrownBy(() -> service.registrar(propia(TipoIncidencia.OTRO, CLIENTE, HOY.plusDays(1), "mañana"), "contador1"))
                .isInstanceOf(ValidationException.class).hasMessage("Faltan o son inválidos: fecha (no puede ser futura)");
    }

    @Test
    void ladescripcionDeMilCaracteresPasaYLaDeMilUnoNo() {
        service.registrar(propia(TipoIncidencia.OTRO, CLIENTE, HOY, "x".repeat(1000)), "contador1");

        assertThatThrownBy(() -> service.registrar(propia(TipoIncidencia.OTRO, CLIENTE, HOY, "x".repeat(1001)), "contador1"))
                .isInstanceOf(ValidationException.class).hasMessage("Faltan o son inválidos: descripcion (máximo 1000 caracteres)");
    }

    @Test
    void sinTipoFechaNiDescripcionNoSeGuardaYElMensajeListaTodo() {
        assertThatThrownBy(() -> service.registrar(propia(null, CLIENTE, null, "  "), "contador1"))
                .isInstanceOf(ValidationException.class).hasMessage("Faltan o son inválidos: tipo, fecha, descripcion");

        verify(incidencias, never()).save(any());
        verify(asociaciones, never()).saveAll(any());
    }

    @Test
    void unCuerpoVacioDa400() {
        assertThatThrownBy(() -> service.registrar(null, "contador1"))
                .isInstanceOf(ValidationException.class).hasMessage("Faltan o son inválidos: tipo, fecha, descripcion");
    }

    @ParameterizedTest
    @EnumSource(value = TipoIncidencia.class, names = "CAIDA_PORTAL_SOL", mode = EnumSource.Mode.EXCLUDE)
    void lasIncidenciasQueNoSonGeneralesExigenCliente(TipoIncidencia tipo) {
        assertThatThrownBy(() -> service.registrar(propia(tipo, null, HOY, "sin cliente"), "contador1"))
                .isInstanceOf(ValidationException.class).hasMessage("Faltan o son inválidos: clienteId");
        assertThatThrownBy(() -> service.registrar(propia(tipo, 0L, HOY, "cliente cero"), "contador1"))
                .isInstanceOf(ValidationException.class).hasMessage("Faltan o son inválidos: clienteId (debe ser positivo)");

        verify(incidencias, never()).save(any());
    }

    @Test
    void siElClienteNoExisteDa404YNoSeGuardaNada() {
        when(clientes.buscar(99L)).thenThrow(new EntityNotFoundException("Cliente no encontrado: 99"));

        assertThatThrownBy(() -> service.registrar(propia(TipoIncidencia.OTRO, 99L, HOY, "x"), "contador1"))
                .isInstanceOf(EntityNotFoundException.class);

        verify(incidencias, never()).save(any());
        verify(asociaciones, never()).saveAll(any());
    }

    // ---- criterio 2: queda en el historial del cliente

    @Test
    void laIncidenciaDeUnClienteQuedaAsociadaSoloAEseCliente() {
        service.registrar(propia(TipoIncidencia.RECHAZO_FRACCIONAMIENTO, CLIENTE, HOY, "SUNAT lo rechazó"), "contador1");

        assertThat(asociacionesGuardadas()).singleElement().satisfies(a -> {
            assertThat(a.getIncidenciaId()).isEqualTo(50L);
            assertThat(a.getClienteId()).isEqualTo(CLIENTE);
        });
        verify(clientesRepo, never()).findByActivoTrue();
    }

    @Test
    void elHistorialDelClienteTraeSusIncidenciasConLaCantidadDeClientesAsociados() {
        Incidencia propia = incidencia(51L, TipoIncidencia.OTRO, LocalDate.of(2026, 11, 8));
        Incidencia general = incidencia(50L, TipoIncidencia.CAIDA_PORTAL_SOL, LocalDate.of(2026, 11, 5));
        when(incidencias.findByCliente(CLIENTE)).thenReturn(List.of(propia, general));
        when(asociaciones.contarPorIncidencia(anyCollection())).thenReturn(List.of(new Object[]{51L, 1L}, new Object[]{50L, 12L}));

        List<IncidenciaDto> r = service.delCliente(CLIENTE);

        assertThat(r).extracting(IncidenciaDto::id).containsExactly(51L, 50L);
        assertThat(r).extracting(IncidenciaDto::clientesAsociados).containsExactly(1, 12);
    }

    @Test
    void sinIncidenciasElHistorialEstaVacioYNoPideConteos() {
        when(incidencias.findByCliente(CLIENTE)).thenReturn(List.of());

        assertThat(service.delCliente(CLIENTE)).isEmpty();
        verify(asociaciones, never()).contarPorIncidencia(any());
    }

    @Test
    void elHistorialDeUnClienteInexistenteDa404() {
        when(clientes.buscar(99L)).thenThrow(new EntityNotFoundException("Cliente no encontrado: 99"));

        assertThatThrownBy(() -> service.delCliente(99L)).isInstanceOf(EntityNotFoundException.class);
        verify(incidencias, never()).findByCliente(any());
    }

    private static Incidencia incidencia(long id, TipoIncidencia tipo, LocalDate fecha) {
        Incidencia i = new Incidencia();
        i.setId(id);
        i.setTipo(tipo);
        i.setFecha(fecha);
        i.setDescripcion("d");
        i.setRegistradaPor("contador1");
        return i;
    }

    // ---- criterio 3: la general (caída del portal SOL) se asocia a toda la cartera activa

    @Test
    void laCaidaDelPortalSolSeAsociaATodaLaCarteraActiva() {
        when(clientesRepo.findByActivoTrue()).thenReturn(LongStream.rangeClosed(1, 5).mapToObj(IncidenciaServiceTest::cliente).toList());

        IncidenciaDto r = service.registrar(propia(TipoIncidencia.CAIDA_PORTAL_SOL, null, HOY, "El portal SOL no abrió"), "contador1");

        assertThat(asociacionesGuardadas()).extracting(IncidenciaCliente::getClienteId).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(asociacionesGuardadas()).extracting(IncidenciaCliente::getIncidenciaId).containsOnly(50L);
        assertThat(r.clientesAsociados()).isEqualTo(5);
        verify(clientes, never()).buscar(any());
    }

    @Test
    void laGeneralSinClientesActivosSeGuardaSinAsociaciones() {
        when(clientesRepo.findByActivoTrue()).thenReturn(List.of());

        IncidenciaDto r = service.registrar(propia(TipoIncidencia.CAIDA_PORTAL_SOL, null, HOY, "Caída general"), "contador1");

        assertThat(r.clientesAsociados()).isZero();
        assertThat(asociacionesGuardadas()).isEmpty();
    }

    @Test
    void laCaidaDelPortalSolNoLlevaCliente() {
        assertThatThrownBy(() -> service.registrar(propia(TipoIncidencia.CAIDA_PORTAL_SOL, CLIENTE, HOY, "x"), "contador1"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Faltan o son inválidos: clienteId (una incidencia general no lleva cliente)");
        verify(incidencias, never()).save(any());
    }

    @Test
    void soloLaCaidaDelPortalSolEsGeneral() {
        for (TipoIncidencia t : TipoIncidencia.values()) {
            assertThat(t.esGeneral()).isEqualTo(t == TipoIncidencia.CAIDA_PORTAL_SOL);
        }
    }

    // ---- criterio 4: el usuario sale del token

    @Test
    void laIncidenciaGuardaElUsuarioQueLaRegistro() {
        IncidenciaDto r = service.registrar(propia(TipoIncidencia.OTRO, CLIENTE, HOY, "x"), " contador1 ");

        ArgumentCaptor<Incidencia> c = ArgumentCaptor.forClass(Incidencia.class);
        verify(incidencias).save(c.capture());
        assertThat(c.getValue().getRegistradaPor()).isEqualTo("contador1");
        assertThat(r.registradaPor()).isEqualTo("contador1");
    }

    @Test
    void sinUsuarioIdentificadoNoSeRegistra() {
        for (String usuario : new String[]{null, "  ", "u".repeat(61)}) {
            assertThatThrownBy(() -> service.registrar(propia(TipoIncidencia.OTRO, CLIENTE, HOY, "x"), usuario))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("No se pudo identificar al usuario que registra la incidencia");
        }
        verify(incidencias, never()).save(any());
    }
}
