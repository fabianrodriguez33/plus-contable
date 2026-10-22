package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluscontable.tributario.dto.TrabajadorDto;
import com.pluscontable.tributario.model.Trabajador;
import com.pluscontable.tributario.model.Trabajador.SistemaPensionario;
import com.pluscontable.tributario.model.Trabajador.TipoDocumento;
import com.pluscontable.tributario.repository.ClienteRepository;
import com.pluscontable.tributario.repository.TrabajadorRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** HU-03 · Tarea 36 (unitarias). La 37 (integración) va en un @SpringBootTest aparte. */
class TrabajadorServiceTest {

    private static final long CLIENTE = 1L;

    private final TrabajadorRepository trabajadores = mock(TrabajadorRepository.class);
    private final ClienteRepository clientes = mock(ClienteRepository.class);
    private final TrabajadorService service = new TrabajadorService(trabajadores, clientes);

    @BeforeEach
    void setUp() {
        when(clientes.existsById(CLIENTE)).thenReturn(true);
        when(trabajadores.save(any(Trabajador.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static TrabajadorDto nacional() {
        return new TrabajadorDto(null, "Ana Quispe", TipoDocumento.DNI, "45678912", false, null,
                SistemaPensionario.ONP, null, LocalDate.of(2026, 3, 1), new BigDecimal("1500.00"),
                null, null, null);
    }

    private static TrabajadorDto extranjero() {
        return new TrabajadorDto(null, "Luis Pérez", TipoDocumento.CE, "001234567", true, "Venezolana",
                SistemaPensionario.AFP, "Prima", LocalDate.of(2026, 4, 1), new BigDecimal("1800.00"),
                "Plazo fijo", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31));
    }

    @Test
    void nacionalConDatosCompletosSeGuarda() {
        TrabajadorDto guardado = service.registrar(CLIENTE, nacional());

        assertThat(guardado.extranjero()).isFalse();
        assertThat(guardado.sistemaPensionario()).isEqualTo(SistemaPensionario.ONP);
        assertThat(guardado.nacionalidad()).isNull();
        assertThat(guardado.tipoContrato()).isNull();
        verify(trabajadores).save(any(Trabajador.class));
    }

    @Test
    void nacionalSinDatosObligatoriosSenalaLosCamposFaltantes() {
        TrabajadorDto vacio = new TrabajadorDto(null, " ", null, null, false, null, null, null, null, null,
                null, null, null);

        assertThatThrownBy(() -> service.registrar(CLIENTE, vacio))
                .isInstanceOf(ValidationException.class)
                .hasMessageContainingAll("nombreCompleto", "tipoDocumento", "numeroDocumento",
                        "sistemaPensionario", "fechaIngreso");
        verify(trabajadores, never()).save(any());
    }

    @Test
    void extranjeroConDocumentoYContratoSeGuardaDiferenciado() {
        TrabajadorDto guardado = service.registrar(CLIENTE, extranjero());

        assertThat(guardado.extranjero()).isTrue();
        assertThat(guardado.tipoDocumento()).isEqualTo(TipoDocumento.CE);
        assertThat(guardado.nacionalidad()).isEqualTo("Venezolana");
        assertThat(guardado.tipoContrato()).isEqualTo("Plazo fijo");
        assertThat(guardado.fechaInicioContrato()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(guardado.nombreAfp()).isEqualTo("Prima");
    }

    @Test
    void extranjeroSinDatosDeExtranjeriaNoSeGuarda() {
        TrabajadorDto e = extranjero();
        TrabajadorDto incompleto = new TrabajadorDto(null, e.nombreCompleto(), TipoDocumento.DNI, e.numeroDocumento(),
                true, null, e.sistemaPensionario(), e.nombreAfp(), e.fechaIngreso(), e.remuneracion(),
                null, null, null);

        assertThatThrownBy(() -> service.registrar(CLIENTE, incompleto))
                .isInstanceOf(ValidationException.class)
                .hasMessageContainingAll("tipoDocumento", "nacionalidad", "tipoContrato", "fechaInicioContrato");
        verify(trabajadores, never()).save(any());
    }

    @Test
    void afpSinNombreDeAfpNoSeGuarda() {
        TrabajadorDto n = nacional();
        TrabajadorDto afp = new TrabajadorDto(null, n.nombreCompleto(), n.tipoDocumento(), n.numeroDocumento(), false,
                null, SistemaPensionario.AFP, "", n.fechaIngreso(), n.remuneracion(), null, null, null);

        assertThatThrownBy(() -> service.registrar(CLIENTE, afp))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("nombreAfp");
        verify(trabajadores, never()).save(any());
    }

    @Test
    void contratoQueTerminaAntesDeEmpezarSeRechaza() {
        TrabajadorDto e = extranjero();
        TrabajadorDto fechas = new TrabajadorDto(null, e.nombreCompleto(), e.tipoDocumento(), e.numeroDocumento(),
                true, e.nacionalidad(), e.sistemaPensionario(), e.nombreAfp(), e.fechaIngreso(), e.remuneracion(),
                e.tipoContrato(), LocalDate.of(2026, 4, 1), LocalDate.of(2026, 3, 1));

        assertThatThrownBy(() -> service.registrar(CLIENTE, fechas))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("fechaFinContrato");
    }

    @Test
    void clienteInexistenteNoRegistra() {
        when(clientes.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.registrar(99L, nacional())).isInstanceOf(EntityNotFoundException.class);
        verify(trabajadores, never()).save(any());
    }
}
