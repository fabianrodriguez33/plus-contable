package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluscontable.tributario.dto.ObligacionDto;
import com.pluscontable.tributario.dto.PagoEntradaDto;
import com.pluscontable.tributario.model.Obligacion;
import com.pluscontable.tributario.model.Obligacion.Estado;
import com.pluscontable.tributario.model.PagoObligacion;
import com.pluscontable.tributario.repository.ObligacionRepository;
import com.pluscontable.tributario.repository.PagoObligacionRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * HU-13 (unitarias de registrar un pago): varios pagos parciales, saldo y acumulado, estados PAGO_PARCIAL y PAGADO,
 * comprobante PDF y reglas de validación. El almacenamiento usa una carpeta temporal.
 */
class PagoObligacionServiceTest {

    private static final long OBLIGACION = 3L;
    private static final long CLIENTE = 9L;
    private static final LocalDate HOY = LocalDate.of(2026, 11, 9);

    @TempDir Path carpeta;

    private final ObligacionRepository obligaciones = mock(ObligacionRepository.class);
    private final PagoObligacionRepository pagos = mock(PagoObligacionRepository.class);
    private final Clock reloj = Clock.fixed(Instant.parse("2026-11-09T15:00:00Z"), ZoneOffset.UTC);
    private AlmacenComprobantes almacen;
    private PagoObligacionService service;
    private Obligacion obligacion;

