package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluscontable.tributario.dto.DocumentoFaltanteDto;
import com.pluscontable.tributario.dto.DocumentoFaltanteEntradaDto;
import com.pluscontable.tributario.dto.IncidenciaDto;
import com.pluscontable.tributario.dto.IncidenciaEntradaDto;
import com.pluscontable.tributario.model.Cliente;
import com.pluscontable.tributario.model.DocumentoFaltante;
import com.pluscontable.tributario.model.EstadoDocumentoFaltante;
import com.pluscontable.tributario.model.TipoIncidencia;
import com.pluscontable.tributario.repository.DocumentoFaltanteRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** HU-24 · Tarea 172 (unitarias): registro con fecha límite y de solicitud, entrega, SIN_RESPUESTA calculado e incidencia. */
class DocumentoFaltanteServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 11);
    private static final long CLIENTE = 7L;

    private final DocumentoFaltanteRepository repo = mock(DocumentoFaltanteRepository.class);
    private final ClienteService clientes = mock(ClienteService.class);
    private final IncidenciaService incidencias = mock(IncidenciaService.class);
    private final DocumentoFaltanteService service = new DocumentoFaltanteService(repo, clientes, incidencias,
            Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), DocumentoFaltanteService.ZONA));

    @BeforeEach
    void preparar() {
        when(clientes.buscar(CLIENTE)).thenReturn(new Cliente());
        when(clientes.buscar(99L)).thenThrow(new EntityNotFoundException("Cliente no encontrado: 99"));
        when(repo.save(any())).thenAnswer(a -> {
            DocumentoFaltante d = a.getArgument(0);
            if (d.getId() == null) d.setId(3L);
            return d;
        });
    }

    private static DocumentoFaltante guardado(String limite, DocumentoFaltante.Estado estado) {
        DocumentoFaltante d = new DocumentoFaltante();
        d.setId(3L);
        d.setClienteId(CLIENTE);
        d.setDocumento("Libro de compras de octubre");
        d.setFechaSolicitud(LocalDate.of(2026, 11, 1));
        d.setFechaLimite(LocalDate.parse(limite));
        d.setEstado(estado);
        if (estado == DocumentoFaltante.Estado.ENTREGADO) d.setFechaEntrega(LocalDate.of(2026, 11, 3));
        return d;
    }

    private static DocumentoFaltanteEntradaDto entrada(String documento, String solicitud, String limite) {
        return new DocumentoFaltanteEntradaDto(documento, solicitud == null ? null : LocalDate.parse(solicitud),
                limite == null ? null : LocalDate.parse(limite));
    }

    @Test
    void criterios1y2_guardaElDocumentoPendienteConSuFechaLimiteYLaFechaEnQueSeSolicito() {
        DocumentoFaltanteDto r = service.registrar(CLIENTE, entrada("  Libro de compras ", "2026-11-05", "2026-11-20"), "contador1");

        ArgumentCaptor<DocumentoFaltante> d = ArgumentCaptor.forClass(DocumentoFaltante.class);
        verify(repo).save(d.capture());
        assertThat(d.getValue().getDocumento()).isEqualTo("Libro de compras");
        assertThat(d.getValue().getFechaSolicitud()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(d.getValue().getFechaLimite()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(d.getValue().getRegistradoPor()).isEqualTo("contador1");
        assertThat(r.estado()).isEqualTo(EstadoDocumentoFaltante.PENDIENTE);
    }

    @Test
    void criterio2_sinFechaDeSolicitudSeTomaLaDeHoyYLaLimitePuedeSerElMismoDia() {
        DocumentoFaltanteDto r = service.registrar(CLIENTE, entrada("Planilla", null, "2026-11-11"), "c");

        assertThat(r.fechaSolicitud()).isEqualTo(HOY);
        assertThat(r.fechaLimite()).isEqualTo(HOY);
    }

    @Test
    void reuneLosErroresYRechazaFechasInvalidasSinGuardar() {
        assertThatThrownBy(() -> service.registrar(CLIENTE, null, "c"))
                .isInstanceOf(ValidationException.class).hasMessage("Faltan o son inválidos: documento, fechaLimite");
        assertThatThrownBy(() -> service.registrar(CLIENTE, entrada("  ", "2026-11-12", "2026-11-30"), "c"))
                .hasMessage("Faltan o son inválidos: documento, fechaSolicitud (no puede ser futura)");
        assertThatThrownBy(() -> service.registrar(CLIENTE, entrada("x".repeat(151), "2026-11-05", "2026-11-04"), "c"))
                .hasMessage("Faltan o son inválidos: documento (máximo 150 caracteres), fechaLimite (no puede ser anterior a la fecha de solicitud)");
        assertThatThrownBy(() -> service.registrar(99L, entrada("Planilla", null, "2026-11-30"), "c")).isInstanceOf(EntityNotFoundException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void unDocumentoDe150CaracteresSiSeAcepta() {
        assertThat(service.registrar(CLIENTE, entrada("x".repeat(150), null, "2026-11-30"), "c").documento()).hasSize(150);
    }

    @Test
    void criterio4_unPendienteConLaFechaLimiteVencidaSeMuestraSinRespuestaYElDeHoyTodaviaNo() {
        when(repo.findByClienteIdOrderByFechaLimiteAscIdAsc(CLIENTE)).thenReturn(List.of(
                guardado("2026-11-10", DocumentoFaltante.Estado.PENDIENTE),
                guardado("2026-11-11", DocumentoFaltante.Estado.PENDIENTE),
                guardado("2026-11-01", DocumentoFaltante.Estado.ENTREGADO)));

        assertThat(service.listar(CLIENTE)).extracting(DocumentoFaltanteDto::estado).containsExactly(
                EstadoDocumentoFaltante.SIN_RESPUESTA, EstadoDocumentoFaltante.PENDIENTE, EstadoDocumentoFaltante.ENTREGADO);
    }

    @Test
    void criterio3_alRecibirloGuardaLaEntregaDeHoyYPasaAEntregadoAunqueEstuvieraSinRespuesta() {
        DocumentoFaltante d = guardado("2026-11-05", DocumentoFaltante.Estado.PENDIENTE);
        when(repo.findById(3L)).thenReturn(Optional.of(d));

        DocumentoFaltanteDto r = service.registrarEntrega(CLIENTE, 3L);

        assertThat(r.estado()).isEqualTo(EstadoDocumentoFaltante.ENTREGADO);
        assertThat(r.fechaEntrega()).isEqualTo(HOY);
    }

    @Test
    void entregarDosVecesDa400YUnDocumentoDeOtroClienteDa404() {
        when(repo.findById(3L)).thenReturn(Optional.of(guardado("2026-11-20", DocumentoFaltante.Estado.ENTREGADO)));

        assertThatThrownBy(() -> service.registrarEntrega(CLIENTE, 3L)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.registrarEntrega(8L, 3L)).isInstanceOf(EntityNotFoundException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void criterio4_unSinRespuestaSeRegistraComoIncidenciaDeHu23YQuedaEnlazado() {
        DocumentoFaltante d = guardado("2026-11-05", DocumentoFaltante.Estado.PENDIENTE);
        when(repo.findById(3L)).thenReturn(Optional.of(d));
        when(incidencias.registrar(any(), eq("contador1"))).thenReturn(
                new IncidenciaDto(40L, TipoIncidencia.CLIENTE_NO_ENVIA_DOCUMENTOS, HOY, "x", "contador1", LocalDateTime.now(), 1));

        DocumentoFaltanteDto r = service.registrarComoIncidencia(CLIENTE, 3L, "contador1");

        ArgumentCaptor<IncidenciaEntradaDto> i = ArgumentCaptor.forClass(IncidenciaEntradaDto.class);
        verify(incidencias).registrar(i.capture(), eq("contador1"));
        assertThat(i.getValue().tipo()).isEqualTo(TipoIncidencia.CLIENTE_NO_ENVIA_DOCUMENTOS);
        assertThat(i.getValue().clienteId()).isEqualTo(CLIENTE);
        assertThat(i.getValue().fecha()).isEqualTo(HOY);
        assertThat(i.getValue().descripcion()).isEqualTo("No envió «Libro de compras de octubre»: vencía el 05/11/2026");
        assertThat(r.incidenciaId()).isEqualTo(40L);
        assertThat(r.estado()).isEqualTo(EstadoDocumentoFaltante.SIN_RESPUESTA);
    }

    @Test
    void criterio4_soloSeRegistraComoIncidenciaUnSinRespuestaYUnaSolaVez() {
        DocumentoFaltante vigente = guardado("2026-11-11", DocumentoFaltante.Estado.PENDIENTE);
        DocumentoFaltante entregado = guardado("2026-11-05", DocumentoFaltante.Estado.ENTREGADO);
        DocumentoFaltante yaRegistrado = guardado("2026-11-05", DocumentoFaltante.Estado.PENDIENTE);
        yaRegistrado.setIncidenciaId(40L);

        for (DocumentoFaltante d : List.of(vigente, entregado, yaRegistrado)) {
            when(repo.findById(3L)).thenReturn(Optional.of(d));
            assertThatThrownBy(() -> service.registrarComoIncidencia(CLIENTE, 3L, "c")).isInstanceOf(ValidationException.class);
        }
        verify(incidencias, never()).registrar(any(), any());
    }

    @Test
    void hoyEsElDeLimaAunqueElServidorCorraEnUtc() {
        // 12/11 02:00 UTC = 11/11 21:00 en Lima: un documento que vence el 11/11 todavía no está sin respuesta
        DocumentoFaltanteService enLima = new DocumentoFaltanteService(repo, clientes, incidencias,
                Clock.fixed(Instant.parse("2026-11-12T02:00:00Z"), DocumentoFaltanteService.ZONA));
        when(repo.findByClienteIdOrderByFechaLimiteAscIdAsc(CLIENTE)).thenReturn(List.of(guardado("2026-11-11", DocumentoFaltante.Estado.PENDIENTE)));

        assertThat(enLima.listar(CLIENTE).get(0).estado()).isEqualTo(EstadoDocumentoFaltante.PENDIENTE);
        assertThat(DocumentoFaltanteService.ZONA).isEqualTo(ZoneId.of("America/Lima"));
    }
}
