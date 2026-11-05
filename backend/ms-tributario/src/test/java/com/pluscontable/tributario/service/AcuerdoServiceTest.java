package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluscontable.tributario.dto.AcuerdoDto;
import com.pluscontable.tributario.model.AcuerdoServicio;
import com.pluscontable.tributario.model.HonorarioHistorial;
import com.pluscontable.tributario.repository.AcuerdoRepository;
import com.pluscontable.tributario.repository.ClienteRepository;
import com.pluscontable.tributario.repository.HonorarioHistorialRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** HU-05 · Tarea 48 (unitarias). La 49 (integración) va en un @SpringBootTest aparte. */
class AcuerdoServiceTest {

    private static final long CLIENTE = 1L;
    private static final LocalDate INICIO = LocalDate.of(2026, 1, 1);

    private final AcuerdoRepository acuerdos = mock(AcuerdoRepository.class);
    private final HonorarioHistorialRepository historial = mock(HonorarioHistorialRepository.class);
    private final ClienteRepository clientes = mock(ClienteRepository.class);
    private final AcuerdoService service = new AcuerdoService(acuerdos, historial, clientes);

    @BeforeEach
    void setUp() {
        when(clientes.existsById(CLIENTE)).thenReturn(true);
        when(acuerdos.findByClienteId(CLIENTE)).thenReturn(Optional.empty());
        when(acuerdos.save(any(AcuerdoServicio.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static AcuerdoDto entrada(String monto, LocalDate desde) {
        return new AcuerdoDto(null, "Contabilidad mensual, planillas", monto == null ? null : new BigDecimal(monto),
                desde, INICIO, null);
    }

    private AcuerdoServicio existente(String monto, LocalDate honorarioDesde) {
        AcuerdoServicio a = new AcuerdoServicio();
        a.setId(7L);
        a.setClienteId(CLIENTE);
        a.setHonorarioMensual(new BigDecimal(monto));
        a.setHonorarioDesde(honorarioDesde);
        a.setFechaInicio(INICIO);
        when(acuerdos.findByClienteId(CLIENTE)).thenReturn(Optional.of(a));
        return a;
    }

    @Test
    void acuerdoNuevoSeRegistraConServiciosHonorarioYFechaDeInicio() {
        AcuerdoDto a = service.guardar(CLIENTE, entrada("450.00", null));

        assertThat(a.honorarioMensual()).isEqualByComparingTo("450");
        assertThat(a.fechaInicio()).isEqualTo(INICIO);
        assertThat(a.honorarioDesde()).isEqualTo(INICIO);
        assertThat(a.serviciosContratados()).isEqualTo("Contabilidad mensual, planillas");
        assertThat(a.historial()).isEmpty();
        verify(historial, never()).save(any());
    }

    @Test
    void sinHonorarioOFechaDeInicioNoSeGuarda() {
        AcuerdoDto vacio = new AcuerdoDto(null, "x", null, null, null, null);

        assertThatThrownBy(() -> service.guardar(CLIENTE, vacio))
                .isInstanceOf(ValidationException.class)
                .hasMessageContainingAll("honorarioMensual", "fechaInicio");
        verify(acuerdos, never()).save(any());
    }

    @Test
    void honorarioCeroONegativoNoSeGuarda() {
        assertThatThrownBy(() -> service.guardar(CLIENTE, entrada("0", null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("mayor que cero");
    }

    @Test
    void cambioDeHonorarioGuardaElAnteriorConSuVigencia() {
        existente("450.00", INICIO);

        AcuerdoDto a = service.guardar(CLIENTE, entrada("500.00", LocalDate.of(2026, 7, 1)));

        ArgumentCaptor<HonorarioHistorial> h = ArgumentCaptor.forClass(HonorarioHistorial.class);
        verify(historial).save(h.capture());
        assertThat(h.getValue().getAcuerdoId()).isEqualTo(7L);
        assertThat(h.getValue().getMonto()).isEqualByComparingTo("450");
        assertThat(h.getValue().getVigenteDesde()).isEqualTo(INICIO);
        assertThat(h.getValue().getVigenteHasta()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(a.honorarioMensual()).isEqualByComparingTo("500");
        assertThat(a.honorarioDesde()).isEqualTo(LocalDate.of(2026, 7, 1));
    }

    @Test
    void mismoHonorarioNoGeneraHistorial() {
        existente("450.00", INICIO);

        service.guardar(CLIENTE, entrada("450", LocalDate.of(2026, 7, 1)));

        verify(historial, never()).save(any());
    }

    @Test
    void nuevaVigenciaNoPuedeSerAnteriorAlHonorarioActual() {
        existente("450.00", LocalDate.of(2026, 7, 1));

        assertThatThrownBy(() -> service.guardar(CLIENTE, entrada("500", LocalDate.of(2026, 7, 1))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("honorarioDesde");
        verify(historial, never()).save(any());
    }

    @Test
    void elVigenteIncluyeElHistorial() {
        existente("500.00", LocalDate.of(2026, 7, 1));
        HonorarioHistorial h = new HonorarioHistorial();
        h.setMonto(new BigDecimal("450"));
        h.setVigenteDesde(INICIO);
        h.setVigenteHasta(LocalDate.of(2026, 6, 30));
        when(historial.findByAcuerdoIdOrderByVigenteDesdeDesc(7L)).thenReturn(List.of(h));

        AcuerdoDto a = service.vigente(CLIENTE);

        assertThat(a.honorarioMensual()).isEqualByComparingTo("500");
        assertThat(a.historial()).singleElement()
                .satisfies(x -> assertThat(x.vigenteHasta()).isEqualTo(LocalDate.of(2026, 6, 30)));
    }

    @Test
    void clienteInexistenteNoRegistra() {
        assertThatThrownBy(() -> service.guardar(99L, entrada("450", null))).isInstanceOf(EntityNotFoundException.class);
    }
}