    @BeforeEach
    void preparar() {
        almacen = new AlmacenComprobantes(carpeta.toString());
        service = new PagoObligacionService(obligaciones, pagos, almacen, 10, reloj);
        obligacion = new Obligacion();
        obligacion.setId(OBLIGACION);
        obligacion.setClienteId(CLIENTE);
        obligacion.setPeriodo(LocalDate.of(2026, 10, 1));
        obligacion.setFechaVencimiento(LocalDate.of(2026, 11, 20));
        obligacion.setEstado(Estado.DECLARADO);
        obligacion.setMontoDeterminado(new BigDecimal("1000.00"));
        when(obligaciones.buscarParaActualizar(OBLIGACION)).thenReturn(Optional.of(obligacion));
        when(obligaciones.save(any(Obligacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pagos.save(any(PagoObligacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pagos.sumaPagada(OBLIGACION)).thenReturn(BigDecimal.ZERO);
        when(pagos.countByObligacionId(OBLIGACION)).thenReturn(0L);
    }

    private static MultipartFile pdf() {
        return new MockMultipartFile("archivo", "constancia.pdf", "application/pdf", "%PDF-1.7 contenido".getBytes(StandardCharsets.US_ASCII));
    }

    private static PagoEntradaDto pago(String monto, String constancia) {
        return new PagoEntradaDto(HOY.minusDays(1), new BigDecimal(monto), constancia);
    }

    private PagoObligacion guardado() {
        ArgumentCaptor<PagoObligacion> c = ArgumentCaptor.forClass(PagoObligacion.class);
        verify(pagos).save(c.capture());
        return c.getValue();
    }

    private long archivosGuardados() throws IOException {
        try (Stream<Path> s = Files.walk(carpeta)) {
            return s.filter(Files::isRegularFile).count();
        }
    }

    private void noSeGuarda(PagoEntradaDto dto, MultipartFile archivo, String... partes) throws IOException {
        assertThatThrownBy(() -> service.registrar(OBLIGACION, dto, archivo))
                .isInstanceOf(ValidationException.class)
                .hasMessageStartingWith("Faltan o son inválidos: ")
                .satisfies(e -> {
                    for (String parte : partes) assertThat(e.getMessage()).contains(parte);
                });
        verify(pagos, never()).save(any());
        verify(obligaciones, never()).save(any());
        assertThat(archivosGuardados()).isZero();
    }

    @Test
    void unPagoParcialDejaPagoParcialConSuAcumuladoYSuSaldo() {
        ObligacionDto r = service.registrar(OBLIGACION, pago("400.00", "C-001"), pdf());

        assertThat(r.estado()).isEqualTo(Estado.PAGO_PARCIAL);
        assertThat(r.montoAcumuladoPagado()).isEqualByComparingTo("400.00");
        assertThat(r.saldo()).isEqualByComparingTo("600.00");
        assertThat(r.numeroPagos()).isEqualTo(1);
        assertThat(obligacion.getEstado()).isEqualTo(Estado.PAGO_PARCIAL);
        PagoObligacion p = guardado();
        assertThat(p.getObligacionId()).isEqualTo(OBLIGACION);
        assertThat(p.getNumeroConstancia()).isEqualTo("C-001");
        assertThat(p.getFechaPago()).isEqualTo(HOY.minusDays(1));
        assertThat(p.getMontoPagado()).isEqualByComparingTo("400.00");
        assertThat(p.getAcumuladoDespues()).isEqualByComparingTo("400.00");
        assertThat(p.getSaldoDespues()).isEqualByComparingTo("600.00");
    }

    @Test
    void elPagoQueIgualaElMontoDeterminadoPasaAPagadoConSaldoCero() {
        ObligacionDto r = service.registrar(OBLIGACION, pago("1000.00", "C-001"), pdf());

        assertThat(r.estado()).isEqualTo(Estado.PAGADO);
        assertThat(r.saldo()).isEqualByComparingTo("0");
        assertThat(obligacion.getEstado()).isEqualTo(Estado.PAGADO);
        assertThat(guardado().getSaldoDespues()).isEqualByComparingTo("0");
    }

    @Test
    void variosPagosParcialesSumanAlAcumuladoYElUltimoLaSalda() {
        // ya hay 400.00 pagados en dos pagos; vuelve a quedar en pago parcial
        obligacion.setEstado(Estado.PAGO_PARCIAL);
        when(pagos.sumaPagada(OBLIGACION)).thenReturn(new BigDecimal("400.00"));
        when(pagos.countByObligacionId(OBLIGACION)).thenReturn(2L);

        ObligacionDto tercero = service.registrar(OBLIGACION, pago("350.00", "C-003"), pdf());

        assertThat(tercero.estado()).isEqualTo(Estado.PAGO_PARCIAL);
        assertThat(tercero.montoAcumuladoPagado()).isEqualByComparingTo("750.00");
        assertThat(tercero.saldo()).isEqualByComparingTo("250.00");
        assertThat(tercero.numeroPagos()).isEqualTo(3);

        // el siguiente cubre el saldo
        when(pagos.sumaPagada(OBLIGACION)).thenReturn(new BigDecimal("750.00"));
        when(pagos.countByObligacionId(OBLIGACION)).thenReturn(3L);
        ObligacionDto cuarto = service.registrar(OBLIGACION, pago("250.00", "C-004"), pdf());

        assertThat(cuarto.estado()).isEqualTo(Estado.PAGADO);
        assertThat(cuarto.montoAcumuladoPagado()).isEqualByComparingTo("1000.00");
        assertThat(cuarto.saldo()).isEqualByComparingTo("0");
        assertThat(cuarto.numeroPagos()).isEqualTo(4);
    }

    @Test
    void conCentavosElAcumuladoYElSaldoSonExactos() {
        obligacion.setMontoDeterminado(new BigDecimal("200.00"));
        when(pagos.sumaPagada(OBLIGACION)).thenReturn(new BigDecimal("100.10"));

        ObligacionDto r = service.registrar(OBLIGACION, pago("99.90", "C-002"), pdf());

        assertThat(r.estado()).isEqualTo(Estado.PAGADO);
        assertThat(r.montoAcumuladoPagado()).isEqualByComparingTo("200.00");
    }

    @Test
    void guardaElComprobanteEnLaCarpetaDelClienteYSusDatosEnElPago() throws IOException {
        service.registrar(OBLIGACION, pago("100.00", "C-001"), pdf());

        PagoObligacion p = guardado();
        assertThat(archivosGuardados()).isEqualTo(1);
        assertThat(p.getNombreArchivo()).isEqualTo("constancia.pdf");
        assertThat(p.getContentType()).isEqualTo("application/pdf");
        assertThat(p.getTamanoBytes()).isEqualTo("%PDF-1.7 contenido".length());
        assertThat(p.getRutaAlmacenamiento()).startsWith("pagos").contains(String.valueOf(CLIENTE)).endsWith(".pdf");
        assertThat(Files.exists(carpeta.resolve(p.getRutaAlmacenamiento()))).isTrue();
    }

    @Test
    void sinSaldoPorPagarNoAdmiteUnPagoMayorQueElSaldoNiGuardaElArchivo() throws IOException {
        when(pagos.sumaPagada(OBLIGACION)).thenReturn(new BigDecimal("900.00"));

        noSeGuarda(pago("100.01", "C-009"), pdf(), "montoPagado (supera el saldo pendiente de 100.00)");
    }

    @Test
    void elPagoExactoDelSaldoSiSeAdmite() {
        when(pagos.sumaPagada(OBLIGACION)).thenReturn(new BigDecimal("900.00"));

        assertThat(service.registrar(OBLIGACION, pago("100.00", "C-009"), pdf()).estado()).isEqualTo(Estado.PAGADO);
    }

    @Test
    void unaObligacionSinDeclararNoRecibePagos() throws IOException {
        obligacion.setEstado(Estado.PENDIENTE);

        assertThatThrownBy(() -> service.registrar(OBLIGACION, pago("100.00", "C-001"), pdf()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Solo se registra pago sobre una obligación ya declarada");
        verify(pagos, never()).save(any());
        assertThat(archivosGuardados()).isZero();
    }

    @Test
    void unaObligacionPagadaNoRecibeMasPagos() throws IOException {
        obligacion.setEstado(Estado.PAGADO);

        assertThatThrownBy(() -> service.registrar(OBLIGACION, pago("100.00", "C-001"), pdf()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("La obligación ya está pagada");
        verify(pagos, never()).save(any());
        assertThat(archivosGuardados()).isZero();
    }

    @Test
    void siLaObligacionNoExisteResponde404() {
        when(obligaciones.buscarParaActualizar(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrar(99L, pago("100.00", "C-001"), pdf()))
                .isInstanceOf(EntityNotFoundException.class).hasMessage("Obligación no encontrada");
    }

    // ---- comprobante = número de constancia; el PDF es opcional (alcance reducido de Beto, 10/11)

    @Test
    void unPagoSinArchivoSeRegistraSoloConElNumeroDeConstanciaYNoGuardaNingunArchivo() throws IOException {
        ObligacionDto r = service.registrar(OBLIGACION, pago("400.00", " C-001 "), null);

        PagoObligacion p = guardado();
        assertThat(p.getNumeroConstancia()).isEqualTo("C-001");
        assertThat(p.getNombreArchivo()).isNull();
        assertThat(p.getContentType()).isNull();
        assertThat(p.getTamanoBytes()).isNull();
        assertThat(p.getRutaAlmacenamiento()).isNull();
        assertThat(archivosGuardados()).isZero();
        assertThat(r.estado()).isEqualTo(Estado.PAGO_PARCIAL);
        assertThat(r.montoAcumuladoPagado()).isEqualByComparingTo("400.00");
        assertThat(r.saldo()).isEqualByComparingTo("600.00");
    }

    @Test
    void unArchivoVacioCuentaComoSinArchivo() throws IOException {
        MultipartFile vacio = new MockMultipartFile("archivo", "c.pdf", "application/pdf", new byte[0]);

        ObligacionDto r = service.registrar(OBLIGACION, pago("1000.00", "C-001"), vacio);

        assertThat(r.estado()).isEqualTo(Estado.PAGADO);
        assertThat(guardado().getRutaAlmacenamiento()).isNull();
        assertThat(archivosGuardados()).isZero();
    }

    @Test
    void sinArchivoSiguenValiendoElSaldoLaFechaYLaConstanciaRepetida() throws IOException {
        noSeGuarda(pago("1000.01", "C-1"), null, "montoPagado (supera el saldo pendiente de 1000.00)");
        noSeGuarda(new PagoEntradaDto(HOY.plusDays(1), new BigDecimal("10"), "C-1"), null, "fechaPago (no puede ser futura)");
        when(pagos.existsByObligacionIdAndNumeroConstancia(OBLIGACION, "C-001")).thenReturn(true);
        noSeGuarda(pago("10", "C-001"), null, "numeroConstancia (ya registrado en esta obligación)");
    }

    @Test
    void siFallaElGuardadoSinArchivoNoHayNadaQueBorrarYLaObligacionNoCambia() {
        doThrow(new IllegalStateException("falla de base")).when(pagos).save(any(PagoObligacion.class));

        assertThatThrownBy(() -> service.registrar(OBLIGACION, pago("100.00", "C-001"), null)).isInstanceOf(IllegalStateException.class);
        verify(obligaciones, never()).save(any());
    }

    // ---- consultar los pagos de una obligación

    @Test
    void listarDevuelveLosPagosEnOrdenConSuConstanciaAcumuladoYSaldo() {
        PagoObligacion a = pagoGuardado(1L, "C-001", "400.00", "400.00", "600.00");
        PagoObligacion b = pagoGuardado(2L, "C-002", "100.00", "500.00", "500.00");
        when(obligaciones.existsById(OBLIGACION)).thenReturn(true);
        when(pagos.findByObligacionIdOrderByFechaPagoAscIdAsc(OBLIGACION)).thenReturn(java.util.List.of(a, b));

        java.util.List<com.pluscontable.tributario.dto.PagoDto> lista = service.listar(OBLIGACION);

        assertThat(lista).extracting(com.pluscontable.tributario.dto.PagoDto::numeroConstancia).containsExactly("C-001", "C-002");
        assertThat(lista.get(1).montoPagado()).isEqualByComparingTo("100.00");
        assertThat(lista.get(1).acumuladoDespues()).isEqualByComparingTo("500.00");
        assertThat(lista.get(1).saldoDespues()).isEqualByComparingTo("500.00");
        assertThat(lista.get(0).fechaPago()).isEqualTo(HOY.minusDays(1));
        assertThat(lista.get(0).id()).isEqualTo(1L);
    }

    @Test
    void listarUnaObligacionSinPagosDaListaVaciaYUnaInexistenteDa404() {
        when(obligaciones.existsById(OBLIGACION)).thenReturn(true);
        when(pagos.findByObligacionIdOrderByFechaPagoAscIdAsc(OBLIGACION)).thenReturn(java.util.List.of());
        when(obligaciones.existsById(99L)).thenReturn(false);

        assertThat(service.listar(OBLIGACION)).isEmpty();
        assertThatThrownBy(() -> service.listar(99L)).isInstanceOf(EntityNotFoundException.class).hasMessage("Obligación no encontrada");
        verify(pagos, never()).findByObligacionIdOrderByFechaPagoAscIdAsc(99L);
    }

    private static PagoObligacion pagoGuardado(long id, String constancia, String monto, String acumulado, String saldo) {
        PagoObligacion p = new PagoObligacion();
        org.springframework.test.util.ReflectionTestUtils.setField(p, "id", id);
        p.setObligacionId(OBLIGACION);
        p.setFechaPago(HOY.minusDays(1));
        p.setMontoPagado(new BigDecimal(monto));
        p.setNumeroConstancia(constancia);
        p.setAcumuladoDespues(new BigDecimal(acumulado));
        p.setSaldoDespues(new BigDecimal(saldo));
        return p;
    }

    @Test
    void sinFechaMontoNiConstanciaListaTodosLosErroresJuntosYNoPideArchivo() throws IOException {
        assertThatThrownBy(() -> service.registrar(OBLIGACION, new PagoEntradaDto(null, null, null), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Faltan o son inválidos: fechaPago, montoPagado, numeroConstancia");
        verify(pagos, never()).save(any());
    }

    @Test
    void sinCuerpoTambienSeRechaza() throws IOException {
        noSeGuarda(null, pdf(), "fechaPago", "montoPagado", "numeroConstancia");
    }

    @Test
    void laFechaDePagoNoPuedeSerFuturaPeroLaDeHoyEsValida() throws IOException {
        noSeGuarda(new PagoEntradaDto(HOY.plusDays(1), new BigDecimal("10"), "C-1"), pdf(), "fechaPago (no puede ser futura)");

        service.registrar(OBLIGACION, new PagoEntradaDto(HOY, new BigDecimal("10"), "C-1"), pdf());
        assertThat(guardado().getFechaPago()).isEqualTo(HOY);
    }

    @Test
    void elMontoDebeSerMayorQueCero() throws IOException {
        noSeGuarda(pago("0", "C-1"), pdf(), "montoPagado (debe ser mayor que cero)");
        noSeGuarda(pago("-5.00", "C-1"), pdf(), "montoPagado (debe ser mayor que cero)");
    }

    @Test
    void elNumeroDeConstanciaEsObligatorioDeHasta30CaracteresYNoSeRepite() throws IOException {
        noSeGuarda(pago("10", "   "), pdf(), "numeroConstancia");
        noSeGuarda(pago("10", "X".repeat(31)), pdf(), "numeroConstancia (máximo 30 caracteres)");
        when(pagos.existsByObligacionIdAndNumeroConstancia(OBLIGACION, "C-001")).thenReturn(true);
        noSeGuarda(pago("10", " C-001 "), pdf(), "numeroConstancia (ya registrado en esta obligación)");
    }

    @Test
    void elComprobanteDebeSerUnPdfDeVerdadYNoSuperarElMaximo() throws IOException {
        MultipartFile texto = new MockMultipartFile("archivo", "c.pdf", "application/pdf", "no soy un pdf".getBytes(StandardCharsets.US_ASCII));
        MultipartFile otroTipo = new MockMultipartFile("archivo", "c.png", "image/png", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));
        MultipartFile grande = new MockMultipartFile("archivo", "c.pdf", "application/pdf", new byte[11 * 1024 * 1024]);

        noSeGuarda(pago("10", "C-1"), texto, "archivo (no es un PDF)");
        noSeGuarda(pago("10", "C-1"), otroTipo, "archivo (no es un PDF)");
        noSeGuarda(pago("10", "C-1"), grande, "archivo (supera el máximo de 10 MB)");
    }

    @Test
    void siFallaElGuardadoDelPagoSeBorraElArchivoYLaObligacionNoCambiaDeEstado() throws IOException {
        doThrow(new IllegalStateException("falla de base")).when(pagos).save(any(PagoObligacion.class));

        assertThatThrownBy(() -> service.registrar(OBLIGACION, pago("100.00", "C-001"), pdf()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(archivosGuardados()).isZero();
        verify(obligaciones, never()).save(any());
    }
}
